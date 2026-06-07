package com.tickets.api_gateway.controller;

import com.tickets.api_gateway.client.PurchaseClient;
import com.tickets.api_gateway.dto.request.PurchaseRequest;
import com.tickets.api_gateway.dto.response.ExpireResponse;
import com.tickets.api_gateway.dto.response.PurchaseResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controlador público de compras.
 *
 * Importante:
 * El gateway no limpia waiting_queue.
 * El gateway no llama a queue-service cuando una compra expira.
 *
 * Solo delega en compra-service.
 */
@Slf4j
@RestController
@RequestMapping("/api/purchase")
@RequiredArgsConstructor
public class PurchaseController {

    private final PurchaseClient purchaseClient;

    /**
     * POST /api/purchase
     *
     * Body:
     * {
     *   "userId": "...",
     *   "ticketId": 123
     * }
     */
    @PostMapping
    public ResponseEntity<PurchaseResponse> purchase(
            @Valid @RequestBody PurchaseRequest request
    ) {
        log.debug(
                "PURCHASE | userId={} ticketId={}",
                request.getUserId(),
                request.getTicketId()
        );

        PurchaseResponse response = purchaseClient.purchase(request);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/purchase/expire/{userId}
     *
     * Lo dejo por compatibilidad, pero el front debería usar:
     * POST /api/buying/expire/{userId}
     */
    @PostMapping("/expire/{userId}")
    public ResponseEntity<ExpireResponse> expire(
            @PathVariable String userId
    ) {
        log.debug("EXPIRE purchase | userId={}", userId);

        ExpireResponse response = purchaseClient.expire(userId);
        return ResponseEntity.ok(response);
    }
}