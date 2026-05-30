package com.tickets.compra.repository;

import com.tickets.compra.entity.QueueEntry;
import com.tickets.compra.enums.QueueStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface QueueEntryRepository extends JpaRepository<QueueEntry, Long> {
    Optional<QueueEntry> findByUserIdAndStatus(UUID userId, QueueStatus status);
    Optional<QueueEntry> findByUserId(UUID userId);
    long countByStatus(QueueStatus status);
}
