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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueueService {

    private static final String WAITING_QUEUE_KEY = "waiting_queue";
    private static final String WAITING_COUNT_KEY = "waiting_count";

    private final RedisTemplate<String, String> redisTemplate;
    private final QueueEntryRepository queueEntryRepository;
    private final EventLogRepository eventLogRepository;
    private final SystemParametersCache paramsCache;

    @Transactional
    public QueueStatusResponse joinQueue(JoinQueueRequest request) {
        String userId = request.getUserId();
        UUID userUuid = UUID.fromString(userId);

        List<String> waitingList = redisTemplate.opsForList()
                .range(WAITING_QUEUE_KEY, 0, -1);

        if (waitingList != null && waitingList.contains(userId)) {
            int position = waitingList.indexOf(userId) + 1;

            return QueueStatusResponse.builder()
                    .userId(userId)
                    .status("WAITING")
                    .position(position)
                    .message("Ya estás en la cola.")
                    .build();
        }

        Long currentSize = redisTemplate.opsForList()
                .size(WAITING_QUEUE_KEY);

        if (currentSize != null && currentSize >= paramsCache.getMaxWaitingQueue()) {
            return QueueStatusResponse.builder()
                    .userId(userId)
                    .status("REJECTED")
                    .message("Cola llena. Intentá más tarde.")
                    .build();
        }

        Long newSize = redisTemplate.opsForList()
                .rightPush(WAITING_QUEUE_KEY, userId);

        int position = newSize != null
                ? newSize.intValue()
                : 1;

        redisTemplate.opsForValue().increment(WAITING_COUNT_KEY);

        QueueEntry entry = new QueueEntry();
        entry.setUserId(userUuid);
        entry.setStatus(QueueStatus.WAITING);
        entry.setPosition(position);
        queueEntryRepository.save(entry);

        EventLog event = new EventLog();
        event.setUserId(userUuid);
        event.setEventType("JOIN_QUEUE");
        event.setPayload(Map.of(
                "position", position,
                "userId", userId
        ));

        eventLogRepository.save(event);

        log.info(
                "[QueueService] Usuario {} ingresó. Posición: {}",
                userId,
                position
        );

        return QueueStatusResponse.builder()
                .userId(userId)
                .status("WAITING")
                .position(position)
                .message("Ingresaste a la cola de espera.")
                .justJoined(true)
                .build();
    }

    /**
     * queue-service solo conoce:
     * - WAITING en waiting_queue
     * - estados persistidos en PostgreSQL
     *
     * BUYING y TTL se resuelven en compra-service vía api-gateway.
     */
    public QueueStatusResponse getStatus(String userId) {
        UUID userUuid = UUID.fromString(userId);

        Optional<QueueEntry> entry = queueEntryRepository.findByUserId(userUuid);

        if (entry.isPresent()) {
            QueueStatus dbStatus = entry.get().getStatus();

            if (dbStatus == QueueStatus.EXPIRED
                    || dbStatus == QueueStatus.PURCHASED
                    || dbStatus == QueueStatus.CANCELLED) {
                return QueueStatusResponse.builder()
                        .userId(userId)
                        .status(dbStatus.name())
                        .build();
            }
        }

        List<String> waitingList = redisTemplate.opsForList()
                .range(WAITING_QUEUE_KEY, 0, -1);

        if (waitingList != null && waitingList.contains(userId)) {
            int position = waitingList.indexOf(userId) + 1;

            return QueueStatusResponse.builder()
                    .userId(userId)
                    .status("WAITING")
                    .position(position)
                    .totalWaiting(waitingList.size())
                    .build();
        }

        if (entry.isPresent()) {
            return QueueStatusResponse.builder()
                    .userId(userId)
                    .status(entry.get().getStatus().name())
                    .build();
        }

        return QueueStatusResponse.builder()
                .userId(userId)
                .status("NOT_FOUND")
                .build();
    }

    /**
     * El TTL de compra no vive en queue-service.
     */
    public Long getTtl(String userId) {
        return -2L;
    }

    public Map<String, Long> getStats() {
        Long waiting = redisTemplate.opsForList()
                .size(WAITING_QUEUE_KEY);

        return Map.of(
                "waiting",
                waiting != null ? waiting : 0L
        );
    }

    /**
     * Limpia únicamente la cola de espera.
     *
     * No toca buying:sessions.
     */
    public void cleanupUser(String userId) {
        Long removed = redisTemplate.opsForList()
                .remove(WAITING_QUEUE_KEY, 0, userId);

        if (removed != null && removed > 0) {
            String currentStr = redisTemplate.opsForValue()
                    .get(WAITING_COUNT_KEY);

            long current = currentStr != null
                    ? Long.parseLong(currentStr)
                    : 0L;

            if (current > 0) {
                redisTemplate.opsForValue().decrement(WAITING_COUNT_KEY);
            }
        }

        log.info(
                "[QueueService] Cleanup de cola {}: removidosCola={}",
                userId,
                removed
        );
    }
}