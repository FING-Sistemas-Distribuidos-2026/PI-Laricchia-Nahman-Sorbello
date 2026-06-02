package com.tickets.compra.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "purchases")
public class Purchase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private com.tickets.compra.enums.PurchaseStatus status;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public Purchase() {}

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTicketId() { return ticketId; }
    public void setTicketId(Long ticketId) { this.ticketId = ticketId; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public com.tickets.compra.enums.PurchaseStatus getStatus() { return status; }
    public void setStatus(com.tickets.compra.enums.PurchaseStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
