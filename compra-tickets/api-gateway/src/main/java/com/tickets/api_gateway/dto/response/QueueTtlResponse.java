package com.tickets.api_gateway.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**


 Respuesta de GET /api/queue/ttl/{userId}
 Contrato: { ttl }*/

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueueTtlResponse {
    private Long ttl;
}