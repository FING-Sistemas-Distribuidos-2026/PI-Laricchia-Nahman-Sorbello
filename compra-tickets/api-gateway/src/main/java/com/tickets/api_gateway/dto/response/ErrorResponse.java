package com.tickets.api_gateway.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Cuerpo de error estándar que devuelve el gateway cuando un downstream
 * falla o cuando la validación de entrada es incorrecta.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {
    //Código HTTP del error
    private int status;
    private String message;

    //Servicio que originó el error (útil para debug)
    private String origin;

    //Timestamp del error
    @Builder.Default
    private String timestamp = Instant.now().toString();
}