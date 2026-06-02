package com.tickets.compra.service;

import com.tickets.compra.dto.PurchaseResponseDTO;
import com.tickets.compra.entity.EventLog;
import com.tickets.compra.entity.Purchase;
import com.tickets.compra.entity.QueueEntry;
import com.tickets.compra.entity.Ticket;
import com.tickets.compra.enums.PurchaseStatus;
import com.tickets.compra.enums.QueueStatus;
import com.tickets.compra.enums.TicketStatus;
import com.tickets.compra.repository.EventLogRepository;
import com.tickets.compra.repository.PurchaseRepository;
import com.tickets.compra.repository.QueueEntryRepository;
import com.tickets.compra.repository.TicketRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class PurchaseService {

    private final TicketRepository ticketRepository;
    private final QueueEntryRepository queueEntryRepository;
    private final PurchaseRepository purchaseRepository;
    private final EventLogRepository eventLogRepository;

    public PurchaseService(
            TicketRepository ticketRepository,
            QueueEntryRepository queueEntryRepository,
            PurchaseRepository purchaseRepository,
            EventLogRepository eventLogRepository) {
        this.ticketRepository = ticketRepository;
        this.queueEntryRepository = queueEntryRepository;
        this.purchaseRepository = purchaseRepository;
        this.eventLogRepository = eventLogRepository;
    }

    /**
     * Confirma la compra de un ticket ya reservado.
     * Lock pesimista sobre el ticket para evitar doble confirmación concurrente.
     */
    @Transactional
    public PurchaseResponseDTO confirmPurchase(UUID userId, Long ticketId) {

        Ticket ticket = ticketRepository.findByIdWithLock(ticketId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Ticket no encontrado: " + ticketId));

        if (ticket.getStatus() != TicketStatus.RESERVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ticket no está en RESERVED. Estado actual: " + ticket.getStatus());
        }
        if (!userId.equals(ticket.getReservedBy())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El ticket pertenece a otra reserva.");
        }

        // Ticket -> SOLD, limpiar reserva
        ticket.setStatus(TicketStatus.SOLD);
        ticket.setReservedBy(null);
        ticketRepository.save(ticket);

        // QueueEntry -> PURCHASED (@PreUpdate maneja updatedAt)
        QueueEntry queueEntry = queueEntryRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "QueueEntry no encontrada para: " + userId));
        queueEntry.setStatus(QueueStatus.PURCHASED);
        queueEntryRepository.save(queueEntry);

        // Purchase (@PrePersist maneja createdAt)
        Purchase purchase = new Purchase();
        purchase.setTicketId(ticketId);
        purchase.setUserId(userId);
        purchase.setStatus(PurchaseStatus.SUCCESS);
        purchaseRepository.save(purchase);

        // EventLog (@PrePersist maneja createdAt)
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", userId.toString());
        payload.put("ticketId", ticketId);

        EventLog log = new EventLog();
        log.setUserId(userId);
        log.setEventType("PURCHASE_SUCCESS");
        log.setPayload(payload);
        eventLogRepository.save(log);

        return PurchaseResponseDTO.builder()
                .userId(userId)
                .ticketId(ticketId)
                .status("PURCHASED")
                .message("Compra confirmada exitosamente.")
                .build();
    }

    /**
     * Expira la compra de un usuario que no completó en tiempo.
     * IDEMPOTENTE: si ya está EXPIRED o PURCHASED no modifica nada.
     * Purchase solo se registra si había un ticket reservado (ticketId NOT NULL en BD).
     */
    @Transactional
    public PurchaseResponseDTO expirePurchase(UUID userId) {

        QueueEntry queueEntry = queueEntryRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "QueueEntry no encontrada para: " + userId));

        // Idempotencia: estados terminales no se tocan
        QueueStatus current = queueEntry.getStatus();
        if (current == QueueStatus.EXPIRED || current == QueueStatus.PURCHASED) {
            return PurchaseResponseDTO.builder()
                    .userId(userId)
                    .status("EXPIRED")
                    .message("Ya procesado anteriormente.")
                    .build();
        }

        // Liberar ticket y registrar Purchase solo si había reserva activa
        ticketRepository.findFirstByStatusAndReservedBy(TicketStatus.RESERVED, userId)
                .ifPresent(ticket -> {
                    Long ticketId = ticket.getId();

                    ticket.setStatus(TicketStatus.AVAILABLE);
                    ticket.setReservedBy(null);
                    ticketRepository.save(ticket);

                    // Purchase requiere ticketId NOT NULL -> solo se crea si había ticket
                    Purchase purchase = new Purchase();
                    purchase.setTicketId(ticketId);
                    purchase.setUserId(userId);
                    purchase.setStatus(PurchaseStatus.EXPIRED);
                    purchaseRepository.save(purchase);
                });

        // QueueEntry -> EXPIRED (@PreUpdate maneja updatedAt)
        queueEntry.setStatus(QueueStatus.EXPIRED);
        queueEntryRepository.save(queueEntry);

        // EventLog
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", userId.toString());

        EventLog log = new EventLog();
        log.setUserId(userId);
        log.setEventType("PURCHASE_EXPIRED");
        log.setPayload(payload);
        eventLogRepository.save(log);

        return PurchaseResponseDTO.builder()
                .userId(userId)
                .status("EXPIRED")
                .message("Compra expirada por timeout.")
                .build();
    }
}