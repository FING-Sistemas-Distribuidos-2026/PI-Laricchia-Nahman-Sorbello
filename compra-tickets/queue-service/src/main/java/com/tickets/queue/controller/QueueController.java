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
@RequestMapping("/queue")   // + context-path /api → resulta en /api/queue
@RequiredArgsConstructor
public class QueueController {

    private final QueueService queueService;

    /**
     * POST /api/queue/join
     * Primera línea de defensa: valida userId antes de tocar Redis o BD.
     */
    @PostMapping("/join")
    public ResponseEntity<QueueStatusResponse> join(@RequestBody JoinQueueRequest request) {
        if (request.getUserId() == null || request.getUserId().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        QueueStatusResponse response = queueService.joinQueue(request);

        HttpStatus httpStatus;
        switch (response.getStatus()) {
            case "WAITING" -> {
                // "Ingresaste" = recién entró → 201
                // "Ya estás en la cola" = duplicado → 409
                httpStatus = "Ingresaste a la cola de espera".equals(response.getMessage())
                        ? HttpStatus.CREATED
                        : HttpStatus.CONFLICT;
            }
            default -> httpStatus = HttpStatus.CONFLICT;
        }

        return ResponseEntity.status(httpStatus).body(response);
    }

    /**
     * GET /api/queue/status/{userId}
     * El frontend hace polling cada N segundos con este endpoint.
     */
    @GetMapping("/status/{userId}")
    public ResponseEntity<QueueStatusResponse> status(@PathVariable String userId) {
        return ResponseEntity.ok(queueService.getStatus(userId));
    }

    /**
     * GET /api/queue/ttl/{userId}
     * Contrato: { "ttl": <segundos> }
     * -2 = clave no existe | -1 = sin TTL | >=0 = segundos restantes
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

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "ok", "service", "queue-service"));
    }
}