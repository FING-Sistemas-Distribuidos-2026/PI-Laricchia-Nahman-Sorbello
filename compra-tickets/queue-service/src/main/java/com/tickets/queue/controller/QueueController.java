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

    @PostMapping("/join")
    public ResponseEntity<QueueStatusResponse> join(@RequestBody JoinQueueRequest request) {
        if (request.getUserId() == null || request.getUserId().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        QueueStatusResponse response = queueService.joinQueue(request);
        HttpStatus httpStatus;
        switch (response.getStatus()) {
            case "WAITING" -> {
                httpStatus = "Ingresaste a la cola de espera".equals(response.getMessage())
                        ? HttpStatus.CREATED
                        : HttpStatus.CONFLICT;
            }
            default -> httpStatus = HttpStatus.CONFLICT;
        }
        return ResponseEntity.status(httpStatus).body(response);
    }

    @GetMapping("/status/{userId}")
    public ResponseEntity<QueueStatusResponse> status(@PathVariable String userId) {
        return ResponseEntity.ok(queueService.getStatus(userId));
    }

    @GetMapping("/ttl/{userId}")
    public ResponseEntity<Map<String, Long>> ttl(@PathVariable String userId) {
        Long ttl = queueService.getTtl(userId);
        return ResponseEntity.ok(Map.of("ttl", ttl != null ? ttl : -2L));
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> stats() {
        return ResponseEntity.ok(queueService.getStats());
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "ok", "service", "queue-service"));
    }

    /**
     * DELETE /api/queue/user/{userId}
     * Limpia al usuario de waiting_queue en Redis.
     * Llamado por el gateway después de /api/purchase/expire/{userId}.
     */
    @DeleteMapping("/user/{userId}")
    public ResponseEntity<Map<String, String>> cleanup(@PathVariable String userId) {
        queueService.cleanupUser(userId);
        return ResponseEntity.ok(Map.of("status", "removed", "userId", userId));
    }
}