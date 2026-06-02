package com.tickets.api_gateway.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Excepción que encapsula un error proveniente de un servicio downstream.
 * Preserva el HttpStatus original para que el GlobalExceptionHandler
 * lo retransmita al frontend sin enmascararlo.
 */
@Getter
public class DownstreamException extends RuntimeException {

    private final HttpStatus status;
    private final String rawBody;
    private final String origin;

    public DownstreamException(HttpStatus status, String rawBody, String origin) {
        super(String.format("[%s] %d – %s", origin, status.value(), rawBody));
        this.status  = status;
        this.rawBody = rawBody;
        this.origin  = origin;
    }
}