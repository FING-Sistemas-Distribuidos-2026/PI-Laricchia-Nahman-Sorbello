package com.tickets.api_gateway.controller;

import com.tickets.api_gateway.client.PurchaseClient;
import com.tickets.api_gateway.dto.response.QueueActivationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/queue")
@RequiredArgsConstructor
public class QueuePurchaseController {

    private final PurchaseClient purchaseClient;

    @PostMapping("/activate/{userId}")
    public ResponseEntity<QueueActivationResponse> activate(
            @PathVariable UUID userId) {
        log.debug("ACTIVATE queue | userId={}", userId);
        QueueActivationResponse response = purchaseClient.activate(userId);
        return ResponseEntity.ok(response);
    }
}