package com.tickets.api_gateway.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Respuesta de POST /api/queue/join
 * Contrato: { userId, status, position }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JoinQueueResponse {
    private String userId;
    private String status;
    //private Integer position;
}
