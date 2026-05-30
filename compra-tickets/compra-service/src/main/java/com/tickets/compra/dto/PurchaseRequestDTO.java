package com.tickets.compra.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class PurchaseRequestDTO {

    @NotNull(message = "userId es obligatorio")
    private UUID userId;

    @NotNull(message = "ticketId es obligatorio")
    private Long ticketId;
}