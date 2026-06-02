package com.tickets.queue.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

// Campos null no se serializan: posición solo aparece en WAITING, ttl solo en BUYING, etc.
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QueueStatusResponse {
    private String  userId;
    private String  status;        // WAITING | BUYING | PURCHASED | EXPIRED | NOT_FOUND | REJECTED
    private Integer position;      // solo WAITING
    private Integer totalWaiting;  // solo WAITING
    private String  ticketId;      // solo BUYING
    private Long    ttlRemaining;  // solo BUYING (segundos)
    private String  message;       // legible para el frontend
    // true solo cuando el usuario ACABA de entrar a la cola ahora mismo
    // no se serializa en el JSON (uso interno del controller)
    @JsonIgnore
    private boolean justJoined;
}