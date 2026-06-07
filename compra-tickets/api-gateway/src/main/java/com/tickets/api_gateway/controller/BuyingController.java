package com.tickets.api_gateway.controller;

import com.tickets.api_gateway.client.PurchaseClient;
import com.tickets.api_gateway.dto.response.ExpireResponse;
import com.tickets.api_gateway.dto.response.QueueStatusResponse;
import com.tickets.api_gateway.dto.response.QueueTtlResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Backend-for-Frontend para la pantalla de compra activa.
 *
 * Este controlador es el contrato público que usa el frontend cuando el usuario
 * ya salió de la cola y está en la ventana BUYING.
 *
 * No toca queue-service.
 * No toca waiting_queue.
 */
@Slf4j
@RestController
@RequestMapping("/api/buying")
@RequiredArgsConstructor
public class BuyingController {

    private final PurchaseClient purchaseClient;

    /**
     * GET /api/buying/ttl/{userId}
     *
     * Devuelve:
     * { "ttl": 123 }
     */
    @GetMapping("/ttl/{userId}")
    public ResponseEntity<QueueTtlResponse> getTtl(
            @PathVariable String userId
    ) {
        log.debug("BFF BUYING TTL | userId={}", userId);

        QueueTtlResponse response = purchaseClient.getBuyingTtl(userId);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/buying/status/{userId}
     *
     * Devuelve estado de compra activa:
     * BUYING | EXPIRED | NOT_FOUND
     */
    @GetMapping("/status/{userId}")
    public ResponseEntity<QueueStatusResponse> getStatus(
            @PathVariable String userId
    ) {
        log.debug("BFF BUYING STATUS | userId={}", userId);

        QueueStatusResponse response = purchaseClient.getBuyingSession(userId);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/buying/expire/{userId}
     *
     * Lo llama el front cuando su countdown llega a 0.
     */
    @PostMapping("/expire/{userId}")
    public ResponseEntity<ExpireResponse> expire(
            @PathVariable String userId
    ) {
        log.debug("BFF BUYING EXPIRE | userId={}", userId);

        ExpireResponse response = purchaseClient.expire(userId);
        return ResponseEntity.ok(response);
    }
}