package com.tickets.api_gateway.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Body para POST /api/purchase
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseRequest {

    @NotBlank(message = "userId es obligatorio")
    private String userId;

    @NotBlank(message = "ticketId es obligatorio")
    private String ticketId;
}