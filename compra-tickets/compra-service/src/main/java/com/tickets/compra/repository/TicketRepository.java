package com.tickets.compra.repository;

import com.tickets.compra.entity.Ticket;
import com.tickets.compra.enums.TicketStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    @Query(value = "SELECT * FROM tickets WHERE status = 'AVAILABLE' LIMIT 1 FOR UPDATE SKIP LOCKED", nativeQuery = true)
    Optional<Ticket> findFirstAvailableWithLock();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Ticket t WHERE t.id = :id")
    Optional<Ticket> findByIdWithLock(@Param("id") Long id);

    Optional<Ticket> findByReservedBy(UUID userId);

    Optional<Ticket> findByIdAndReservedBy(Long id, UUID userId);

    Optional<Ticket> findFirstByStatusAndReservedBy(TicketStatus status, UUID userId);

    long countByStatus(TicketStatus status);
}