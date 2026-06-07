package com.tickets.compra.controller;

import com.tickets.compra.dto.BuyingSessionResponseDTO;
import com.tickets.compra.dto.PurchaseRequestDTO;
import com.tickets.compra.dto.PurchaseResponseDTO;
import com.tickets.compra.service.ActiveBuyingRedisService;
import com.tickets.compra.service.PurchaseService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/purchase")
public class PurchaseController {

    private final PurchaseService purchaseService;
    private final ActiveBuyingRedisService activeBuyingRedisService;

    public PurchaseController(
            PurchaseService purchaseService,
            ActiveBuyingRedisService activeBuyingRedisService
    ) {
        this.purchaseService = purchaseService;
        this.activeBuyingRedisService = activeBuyingRedisService;
    }

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
    public ResponseEntity<PurchaseResponseDTO> confirmPurchase(
            @Valid @RequestBody PurchaseRequestDTO request
    ) {
        PurchaseResponseDTO response = purchaseService.confirmPurchase(
                request.getUserId(),
                request.getTicketId()
        );

        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/purchase/expire/{userId}
     *
     * Lo llama el BFF/frontend cuando el countdown llega a 0.
     */
    @PostMapping("/expire/{userId}")
    public ResponseEntity<PurchaseResponseDTO> expirePurchase(
            @PathVariable UUID userId
    ) {
        PurchaseResponseDTO response = purchaseService.expirePurchase(userId);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/purchase/session/{userId}
     *
     * Consulta exclusivamente la ventana BUYING.
     *
     * Fuente:
     * Redis HASH buying:sessions.
     *
     * No toca waiting_queue.
     */
    @GetMapping("/session/{userId}")
    public ResponseEntity<BuyingSessionResponseDTO> getBuyingSession(
            @PathVariable UUID userId
    ) {
        return ResponseEntity.ok(
                activeBuyingRedisService.findSession(userId)
                        .map(session -> {
                            long ttl = activeBuyingRedisService
                                    .ttlRemainingSeconds(userId);

                            boolean expired = ttl <= 0L;

                            return BuyingSessionResponseDTO.builder()
                                    .userId(userId)
                                    .status(expired ? "EXPIRED" : "BUYING")
                                    .ticketId(session.ticketId())
                                    .ttlRemaining(Math.max(0L, ttl))
                                    .activatedAt(session.activatedAt())
                                    .expiresAt(session.expiresAt())
                                    .source(ActiveBuyingRedisService.BUYING_HASH_KEY)
                                    .message(expired
                                            ? "La ventana de compra expiró."
                                            : "Usuario en ventana de compra activa.")
                                    .build();
                        })
                        .orElseGet(() -> BuyingSessionResponseDTO.builder()
                                .userId(userId)
                                .status("NOT_FOUND")
                                .ttlRemaining(-2L)
                                .source(ActiveBuyingRedisService.BUYING_HASH_KEY)
                                .message("No existe sesión de compra activa para el usuario.")
                                .build())
        );
    }

    /**
     * GET /api/purchase/ttl/{userId}
     *
     * Devuelve:
     * { "ttl": segundos }
     *
     * -2 si no existe sesión activa.
     *  0 si existe pero ya venció.
     */
    @GetMapping("/ttl/{userId}")
    public ResponseEntity<Map<String, Long>> getBuyingTtl(
            @PathVariable UUID userId
    ) {
        long ttl = activeBuyingRedisService.ttlRemainingSeconds(userId);
        return ResponseEntity.ok(Map.of("ttl", ttl));
    }

    /**
     * GET /api/purchase/active-count
     *
     * Lo usa scheduler-service para saber cuántos están comprando.
     *
     * El scheduler no lee directamente buying:sessions.
     */
    @GetMapping("/active-count")
    public ResponseEntity<Map<String, Long>> getActiveBuyingCount() {
        return ResponseEntity.ok(
                Map.of("count", activeBuyingRedisService.countSessions())
        );
    }
}