package com.tickets.scheduler.client.dto;

public record ActivateResponse(
        String userId,
        Long   ticketId,   // número, no string — importante para el JSON en Redis
        String status,
        String message
) {}
