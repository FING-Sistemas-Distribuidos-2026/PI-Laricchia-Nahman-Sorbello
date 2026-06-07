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

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class PurchaseService {

    private final TicketRepository ticketRepository;
    private final QueueEntryRepository queueEntryRepository;
    private final PurchaseRepository purchaseRepository;
    private final EventLogRepository eventLogRepository;
    private final ActiveBuyingRedisService activeBuyingRedisService;

    public PurchaseService(
            TicketRepository ticketRepository,
            QueueEntryRepository queueEntryRepository,
            PurchaseRepository purchaseRepository,
            EventLogRepository eventLogRepository,
            ActiveBuyingRedisService activeBuyingRedisService
    ) {
        this.ticketRepository = ticketRepository;
        this.queueEntryRepository = queueEntryRepository;
        this.purchaseRepository = purchaseRepository;
        this.eventLogRepository = eventLogRepository;
        this.activeBuyingRedisService = activeBuyingRedisService;
    }

    /**
     * Confirma la compra de un ticket reservado.
     *
     * Fuente de verdad de la ventana activa:
     * Redis HASH buying:sessions.
     *
     * Este método NO toca waiting_queue.
     */
    @Transactional
    public PurchaseResponseDTO confirmPurchase(UUID userId, Long ticketId) {

        ActiveBuyingRedisService.ActiveBuyingSession session =
                activeBuyingRedisService.findSession(userId)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "El usuario no tiene ventana de compra activa."
                        ));

        if (!ticketId.equals(session.ticketId())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El ticket no coincide con la sesión de compra activa."
            );
        }

        /**
         * Validación fuerte del backend.
         *
         * Aunque el front mire el TTL antes de confirmar,
         * el backend no puede confiar solamente en el front.
         *
         * Si venció, expira y devuelve status EXPIRED.
         */
        if (!session.expiresAt().isAfter(Instant.now())) {
            return expirePurchase(userId);
        }

        Ticket ticket = ticketRepository.findByIdWithLock(ticketId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Ticket no encontrado: " + ticketId
                ));

        if (ticket.getStatus() != TicketStatus.RESERVED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Ticket no está en RESERVED. Estado actual: "
                            + ticket.getStatus()
            );
        }

        if (!userId.equals(ticket.getReservedBy())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El ticket pertenece a otra reserva."
            );
        }

        ticket.setStatus(TicketStatus.SOLD);
        ticket.setReservedBy(null);
        ticketRepository.save(ticket);

        QueueEntry queueEntry = queueEntryRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "QueueEntry no encontrada para: " + userId
                ));

        queueEntry.setStatus(QueueStatus.PURCHASED);
        queueEntryRepository.save(queueEntry);

        Purchase purchase = new Purchase();
        purchase.setTicketId(ticketId);
        purchase.setUserId(userId);
        purchase.setStatus(PurchaseStatus.SUCCESS);
        purchaseRepository.save(purchase);

        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", userId.toString());
        payload.put("ticketId", ticketId);
        payload.put("redisHash", ActiveBuyingRedisService.BUYING_HASH_KEY);

        EventLog log = new EventLog();
        log.setUserId(userId);
        log.setEventType("PURCHASE_SUCCESS");
        log.setPayload(payload);
        eventLogRepository.save(log);

        activeBuyingRedisService.removeSession(userId);

        return PurchaseResponseDTO.builder()
                .userId(userId)
                .ticketId(ticketId)
                .status("PURCHASED")
                .message("Compra confirmada exitosamente.")
                .build();
    }

    /**
     * Expira la ventana de compra.
     *
     * Es idempotente:
     * si ya estaba EXPIRED o PURCHASED, no rompe nada.
     */
    @Transactional
    public PurchaseResponseDTO expirePurchase(UUID userId) {

        QueueEntry queueEntry = queueEntryRepository
                .findByUserId(userId)
                .orElse(null);

        if (queueEntry == null) {
            activeBuyingRedisService.removeSession(userId);

            return PurchaseResponseDTO.builder()
                    .userId(userId)
                    .status("EXPIRED")
                    .message("Sesión expirada o ya procesada.")
                    .build();
        }

        if (queueEntry.getStatus() == QueueStatus.PURCHASED) {
            activeBuyingRedisService.removeSession(userId);

            return PurchaseResponseDTO.builder()
                    .userId(userId)
                    .status("PURCHASED")
                    .message("La compra ya había sido confirmada.")
                    .build();
        }

        if (queueEntry.getStatus() == QueueStatus.EXPIRED) {
            activeBuyingRedisService.removeSession(userId);

            return PurchaseResponseDTO.builder()
                    .userId(userId)
                    .status("EXPIRED")
                    .message("La compra ya había expirado.")
                    .build();
        }

        ticketRepository.findFirstByStatusAndReservedBy(
                TicketStatus.RESERVED,
                userId
        ).ifPresent(ticket -> {
            Long ticketId = ticket.getId();

            ticket.setStatus(TicketStatus.AVAILABLE);
            ticket.setReservedBy(null);
            ticketRepository.save(ticket);

            Purchase purchase = new Purchase();
            purchase.setTicketId(ticketId);
            purchase.setUserId(userId);
            purchase.setStatus(PurchaseStatus.EXPIRED);
            purchaseRepository.save(purchase);
        });

        queueEntry.setStatus(QueueStatus.EXPIRED);
        queueEntryRepository.save(queueEntry);

        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", userId.toString());
        payload.put("redisHash", ActiveBuyingRedisService.BUYING_HASH_KEY);

        EventLog log = new EventLog();
        log.setUserId(userId);
        log.setEventType("PURCHASE_EXPIRED");
        log.setPayload(payload);
        eventLogRepository.save(log);

        activeBuyingRedisService.removeSession(userId);

        return PurchaseResponseDTO.builder()
                .userId(userId)
                .status("EXPIRED")
                .message("Compra expirada por timeout.")
                .build();
    }
}