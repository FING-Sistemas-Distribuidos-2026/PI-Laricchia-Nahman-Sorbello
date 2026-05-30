package com.tickets.compra.service;

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

    private final QueueEntryRepository queueEntryRepository;
    private final TicketRepository ticketRepository;
    private final EventLogRepository eventLogRepository;

    public QueueActivationService(
            QueueEntryRepository queueEntryRepository,
            TicketRepository ticketRepository,
            EventLogRepository eventLogRepository) {
        this.queueEntryRepository = queueEntryRepository;
        this.ticketRepository = ticketRepository;
        this.eventLogRepository = eventLogRepository;
    }

    /**
     * Mueve un usuario de WAITING a BUYING y le reserva un ticket.
     * Llamado exclusivamente por el Scheduler.
     *
     * Atomicidad: la búsqueda del ticket con lock pesimista y la actualización
     * de QueueEntry ocurren en la misma transacción. Si cualquier paso falla,
     * rollback completo — no queda estado inconsistente.
     *
     * Idempotente: si el usuario ya está en BUYING devuelve su ticket actual
     * sin crear registros nuevos.
     */
    @Transactional
    public QueueActivationResponseDTO activate(UUID userId) {

        // 1. Buscar QueueEntry del usuario
        QueueEntry queueEntry = queueEntryRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "QueueEntry no encontrada para: " + userId));

        // 2. Idempotencia: si ya está en BUYING devolver el ticket que tiene reservado
        if (queueEntry.getStatus() == QueueStatus.BUYING) {
            Ticket ticketActual = ticketRepository
                    .findFirstByStatusAndReservedBy(TicketStatus.RESERVED, userId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Usuario en BUYING pero sin ticket reservado. Estado inconsistente."));
            return QueueActivationResponseDTO.builder()
                    .userId(userId)
                    .ticketId(ticketActual.getId())
                    .status("BUYING")
                    .message("Usuario ya estaba en ventana de compra.")
                    .build();
        }

        // 3. Validar que el usuario esté en WAITING
        if (queueEntry.getStatus() != QueueStatus.WAITING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El usuario no está en WAITING. Estado actual: " + queueEntry.getStatus());
        }

        // 4. Buscar ticket disponible con lock pesimista (FOR UPDATE SKIP LOCKED)
        //    Si no hay tickets lanza 409 — el Scheduler no debe reintentar este ciclo
        Ticket ticket = ticketRepository.findFirstAvailableWithLock()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "No hay tickets disponibles."));

        // 5. Reservar ticket para este usuario
        ticket.setStatus(TicketStatus.RESERVED);
        ticket.setReservedBy(userId);
        ticketRepository.save(ticket);

        // 6. QueueEntry -> BUYING (@PreUpdate maneja updatedAt)
        queueEntry.setStatus(QueueStatus.BUYING);
        queueEntryRepository.save(queueEntry);

        // 7. EventLog
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", userId.toString());
        payload.put("ticketId", ticket.getId());

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
}