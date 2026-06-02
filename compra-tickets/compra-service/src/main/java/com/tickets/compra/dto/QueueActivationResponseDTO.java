package com.tickets.compra.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueueActivationResponseDTO {
    private UUID userId;
    private Long ticketId;
    private String status;
    private String message;
}