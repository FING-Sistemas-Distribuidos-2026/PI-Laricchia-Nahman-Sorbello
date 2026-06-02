package com.tickets.api_gateway.controller;

import com.tickets.api_gateway.client.QueueClient;
import com.tickets.api_gateway.dto.response.JoinQueueResponse;
import com.tickets.api_gateway.dto.response.QueueActivationResponse;
import com.tickets.api_gateway.dto.response.QueueStatusResponse;
import com.tickets.api_gateway.dto.request.JoinQueueRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Controlador público de cola virtual.
 * Expone al frontend React los endpoints de cola y los delega
 * íntegramente a queue-service vía QueueClient (Feign).
 */
@Slf4j
@RestController
@RequestMapping("/api/queue")
@RequiredArgsConstructor
public class QueueController {

    private final QueueClient queueClient;

    // POST /api/queue/join

    /**
     * Incorpora al usuario a la cola virtual.
     *
     * @param request { userId }
     * @return { userId, status, position }
     */
    @PostMapping("/join")
    public ResponseEntity<JoinQueueResponse> join(
            @Valid @RequestBody JoinQueueRequest request) {

        log.debug("JOIN queue | userId={}", request.getUserId());
        JoinQueueResponse response = queueClient.join(request);
        return ResponseEntity.ok(response);
    }

    // GET /api/queue/status/{userId}

    /**
     * Consulta el estado actual del usuario en la cola.
     *
     * @param userId identificador del usuario
     * @return { status, position }
     */
    @GetMapping("/status/{userId}")
    public ResponseEntity<QueueStatusResponse> getStatus(
            @PathVariable String userId) {

        log.debug("STATUS queue | userId={}", userId);
        QueueStatusResponse response = queueClient.getStatus(userId);
        return ResponseEntity.ok(response);
    }



    // POST /api/queue/activate/{userId}
    /**
     * Mueve al usuario de WAITING a BUYING y reserva un ticket.
     * 404 y 409 de queue-service se propagan tal cual al frontend.
     *
     * @param userId UUID del usuario
     * @return { userId, ticketId, status: "BUYING" }
     */
    @PostMapping("/activate/{userId}")
    public ResponseEntity<QueueActivationResponse> activate(
            @PathVariable UUID userId) {

        log.debug("ACTIVATE queue | userId={}", userId);
        QueueActivationResponse response = queueClient.activate(userId);
        return ResponseEntity.ok(response);
    }

}