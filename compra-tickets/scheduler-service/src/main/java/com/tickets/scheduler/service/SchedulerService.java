package com.tickets.scheduler.service;

import com.tickets.scheduler.client.CompraClient;
import com.tickets.scheduler.client.CompraClient.ActivateOutcome;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SchedulerService {

    private static final String WAITING_QUEUE_KEY = "waiting_queue";
    private static final String WAITING_COUNT_KEY = "waiting_count";

    private final RedisTemplate<String, String> redisTemplate;
    private final SystemParametersCache paramsCache;
    private final CompraClient compraClient;

    @Scheduled(fixedDelayString = "${scheduler.interval.ms:5000}")
    public void procesarCola() {
        try {
            int slotsLibres = calcularSlotsLibres();

            if (slotsLibres <= 0) {
                log.debug(
                        "[Scheduler] Ventanilla llena. Buyers activos: {}",
                        getBuyingCount()
                );

                return;
            }

            log.info("[Scheduler] Ciclo iniciado. Slots libres: {}", slotsLibres);

            for (int i = 0; i < slotsLibres; i++) {
                boolean continuar = procesarSiguienteUsuario();

                if (!continuar) {
                    log.info(
                            "[Scheduler] Ciclo detenido en slot {}/{}.",
                            i + 1,
                            slotsLibres
                    );

                    break;
                }
            }

        } catch (Exception e) {
            log.error(
                    "[Scheduler] Error no controlado: {}",
                    e.getMessage(),
                    e
            );
        }
    }

    private boolean procesarSiguienteUsuario() {
        String userIdStr = redisTemplate.opsForList()
                .leftPop(WAITING_QUEUE_KEY);

        if (userIdStr == null) {
            log.debug("[Scheduler] Cola vacía.");
            return false;
        }

        UUID userId;

        try {
            userId = UUID.fromString(userIdStr);
        } catch (IllegalArgumentException e) {
            log.error(
                    "[Scheduler] UUID inválido en cola: '{}'. Descartado.",
                    userIdStr
            );

            decrementWaitingCount();
            return true;
        }

        log.info("[Scheduler] Procesando slot para: {}", userId);

        ActivateOutcome outcome = compraClient.activateUser(userId);

        switch (outcome.result()) {
            case SUCCESS -> {
                /**
                 * compra-service creó la sesión en Redis HASH buying:sessions.
                 *
                 * El scheduler:
                 * - NO crea active:{userId}
                 * - NO toca buying:sessions
                 * - NO maneja TTL
                 * - NO toca estados de compra
                 */
                decrementWaitingCount();

                log.info(
                        "[Scheduler] {} activado. TicketId: {}.",
                        userId,
                        outcome.ticketId()
                );

                return true;
            }

            case NO_TICKETS -> {
                redisTemplate.opsForList()
                        .leftPush(WAITING_QUEUE_KEY, userId.toString());

                log.warn(
                        "[Scheduler] Sin tickets. {} devuelto al frente.",
                        userId
                );

                return false;
            }

            case USER_NOT_FOUND, USER_NOT_WAITING -> {
                decrementWaitingCount();

                log.warn(
                        "[Scheduler] {} descartado por estado inválido o inexistente.",
                        userId
                );

                return true;
            }

            case NETWORK_ERROR -> {
                redisTemplate.opsForList()
                        .leftPush(WAITING_QUEUE_KEY, userId.toString());

                log.error(
                        "[Scheduler] Error de red. {} devuelto al frente.",
                        userId
                );

                return true;
            }

            default -> {
                redisTemplate.opsForList()
                        .leftPush(WAITING_QUEUE_KEY, userId.toString());

                log.error(
                        "[Scheduler] Resultado inesperado para {}.",
                        userId
                );

                return true;
            }
        }
    }

    private long getBuyingCount() {
        return compraClient.getActiveBuyingCount();
    }

    private int calcularSlotsLibres() {
        long maxBuyers = paramsCache.getMaxConcurrentBuyers();
        long buyingCount = getBuyingCount();

        return (int) Math.max(0, maxBuyers - buyingCount);
    }

    private void decrementWaitingCount() {
        String currentStr = redisTemplate.opsForValue()
                .get(WAITING_COUNT_KEY);

        long current = currentStr != null
                ? Long.parseLong(currentStr)
                : 0L;

        if (current > 0) {
            redisTemplate.opsForValue().decrement(WAITING_COUNT_KEY);
        }
    }
}