package com.tickets.api_gateway.controller;

import com.tickets.api_gateway.client.PurchaseClient;
import com.tickets.api_gateway.client.QueueClient;
import com.tickets.api_gateway.dto.request.JoinQueueRequest;
import com.tickets.api_gateway.dto.response.JoinQueueResponse;
import com.tickets.api_gateway.dto.response.QueueStatusResponse;
import com.tickets.api_gateway.dto.response.QueueTtlResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controlador público de cola virtual.
 *
 * Separación:
 *
 * WAITING:
 *   queue-service
 *   Redis LIST waiting_queue
 *
 * BUYING:
 *   compra-service
 *   Redis HASH buying:sessions
 */
@Slf4j
@RestController
@RequestMapping("/api/queue")
@RequiredArgsConstructor
public class QueueController {

    private final QueueClient queueClient;
    private final PurchaseClient purchaseClient;

    /**
     * POST /api/queue/join
     *
     * Solo mete al usuario en waiting_queue.
     */
    @PostMapping("/join")
    public ResponseEntity<JoinQueueResponse> join(
            @Valid @RequestBody JoinQueueRequest request
    ) {
        log.debug("JOIN queue | userId={}", request.getUserId());

        JoinQueueResponse response = queueClient.join(request);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/queue/status/{userId}
     *
     * Para el frontend general.
     *
     * Primero consulta si ya está BUYING en compra-service.
     * Si no está comprando, consulta queue-service.
     */
    @GetMapping("/status/{userId}")
    public ResponseEntity<QueueStatusResponse> getStatus(
            @PathVariable String userId
    ) {
        log.debug("STATUS queue/buying | userId={}", userId);

        try {
            QueueStatusResponse buying = purchaseClient.getBuyingSession(userId);

            if (buying != null &&
                    ("BUYING".equals(buying.getStatus())
                            || "EXPIRED".equals(buying.getStatus()))) {
                return ResponseEntity.ok(buying);
            }
        } catch (Exception e) {
            log.warn(
                    "No se pudo consultar sesión BUYING en compra-service para {}. Fallback a queue-service.",
                    userId
            );
        }

        QueueStatusResponse response = queueClient.getStatus(userId);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/queue/ttl/{userId}
     *
     * Compatibilidad.
     * El TTL real sale de compra-service.
     */
    @GetMapping("/ttl/{userId}")
    public ResponseEntity<QueueTtlResponse> getTtl(
            @PathVariable String userId
    ) {
        log.debug("TTL buying | userId={}", userId);

        QueueTtlResponse response = purchaseClient.getBuyingTtl(userId);
        return ResponseEntity.ok(response);
    }
}