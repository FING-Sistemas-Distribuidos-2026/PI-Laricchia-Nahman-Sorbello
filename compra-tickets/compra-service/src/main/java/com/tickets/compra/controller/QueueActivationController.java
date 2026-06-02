package com.tickets.compra.controller;

import com.tickets.compra.dto.QueueActivationResponseDTO;
import com.tickets.compra.service.QueueActivationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/queue")
public class QueueActivationController {

    private final QueueActivationService queueActivationService;

    public QueueActivationController(QueueActivationService queueActivationService) {
        this.queueActivationService = queueActivationService;
    }

    /**
     * POST /api/queue/activate/{userId}
     * Llamado por el Scheduler para mover un usuario de WAITING a BUYING.
     * Reserva un ticket atómicamente y devuelve el ticketId.
     * 200 -> { userId, ticketId, status: "BUYING" }
     * 404 -> QueueEntry no encontrada
     * 409 -> usuario no está en WAITING, o no hay tickets disponibles
     */
    @PostMapping("/activate/{userId}")
    public ResponseEntity<QueueActivationResponseDTO> activate(@PathVariable UUID userId) {
        return ResponseEntity.ok(queueActivationService.activate(userId));
    }
}