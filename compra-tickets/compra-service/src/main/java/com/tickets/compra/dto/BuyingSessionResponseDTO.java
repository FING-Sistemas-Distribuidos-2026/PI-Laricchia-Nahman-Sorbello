package com.tickets.compra.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BuyingSessionResponseDTO {

    private UUID userId;

    /**
     * BUYING | EXPIRED | NOT_FOUND
     */
    private String status;

    private Long ticketId;

    /**
     * Segundos restantes.
     *
     * 0  -> existe sesión pero ya venció.
     * -2 -> no existe sesión activa.
     */
    private Long ttlRemaining;

    private Instant activatedAt;
    private Instant expiresAt;

    /**
     * Para debug/documentación.
     * Siempre debería ser buying:sessions.
     */
    private String source;

    private String message;
}