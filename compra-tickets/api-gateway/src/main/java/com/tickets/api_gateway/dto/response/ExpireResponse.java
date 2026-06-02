package com.tickets.api_gateway.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Respuesta de POST /api/purchase/expire/{userId}
 * Contrato: { status: "EXPIRED" }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpireResponse {
    private String status;
}