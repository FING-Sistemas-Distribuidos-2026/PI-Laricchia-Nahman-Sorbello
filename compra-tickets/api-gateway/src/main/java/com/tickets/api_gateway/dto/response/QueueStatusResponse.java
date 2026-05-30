package com.tickets.api_gateway.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Respuesta de GET /api/queue/status/{userId}
 * Contrato: { status, position }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueueStatusResponse {
    private String status;
    private Integer position;
}