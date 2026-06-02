package com.tickets.queue.repository;

import com.tickets.queue.model.QueueEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface QueueEntryRepository extends JpaRepository<QueueEntry, Long> {
    Optional<QueueEntry> findByUserId(String userId);
}