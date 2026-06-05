package com.tickets.scheduler.service;

import com.tickets.scheduler.client.CompraClient;
import com.tickets.scheduler.client.CompraClient.ActivateOutcome;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class SchedulerService {

    private static final String WAITING_QUEUE_KEY = "waiting_queue";
    private static final String BUYING_COUNT_KEY  = "buying_count";
    private static final String WAITING_COUNT_KEY = "waiting_count";
    private static final String BUYING_USERS_KEY  = "buying_users";

    private final RedisTemplate<String, String> redisTemplate;
    private final SystemParametersCache         paramsCache;
    private final CompraClient                  compraClient;

    @Scheduled(fixedDelayString = "${scheduler.interval.ms:5000}")
    public void procesarCola() {

        try {
            limpiarExpirados();
            verificarIntegridadSesiones();
            int slotsLibres = calcularSlotsLibres();
            if (slotsLibres <= 0) {
                log.debug("[Scheduler] Ventanilla llena. Buyers: {}", getBuyingCount());
                return;
            }

            log.info("[Scheduler] Ciclo iniciado. Slots libres: {}", slotsLibres);

            for (int i = 0; i < slotsLibres; i++) {
                boolean continuar = procesarSiguienteUsuario();
                if (!continuar) {
                    log.info("[Scheduler] Ciclo detenido en slot {}/{}.", i + 1, slotsLibres);
                    break;
                }
            }



        } catch (Exception e) {
            log.error("[Scheduler] Error no controlado: {}", e.getMessage(), e);
        }
    }

    private boolean procesarSiguienteUsuario() {
        // LPOP devuelve el UUID como String — Redis no entiende UUIDs nativamente
        String userIdStr = redisTemplate.opsForList().leftPop(WAITING_QUEUE_KEY);
        if (userIdStr == null) {
            log.debug("[Scheduler] Cola vacía.");
            return false;
        }

        // Convertir String → UUID. Si Redis tiene basura (no debería), descartamos.
        UUID userId;
        try {
            userId = UUID.fromString(userIdStr);
        } catch (IllegalArgumentException e) {
            log.error("[Scheduler] UUID inválido en cola: '{}'. Descartado.", userIdStr);
            return true; // continuar con el siguiente
        }

        log.info("[Scheduler] Procesando slot para: {}", userId);

        // La clave Redis sigue siendo string (Redis no tiene tipo UUID)
        String activeKey = "active:" + userId;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(activeKey))) {
            log.warn("[Scheduler] {} ya tiene sesión activa. Saltando.", userId);
            return true;
        }

        ActivateOutcome outcome = compraClient.activateUser(userId);

        switch (outcome.result()) {
            case SUCCESS -> {
                long ttl = paramsCache.getPurchaseTtlSeconds();
                String payload = String.format(
                        "{\"userId\":\"%s\",\"ticketId\":%d,\"status\":\"BUYING\"}",
                        userId, outcome.ticketId());
                redisTemplate.opsForValue().set(activeKey, payload, Duration.ofSeconds(ttl));
                redisTemplate.opsForValue().increment(BUYING_COUNT_KEY);
                redisTemplate.opsForValue().decrement(WAITING_COUNT_KEY);
                redisTemplate.opsForSet().add(BUYING_USERS_KEY, userId.toString());
                log.info("[Scheduler] {} activado. TicketId: {}. TTL: {}s",
                        userId, outcome.ticketId(), ttl);
                return true;
            }
            case NO_TICKETS -> {
                // Devolver al frente como string (formato consistente con queue-service)
                redisTemplate.opsForList().leftPush(WAITING_QUEUE_KEY, userId.toString());
                log.warn("[Scheduler] Sin tickets. {} devuelto al frente.", userId);
                return false;
            }
            case USER_NOT_FOUND -> {
                redisTemplate.opsForValue().decrement(WAITING_COUNT_KEY);
                log.warn("[Scheduler] {} no existe en BD. Descartado.", userId);
                return true;
            }
            case USER_NOT_WAITING -> {
                redisTemplate.opsForValue().decrement(WAITING_COUNT_KEY);
                log.warn("[Scheduler] {} en estado incorrecto. Descartado.", userId);
                return true;
            }
            case NETWORK_ERROR -> {
                redisTemplate.opsForList().leftPush(WAITING_QUEUE_KEY, userId.toString());
                log.error("[Scheduler] Error de red. {} devuelto al frente.", userId);
                return true;
            }
            default -> {
                redisTemplate.opsForList().leftPush(WAITING_QUEUE_KEY, userId.toString());
                log.error("[Scheduler] Resultado inesperado para {}.", userId);
                return true;
            }
        }
    }

    /*private void verificarIntegridadSesiones() {
        try {
            Set<String> activeKeys = redisTemplate.keys("active:*");
            if (activeKeys == null || activeKeys.isEmpty()) return;
            for (String key : activeKeys) {
                Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
                if (ttl != null && ttl == -1L) {
                    log.error("[Scheduler] ALERTA: {} sin TTL. Revisar bug.", key);
                }
            }
        } catch (Exception e) {
            log.error("[Scheduler] Error verificación: {}", e.getMessage());
        }
    }*/
    private void limpiarExpirados() {
        try {
            Set<String> buyingUsers = redisTemplate.opsForSet().members(BUYING_USERS_KEY);
            if (buyingUsers == null || buyingUsers.isEmpty()) return;

            for (String userIdStr : buyingUsers) {
                String activeKey = "active:" + userIdStr;
                Boolean exists = redisTemplate.hasKey(activeKey);
                if (exists == null || !exists) {
                    UUID userId = UUID.fromString(userIdStr);
                    log.info("[Scheduler] Usuario {} expiró. Llamando a expire.", userId);
                    boolean ok = compraClient.expireUser(userId);
                    if (ok) {
                        redisTemplate.opsForSet().remove(BUYING_USERS_KEY, userIdStr);
                        redisTemplate.opsForValue().decrement(BUYING_COUNT_KEY);
                        log.info("[Scheduler] {} limpiado correctamente.", userId);
                    }
                }
            }
        } catch (Exception e) {
            log.error("[Scheduler] Error limpiando expirados: {}", e.getMessage());
        }
    }
    //cambiosssssssssssssssssss
    private void verificarIntegridadSesiones() {
        try {
            // Contar cuántas claves active:* existen realmente en Redis
            Set<String> activeKeys = redisTemplate.keys("active:*");
            long activasReales = (activeKeys == null) ? 0 : activeKeys.size();

            // Corregir buying_count para que refleje la realidad
            String val = redisTemplate.opsForValue().get(BUYING_COUNT_KEY);
            long buyingCount = val != null ? Long.parseLong(val) : 0L;

            if (buyingCount != activasReales) {
                log.warn("[Scheduler] buying_count desincronizado: contador={} real={}. Corrigiendo.",
                        buyingCount, activasReales);
                redisTemplate.opsForValue().set(BUYING_COUNT_KEY, String.valueOf(activasReales));
            }

            // Loguear claves sin TTL (bug de integridad)
            if (activeKeys != null) {
                for (String key : activeKeys) {
                    Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
                    if (ttl != null && ttl == -1L) {
                        log.error("[Scheduler] ALERTA: {} sin TTL. Revisar bug.", key);
                    }
                }
            }
        } catch (Exception e) {
            log.error("[Scheduler] Error verificación: {}", e.getMessage());
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