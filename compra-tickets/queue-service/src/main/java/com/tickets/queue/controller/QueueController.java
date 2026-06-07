package com.tickets.queue.controller;

import com.tickets.queue.dto.JoinQueueRequest;
import com.tickets.queue.dto.QueueStatusResponse;
import com.tickets.queue.service.QueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/queue")
@RequiredArgsConstructor
public class QueueController {

    private final QueueService queueService;

    /**
     * POST /api/queue/join
     *
     * Este endpoint SOLO mete al usuario en la cola de espera.
     *
     * Importante:
     * - Si queda WAITING, debe responder 200/201, no 409.
     * - Si responde 409, el front lo interpreta como error.
     */
    @PostMapping("/join")
    public ResponseEntity<QueueStatusResponse> join(@RequestBody JoinQueueRequest request) {
        if (request.getUserId() == null || request.getUserId().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        QueueStatusResponse response = queueService.joinQueue(request);

        return switch (response.getStatus()) {
            case "WAITING" -> {
                if (response.isJustJoined()) {
                    yield ResponseEntity.status(HttpStatus.CREATED).body(response);
                }

                yield ResponseEntity.ok(response);
            }

            case "REJECTED" ->
                    ResponseEntity.status(HttpStatus.CONFLICT).body(response);

            default ->
                    ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        };
    }

    /**
     * GET /api/queue/status/{userId}
     */
    @GetMapping("/status/{userId}")
    public ResponseEntity<QueueStatusResponse> status(@PathVariable String userId) {
        return ResponseEntity.ok(queueService.getStatus(userId));
    }

    /**
     * Endpoint legado.
     *
     * El TTL real de compra activa ahora lo maneja compra-service.
     */
    @GetMapping("/ttl/{userId}")
    public ResponseEntity<Map<String, Long>> ttl(@PathVariable String userId) {
        Long ttl = queueService.getTtl(userId);
        return ResponseEntity.ok(Map.of("ttl", ttl != null ? ttl : -2L));
    }

    /**
     * GET /api/queue/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> stats() {
        return ResponseEntity.ok(queueService.getStats());
    }

    /**
     * GET /api/queue/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "service", "queue-service"
        ));
    }

    /**
     * DELETE /api/queue/user/{userId}
     *
     * Limpia únicamente waiting_queue.
     * No toca buying:sessions.
     */
    @DeleteMapping("/user/{userId}")
    public ResponseEntity<Map<String, String>> cleanup(@PathVariable String userId) {
        queueService.cleanupUser(userId);

        return ResponseEntity.ok(Map.of(
                "status", "removed",
                "userId", userId
        ));
    }
}