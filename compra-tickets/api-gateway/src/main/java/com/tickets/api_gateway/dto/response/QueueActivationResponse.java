package com.tickets.api_gateway.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

/**
 * Respuesta de POST /api/queue/activate/{userId}
 * Contrato: { userId, ticketId, status: "BUYING" }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueueActivationResponse {
    private UUID userId;
    private Long ticketId;
    private String status;
}