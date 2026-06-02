package com.tickets.compra.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tickets")
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private com.tickets.compra.enums.TicketStatus status;

    @Column(name = "reserved_by")
    private UUID reservedBy;

    public Ticket() {}

    @PrePersist
    public void prePersist() {
        if (this.status == null) {
            this.status = com.tickets.compra.enums.TicketStatus.AVAILABLE;
        }
    }
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public com.tickets.compra.enums.TicketStatus getStatus() { return status; }
    public void setStatus(com.tickets.compra.enums.TicketStatus status) { this.status = status; }

    public UUID getReservedBy() { return reservedBy; }
    public void setReservedBy(UUID reservedBy) { this.reservedBy = reservedBy; }

}
