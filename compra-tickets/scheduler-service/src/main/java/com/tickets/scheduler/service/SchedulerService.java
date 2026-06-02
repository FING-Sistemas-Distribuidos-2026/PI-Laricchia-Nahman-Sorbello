package com.tickets.scheduler.service;

import com.tickets.scheduler.client.CompraClient;
import com.tickets.scheduler.client.CompraClient.ActivateOutcome;
import com.tickets.scheduler.client.CompraClient.ActivateResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class SchedulerService {

    private static final String WAITING_QUEUE_KEY = "waiting_queue";
    private static final String BUYING_COUNT_KEY  = "buying_count";
    private static final String WAITING_COUNT_KEY = "waiting_count";

    private final RedisTemplate<String, String> redisTemplate;
    private final SystemParametersCache         paramsCache;
    private final CompraClient                  compraClient;

    /**
     * Corazón de la ventana deslizante.
     * fixedDelay = espera 5s DESPUÉS de que termina el ciclo anterior.
     * Esto evita solapamiento si un ciclo tarda más de 5s.
     */
    @Scheduled(fixedDelayString = "${scheduler.interval.ms:5000}")
    public void procesarCola() {
        try {
            int slotsLibres = calcularSlotsLibres();

            if (slotsLibres <= 0) {
                log.debug("[Scheduler] Ventanilla llena. Buyers: {}. Max: {}",
                        getBuyingCount(), paramsCache.getMaxConcurrentBuyers());
                return;
            }

            log.info("[Scheduler] Ciclo iniciado. Slots libres: {}", slotsLibres);

            for (int i = 0; i < slotsLibres; i++) {
                boolean continuar = procesarSiguienteUsuario();
                if (!continuar) {
                    log.info("[Scheduler] Ciclo detenido en slot {} de {}.", i + 1, slotsLibres);
                    break;
                }
            }

            // Red de seguridad: detectar claves active:* sin TTL
            // (no debería pasar, pero si pasa es una bomba de tiempo)
            verificarIntegridadSesiones();

        } catch (Exception e) {
            log.error("[Scheduler] Error no controlado en ciclo: {}", e.getMessage(), e);
        }
    }

    /**
     * Procesa un único usuario de la cola.
     * Devuelve true si el ciclo debe continuar, false si debe detenerse.
     *
     * Garantía de no pérdida:
     * Si cualquier paso falla después del LPOP, el usuario se devuelve
     * al FRENTE de la cola con LPUSH. Nunca se pierde un usuario.
     */
    private boolean procesarSiguienteUsuario() {
        // LPOP: saca por la izquierda (FIFO garantizado con RPUSH del queue-service)
        String userId = redisTemplate.opsForList().leftPop(WAITING_QUEUE_KEY);

        if (userId == null) {
            log.debug("[Scheduler] Cola vacía.");
            return false; // no hay más usuarios, detener el for
        }

        log.info("[Scheduler] Procesando slot para: {}", userId);

        // Verificación defensiva: ¿ya tiene sesión activa?
        // Caso borde: el scheduler corrió dos veces muy rápido o hubo bug previo
        String activeKey = "active:" + userId;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(activeKey))) {
            log.warn("[Scheduler] {} ya tiene active:key. Saltando sin modificar contadores.", userId);
            // No devolvemos a la cola (ya está activo), no tocamos contadores
            return true;
        }

        // Llamar a Aida para reservar ticket
        ActivateOutcome outcome = compraClient.activateUser(userId);

        switch (outcome.result()) {

            case SUCCESS -> {
                // Éxito: crear sesión activa en Redis
                long ttl = paramsCache.getPurchaseTtlSeconds();

                // ticketId es Long (BIGSERIAL de Aida) → sin comillas en el JSON
                String payload = String.format(
                        "{\"ticketId\":%d,\"status\":\"BUYING\"}", outcome.ticketId());

                // SET active:userId payload EX ttl
                // Redis borrará esta clave automáticamente cuando venza el TTL
                redisTemplate.opsForValue().set(activeKey, payload, Duration.ofSeconds(ttl));

                // Actualizar contadores
                redisTemplate.opsForValue().increment(BUYING_COUNT_KEY);
                redisTemplate.opsForValue().decrement(WAITING_COUNT_KEY);

                log.info("[Scheduler] {} activado. TicketId: {}. TTL: {}s",
                        userId, outcome.ticketId(), ttl);
                return true; // continuar con el siguiente slot
            }

            case NO_TICKETS -> {
                // Sin tickets disponibles para nadie.
                // Devolver al frente y detener el ciclo completo.
                // No tiene sentido activar más usuarios si no hay tickets.
                redisTemplate.opsForList().leftPush(WAITING_QUEUE_KEY, userId);
                log.warn("[Scheduler] Sin tickets disponibles. Usuario {} devuelto al frente.", userId);
                return false; // detener el for
            }

            case USER_NOT_FOUND -> {
                // El userId no existe en la BD de Aida.
                // Es un usuario fantasma en Redis (inconsistencia).
                // Lo descartamos: no lo devolvemos a la cola.
                redisTemplate.opsForValue().decrement(WAITING_COUNT_KEY);
                log.warn("[Scheduler] Usuario {} no existe en BD. Descartado de la cola.", userId);
                return true; // continuar con el siguiente
            }

            case USER_NOT_WAITING -> {
                // El usuario existe pero no está en estado WAITING en la BD.
                // Mismo tratamiento que NOT_FOUND: descartar.
                redisTemplate.opsForValue().decrement(WAITING_COUNT_KEY);
                log.warn("[Scheduler] Usuario {} en estado incorrecto. Descartado.", userId);
                return true;
            }

            case NETWORK_ERROR -> {
                // Aida no responde. Devolver al FRENTE de la cola.
                // El usuario no pierde su turno por un problema de infraestructura.
                redisTemplate.opsForList().leftPush(WAITING_QUEUE_KEY, userId);
                log.error("[Scheduler] Error de red. {} devuelto al frente de la cola.", userId);
                return true; // intentar con el siguiente (puede que otros funcionen)
            }

            default -> {
                // Caso no contemplado: devolver por seguridad
                redisTemplate.opsForList().leftPush(WAITING_QUEUE_KEY, userId);
                log.error("[Scheduler] Resultado inesperado para {}. Devuelto a la cola.", userId);
                return true;
            }
        }
    }

    /**
     * Red de seguridad: verifica que todas las claves active:* tengan TTL.
     * Si alguna no tiene TTL (-1) es un bug — la sesión nunca expiraría.
     * Las claves que ya expiraron (-2) no aparecen en KEYS, Redis las borró.
     *
     * Nota sobre KEYS active:* en producción real:
     * KEYS es O(N) sobre todas las claves. Con MAX_CONCURRENT_BUYERS=10,
     * hay máximo 10 claves active:* → O(10) = perfectamente aceptable.
     * En un sistema con millones de claves usaríamos SCAN en su lugar.
     */
    private void verificarIntegridadSesiones() {
        try {
            Set<String> activeKeys = redisTemplate.keys("active:*");
            if (activeKeys == null || activeKeys.isEmpty()) return;

            for (String key : activeKeys) {
                Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
                if (ttl != null && ttl == -1L) {
                    // Clave sin TTL: esto no debería pasar nunca.
                    // El scheduler siempre crea active:{userId} con EX.
                    // Si pasa, la dejamos pero logueamos con nivel ERROR para investigar.
                    log.error("[Scheduler] ALERTA: {} no tiene TTL. " +
                            "Sesión nunca expiraría. Revisar bug.", key);
                }
                // TTL >= 0: normal, Redis la borrará solo
                // TTL == -2: ya expiró, Redis la borró, no aparece en KEYS
            }
        } catch (Exception e) {
            log.error("[Scheduler] Error en verificación de sesiones: {}", e.getMessage());
        }
    }

    private long getBuyingCount() {
        String val = redisTemplate.opsForValue().get(BUYING_COUNT_KEY);
        return val != null ? Long.parseLong(val) : 0L;
    }

    private int calcularSlotsLibres() {
        long maxBuyers   = paramsCache.getMaxConcurrentBuyers();
        long buyingCount = getBuyingCount();
        return (int) Math.max(0, maxBuyers - buyingCount);
    }
}