package com.tickets.compra.service;

import com.tickets.compra.client.SystemParametersClient;
import com.tickets.compra.dto.QueueActivationResponseDTO;
import com.tickets.compra.entity.EventLog;
import com.tickets.compra.entity.QueueEntry;
import com.tickets.compra.entity.Ticket;
import com.tickets.compra.enums.QueueStatus;
import com.tickets.compra.enums.TicketStatus;
import com.tickets.compra.repository.EventLogRepository;
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
public class QueueActivationService {

    private static final long DEFAULT_PURCHASE_TTL_SECONDS = 600L;

    private final QueueEntryRepository queueEntryRepository;
    private final TicketRepository ticketRepository;
    private final EventLogRepository eventLogRepository;
    private final ActiveBuyingRedisService activeBuyingRedisService;
    private final SystemParametersClient systemParametersClient;

    public QueueActivationService(
            QueueEntryRepository queueEntryRepository,
            TicketRepository ticketRepository,
            EventLogRepository eventLogRepository,
            ActiveBuyingRedisService activeBuyingRedisService,
            SystemParametersClient systemParametersClient
    ) {
        this.queueEntryRepository = queueEntryRepository;
        this.ticketRepository = ticketRepository;
        this.eventLogRepository = eventLogRepository;
        this.activeBuyingRedisService = activeBuyingRedisService;
        this.systemParametersClient = systemParametersClient;
    }

    /**
     * Activa una ventana de compra.
     *
     * Importante:
     * - La cola de espera sigue siendo de queue-service/scheduler-service.
     * - compra-service NO toca waiting_queue.
     * - El usuario que ya puede comprar se guarda en Redis HASH buying:sessions.
     */
    @Transactional
    public QueueActivationResponseDTO activate(UUID userId) {

        QueueEntry queueEntry = queueEntryRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "QueueEntry no encontrada para: " + userId
                ));

        if (queueEntry.getStatus() == QueueStatus.BUYING) {
            Ticket ticketActual = ticketRepository
                    .findFirstByStatusAndReservedBy(TicketStatus.RESERVED, userId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Usuario en BUYING pero sin ticket reservado."
                    ));

            if (activeBuyingRedisService.findSession(userId).isEmpty()) {
                activeBuyingRedisService.putSession(
                        userId,
                        ticketActual.getId(),
                        getPurchaseTtlSeconds()
                );
            }

            return QueueActivationResponseDTO.builder()
                    .userId(userId)
                    .ticketId(ticketActual.getId())
                    .status("BUYING")
                    .message("Usuario ya estaba en ventana de compra.")
                    .build();
        }

        if (queueEntry.getStatus() != QueueStatus.WAITING) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El usuario no está en WAITING. Estado actual: "
                            + queueEntry.getStatus()
            );
        }

        Ticket ticket = ticketRepository.findFirstAvailableWithLock()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "No hay tickets disponibles."
                ));

        ticket.setStatus(TicketStatus.RESERVED);
        ticket.setReservedBy(userId);
        ticketRepository.save(ticket);

        queueEntry.setStatus(QueueStatus.BUYING);
        queueEntryRepository.save(queueEntry);

        activeBuyingRedisService.putSession(
                userId,
                ticket.getId(),
                getPurchaseTtlSeconds()
        );

        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", userId.toString());
        payload.put("ticketId", ticket.getId());
        payload.put("redisHash", ActiveBuyingRedisService.BUYING_HASH_KEY);

        EventLog log = new EventLog();
        log.setUserId(userId);
        log.setEventType("ENTER_BUYING");
        log.setPayload(payload);
        eventLogRepository.save(log);

        return QueueActivationResponseDTO.builder()
                .userId(userId)
                .ticketId(ticket.getId())
                .status("BUYING")
                .message("Usuario activado para compra.")
                .build();
    }

    private long getPurchaseTtlSeconds() {
        try {
            String value = systemParametersClient.get("PURCHASE_TTL_SECONDS");
            return value != null
                    ? Long.parseLong(value)
                    : DEFAULT_PURCHASE_TTL_SECONDS;
        } catch (Exception e) {
            return DEFAULT_PURCHASE_TTL_SECONDS;
        }
    }
}