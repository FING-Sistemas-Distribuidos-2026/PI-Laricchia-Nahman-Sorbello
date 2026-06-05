package com.tickets.queue.service;

import com.tickets.queue.dto.JoinQueueRequest;
import com.tickets.queue.dto.QueueStatusResponse;
import com.tickets.queue.enums.QueueStatus;
import com.tickets.queue.model.EventLog;
import com.tickets.queue.model.QueueEntry;
import com.tickets.queue.repository.EventLogRepository;
import com.tickets.queue.repository.QueueEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueueService {

    private static final String WAITING_QUEUE_KEY = "waiting_queue";

    private final RedisTemplate<String, String> redisTemplate;
    private final QueueEntryRepository          queueEntryRepository;
    private final EventLogRepository            eventLogRepository;
    private final SystemParametersCache         paramsCache;

    @Transactional
    public QueueStatusResponse joinQueue(JoinQueueRequest request) {
        String userId    = request.getUserId();
        UUID   userUuid  = UUID.fromString(userId);
        String activeKey = "active:" + userId;

        if (redisTemplate.opsForValue().get(activeKey) != null) {
            return QueueStatusResponse.builder()
                    .userId(userId).status("BUYING")
                    .message("Ya tenés una sesión de compra activa").build();
        }

        List<String> waitingList = redisTemplate.opsForList().range(WAITING_QUEUE_KEY, 0, -1);
        if (waitingList != null && waitingList.contains(userId)) {
            int position = waitingList.indexOf(userId) + 1;
            return QueueStatusResponse.builder()
                    .userId(userId).status("WAITING")
                    .position(position).message("Ya estás en la cola").build();
        }

        Long currentSize = redisTemplate.opsForList().size(WAITING_QUEUE_KEY);
        if (currentSize != null && currentSize >= paramsCache.getMaxWaitingQueue()) {
            return QueueStatusResponse.builder()
                    .userId(userId).status("REJECTED")
                    .message("Cola llena. Intentá más tarde.").build();
        }

        Long newSize = redisTemplate.opsForList().rightPush(WAITING_QUEUE_KEY, userId);
        int position = (newSize != null) ? newSize.intValue() : 1;
        redisTemplate.opsForValue().increment("waiting_count");

        //queueEntryRepository.findByUserId(userUuid).ifPresent(queueEntryRepository::delete);
        QueueEntry entry = new QueueEntry();
        entry.setUserId(userUuid);
        entry.setStatus(QueueStatus.WAITING);
        entry.setPosition(position);;
        queueEntryRepository.save(entry);
        EventLog event = new EventLog();
        event.setUserId(userUuid);
        event.setEventType("JOIN_QUEUE");
        event.setPayload(Map.of("position", position, "userId", userId));
        eventLogRepository.save(event);

        log.info("[QueueService] Usuario {} ingresó. Posición: {}", userId, position);

        return QueueStatusResponse.builder()
                .userId(userId).status("WAITING").position(position)
                .message("Ingresaste a la cola de espera").justJoined(true).build();
    }

    /**
     * Orden de prioridad:
     * 1. active:{userId} en Redis        → BUYING
     * 2. Estado terminal en PostgreSQL   → EXPIRED / PURCHASED / CANCELLED
     * 3. waiting_queue en Redis          → WAITING
     * 4. Estado no terminal en PostgreSQL
     * 5. NOT_FOUND
     *
     * El chequeo terminal VA ANTES de waiting_queue para evitar que un usuario
     * EXPIRED siga apareciendo como WAITING porque su userId quedó en la lista.
     */
    public QueueStatusResponse getStatus(String userId) {
        String activeKey = "active:" + userId;

        // 1. ¿Comprando ahora?
        String activeJson = redisTemplate.opsForValue().get(activeKey);
        if (activeJson != null) {
            Long ttl = redisTemplate.getExpire(activeKey);
            Long ticketId = extractTicketId(activeJson);
            log.info("[DEBUG] activeJson={} ticketId={}", activeJson, ticketId);  // ← agregar esto
            return QueueStatusResponse.builder()
                    .userId(userId)
                    .status("BUYING")
                    .ticketId(ticketId)
                    .ttlRemaining(ttl)
                    .build();
        }

        // 2. Estado terminal en PostgreSQL ANTES de mirar waiting_queue
        Optional<QueueEntry> entry = queueEntryRepository.findByUserId(UUID.fromString(userId));
        if (entry.isPresent()) {
            QueueStatus dbStatus = entry.get().getStatus();
            if (dbStatus == QueueStatus.EXPIRED
                    || dbStatus == QueueStatus.PURCHASED
                    || dbStatus == QueueStatus.CANCELLED) {
                return QueueStatusResponse.builder()
                        .userId(userId).status(dbStatus.name()).build();
            }
        }

        // 3. ¿En cola de espera?
        List<String> waitingList = redisTemplate.opsForList().range(WAITING_QUEUE_KEY, 0, -1);
        if (waitingList != null && waitingList.contains(userId)) {
            int position = waitingList.indexOf(userId) + 1;
            return QueueStatusResponse.builder()
                    .userId(userId).status("WAITING")
                    .position(position).totalWaiting(waitingList.size()).build();
        }

        // 4. Estado no terminal en PostgreSQL
        if (entry.isPresent()) {
            return QueueStatusResponse.builder()
                    .userId(userId).status(entry.get().getStatus().name()).build();
        }

        return QueueStatusResponse.builder().userId(userId).status("NOT_FOUND").build();
    }

    public Long getTtl(String userId) {
        return redisTemplate.getExpire("active:" + userId);
    }

    public Map<String, Long> getStats() {
        Long waiting = redisTemplate.opsForList().size(WAITING_QUEUE_KEY);
        String buyingStr = redisTemplate.opsForValue().get("buying_count");
        long buying = buyingStr != null ? Long.parseLong(buyingStr) : 0L;
        return Map.of("waiting", waiting != null ? waiting : 0L, "buying", buying);
    }
    /**
     * Limpia TODO el estado del usuario en Redis.
     * Lo llama compra-service después de PURCHASED o EXPIRED.
     * Idempotente: si las claves ya no existen, no rompe nada.
     */
    public void cleanupUser(String userId) {
        String activeKey = "active:" + userId;

        Boolean hadActiveSession = redisTemplate.hasKey(activeKey);
        redisTemplate.delete(activeKey);

        Long removed = redisTemplate.opsForList().remove(WAITING_QUEUE_KEY, 0, userId);

        // Decrementar buying_count siempre que se llame a cleanup
        // (este endpoint solo lo llama Aida después de BUYING → PURCHASED o EXPIRED).
        // Si la clave active:* ya había sido borrada por TTL nativo, igual hay que decrementar
        // porque cuando el scheduler la creó, ya incrementó buying_count.
        // Piso en 0 para evitar contadores negativos por llamadas duplicadas (idempotencia).
        String currentStr = redisTemplate.opsForValue().get("buying_count");
        long current = currentStr != null ? Long.parseLong(currentStr) : 0L;
        if (current > 0) {
            redisTemplate.opsForValue().decrement("buying_count");
        }

        if (removed != null && removed > 0) {
            String wCurrentStr = redisTemplate.opsForValue().get("waiting_count");
            long wCurrent = wCurrentStr != null ? Long.parseLong(wCurrentStr) : 0L;
            if (wCurrent > 0) {
                redisTemplate.opsForValue().decrement("waiting_count");
            }
        }

        log.info("[QueueService] Cleanup {}: activeBorrado={}, removidosCola={}",
                userId, hadActiveSession, removed);
    }
    /**
     * Extrae el ticketId del JSON guardado en active:{userId}.
     * Formato esperado: {"userId":"...","ticketId":42,"status":"BUYING"}
     * Devuelve null si no se puede parsear.
     */
    private Long extractTicketId(String json) {
        if (json == null) return null;
        try {
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("\"ticketId\"\\s*:\\s*(\\d+)")
                    .matcher(json);
            if (m.find()) {
                return Long.parseLong(m.group(1));
            }
        } catch (Exception e) {
            log.warn("[QueueService] No se pudo parsear ticketId de: {}", json);
        }
        return null;
    }
}