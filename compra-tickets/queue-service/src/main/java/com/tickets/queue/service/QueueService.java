package com.tickets.queue.service;

import com.tickets.queue.dto.JoinQueueRequest;
import com.tickets.queue.dto.QueueStatusResponse;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class QueueService {

    private static final String WAITING_QUEUE_KEY = "waiting_queue";

    private final RedisTemplate<String, String> redisTemplate;
    private final QueueEntryRepository          queueEntryRepository;
    private final EventLogRepository            eventLogRepository;
    private final SystemParametersCache         paramsCache;

    /**
     * Flujo de ingreso a la cola — 4 validaciones en orden estricto.
     */
    @Transactional
    public QueueStatusResponse joinQueue(JoinQueueRequest request) {
        String userId   = request.getUserId();
        String activeKey = "active:" + userId;

        // PASO 1: ¿ya tiene sesión activa de compra?
        if (redisTemplate.opsForValue().get(activeKey) != null) {
            return QueueStatusResponse.builder()
                    .userId(userId)
                    .status("BUYING")
                    .message("Ya tenés una sesión de compra activa")
                    .build();
        }

        // PASO 2: ¿ya está en la cola de espera?
        List<String> waitingList = redisTemplate.opsForList().range(WAITING_QUEUE_KEY, 0, -1);
        if (waitingList != null && waitingList.contains(userId)) {
            int position = waitingList.indexOf(userId) + 1;
            return QueueStatusResponse.builder()
                    .userId(userId)
                    .status("WAITING")
                    .position(position)
                    .message("Ya estás en la cola")
                    .build();
        }

        // PASO 3: ¿cola llena?
        Long currentSize = redisTemplate.opsForList().size(WAITING_QUEUE_KEY);
        if (currentSize != null && currentSize >= paramsCache.getMaxWaitingQueue()) {
            return QueueStatusResponse.builder()
                    .userId(userId)
                    .status("REJECTED")
                    .message("Cola llena. Intentá más tarde.")
                    .build();
        }

        // PASO 4: agregar a la cola
        // RPUSH = entra por la derecha. Scheduler saca con LPOP por la izquierda → FIFO garantizado.
        // rightPush devuelve el nuevo tamaño de la lista: eso es la posición exacta, sin segunda consulta.
        Long newSize = redisTemplate.opsForList().rightPush(WAITING_QUEUE_KEY, userId);
        int position = (newSize != null) ? newSize.intValue() : 1;
        redisTemplate.opsForValue().increment("waiting_count");

        // Persistir en PostgreSQL — si Redis se reinicia, la DB permite reconstruir el estado
        QueueEntry entry = queueEntryRepository.findByUserId(userId)
                .orElse(new QueueEntry());
        entry.setUserId(userId);
        entry.setStatus("WAITING");
        entry.setCreatedAt(entry.getCreatedAt() != null ? entry.getCreatedAt() : LocalDateTime.now());
        entry.setUpdatedAt(LocalDateTime.now());
        queueEntryRepository.save(entry);

        // Auditoría
        EventLog event = new EventLog();
        event.setUserId(userId);
        event.setEventType("JOIN_QUEUE");
        event.setPayload(String.format("{\"position\":%d}", position));
        event.setCreatedAt(LocalDateTime.now());
        eventLogRepository.save(event);

        log.info("[QueueService] Usuario {} ingresó. Posición: {}", userId, position);

        return QueueStatusResponse.builder()
                .userId(userId)
                .status("WAITING")
                .position(position)
                .message("Ingresaste a la cola de espera")
                .justJoined(true)
                .build();
    }

    /**
     * Estado actual del usuario.
     * Redis primero (O(1)), PostgreSQL solo si no está en Redis.
     */
    public QueueStatusResponse getStatus(String userId) {
        String activeKey = "active:" + userId;

        // ¿Comprando ahora?
        if (redisTemplate.opsForValue().get(activeKey) != null) {
            Long ttl = redisTemplate.getExpire(activeKey);
            return QueueStatusResponse.builder()
                    .userId(userId)
                    .status("BUYING")
                    .ttlRemaining(ttl)
                    .build();
        }

        // ¿En cola de espera?
        List<String> waitingList = redisTemplate.opsForList().range(WAITING_QUEUE_KEY, 0, -1);
        if (waitingList != null && waitingList.contains(userId)) {
            int position = waitingList.indexOf(userId) + 1;
            return QueueStatusResponse.builder()
                    .userId(userId)
                    .status("WAITING")
                    .position(position)
                    .totalWaiting(waitingList.size())
                    .build();
        }

        // Estado final en PostgreSQL (PURCHASED, EXPIRED, CANCELLED)
        Optional<QueueEntry> entry = queueEntryRepository.findByUserId(userId);
        if (entry.isPresent()) {
            return QueueStatusResponse.builder()
                    .userId(userId)
                    .status(entry.get().getStatus())
                    .build();
        }

        return QueueStatusResponse.builder()
                .userId(userId)
                .status("NOT_FOUND")
                .build();
    }

    /**
     * TTL restante de la sesión de compra.
     * Devuelve -2 si la clave no existe, -1 si no tiene TTL (no debería pasar).
     */
    public Long getTtl(String userId) {
        return redisTemplate.getExpire("active:" + userId);
    }

    /**
     * Métricas rápidas. LLEN es O(1).
     */
    public Map<String, Long> getStats() {
        Long waiting = redisTemplate.opsForList().size(WAITING_QUEUE_KEY);
        String buyingStr = redisTemplate.opsForValue().get("buying_count");
        long buying = buyingStr != null ? Long.parseLong(buyingStr) : 0L;
        return Map.of(
                "waiting", waiting != null ? waiting : 0L,
                "buying",  buying
        );
    }
}