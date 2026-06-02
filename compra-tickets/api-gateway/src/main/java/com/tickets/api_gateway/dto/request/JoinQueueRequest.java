package com.tickets.api_gateway.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Body para POST /api/queue/join
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JoinQueueRequest {

    @NotBlank(message = "userId es obligatorio")
    private String userId;
}
