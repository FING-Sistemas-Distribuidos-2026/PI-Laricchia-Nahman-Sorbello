package com.tickets.api_gateway.controller;

import com.tickets.api_gateway.client.PurchaseClient;
import com.tickets.api_gateway.dto.response.ExpireResponse;
import com.tickets.api_gateway.dto.response.PurchaseResponse;
import com.tickets.api_gateway.dto.request.PurchaseRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador público de compras.
 * Expone al frontend los endpoints de compra / expiración y los delega
 * a compra-service vía PurchaseClient (Feign).
 */
@Slf4j
@RestController
@RequestMapping("/api/purchase")
@RequiredArgsConstructor
public class PurchaseController {

    private final PurchaseClient purchaseClient;

    //POST /api/purchase

    /**
     * Ejecuta la compra de una entrada.
     *
     * @param request { userId, ticketId }
     * @return { status: "PURCHASED" }
     */
    @PostMapping
    public ResponseEntity<PurchaseResponse> purchase(
            @Valid @RequestBody PurchaseRequest request) {
        log.debug("PURCHASE | userId={} ticketId={}", request.getUserId(), request.getTicketId());
        PurchaseResponse response = purchaseClient.purchase(request);
        return ResponseEntity.ok(response);
    }

    // POST /api/purchase/expire/{userId}

    /**
     * Expira el slot de compra de un usuario (llamado por el scheduler o el propio servicio).
     *
     * @param userId identificador del usuario
     * @return { status: "EXPIRED" }
     */
    @PostMapping("/expire/{userId}")
    public ResponseEntity<ExpireResponse> expire(
            @PathVariable String userId) {

        log.debug("EXPIRE purchase | userId={}", userId);
        ExpireResponse response = purchaseClient.expire(userId);
        return ResponseEntity.ok(response);
    }
}