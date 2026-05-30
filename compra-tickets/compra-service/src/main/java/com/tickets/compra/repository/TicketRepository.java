package com.tickets.compra.repository;

import com.tickets.compra.entity.Ticket;
import com.tickets.compra.enums.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    @Query(value = "SELECT * FROM tickets WHERE status = 'AVAILABLE' LIMIT 1 FOR UPDATE SKIP LOCKED", nativeQuery = true)
    Optional<Ticket> findFirstAvailableWithLock();

    Optional<Ticket> findByReservedBy(UUID userId);

    long countByStatus(TicketStatus status);
}
