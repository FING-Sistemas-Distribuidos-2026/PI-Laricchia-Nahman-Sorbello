package com.tickets.compra.service;

import com.tickets.compra.entity.QueueEntry;
import com.tickets.compra.entity.Ticket;
import com.tickets.compra.enums.QueueStatus;
import com.tickets.compra.enums.TicketStatus;
import com.tickets.compra.repository.QueueEntryRepository;
import com.tickets.compra.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BuyingSessionPersistenceScheduler {

    private final ActiveBuyingRedisService activeBuyingRedisService;
    private final QueueEntryRepository queueEntryRepository;
    private final TicketRepository ticketRepository;

    /**
     * Snapshot periódico del HASH Redis buying:sessions hacia PostgreSQL.
     *
     * Esto NO expira usuarios.
     * Esto NO toca waiting_queue.
     * Esto NO decide la lógica del TTL.
     *
     * El TTL visible lo maneja el front/BFF consultando /api/buying/ttl
     * y llamando /api/buying/expire cuando llega a 0.
     */
    @Scheduled(fixedDelayString = "${buying.snapshot.interval.ms:30000}")
    @Transactional
    public void persistBuyingSessions() {
        var sessions = activeBuyingRedisService.findAllSessions();

        if (sessions.isEmpty()) {
            return;
        }

        log.info(
                "[BuyingSnapshot] Persistiendo {} sesiones desde Redis HASH {}",
                sessions.size(),
                ActiveBuyingRedisService.BUYING_HASH_KEY
        );

        for (ActiveBuyingRedisService.ActiveBuyingSession session : sessions) {
            var userId = java.util.UUID.fromString(session.userId());

            QueueEntry entry = queueEntryRepository
                    .findByUserId(userId)
                    .orElse(null);

            if (entry != null && entry.getStatus() == QueueStatus.WAITING) {
                entry.setStatus(QueueStatus.BUYING);
                queueEntryRepository.save(entry);
            }

            Ticket ticket = ticketRepository
                    .findById(session.ticketId())
                    .orElse(null);

            if (ticket != null && ticket.getStatus() == TicketStatus.AVAILABLE) {
                ticket.setStatus(TicketStatus.RESERVED);
                ticket.setReservedBy(userId);
                ticketRepository.save(ticket);
            }

            activeBuyingRedisService.markPersisted(userId);
        }
    }
}