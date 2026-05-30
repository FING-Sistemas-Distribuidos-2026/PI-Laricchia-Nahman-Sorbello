package com.tickets.compra.controller;

import com.tickets.compra.dto.PurchaseRequestDTO;
import com.tickets.compra.dto.PurchaseResponseDTO;
import com.tickets.compra.service.PurchaseService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/purchase")
public class PurchaseController {

    private final PurchaseService purchaseService;

    public PurchaseController(PurchaseService purchaseService) {
        this.purchaseService = purchaseService;
    }

    /**
     * POST /api/purchase
     * Body: { "userId": "...", "ticketId": 123 }
     */
    @PostMapping
    public ResponseEntity<PurchaseResponseDTO> confirmPurchase(
            @Valid @RequestBody PurchaseRequestDTO request) {

        PurchaseResponseDTO response = purchaseService.confirmPurchase(
                request.getUserId(), request.getTicketId());
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/purchase/expire/{userId}
     */
    @PostMapping("/expire/{userId}")
    public ResponseEntity<PurchaseResponseDTO> expirePurchase(
            @PathVariable UUID userId) {

        PurchaseResponseDTO response = purchaseService.expirePurchase(userId);
        return ResponseEntity.ok(response);
    }
}