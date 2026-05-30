package com.tickets.api_gateway.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tickets.api_gateway.dto.response.ErrorResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Manejador global de excepciones del API Gateway.
 *
 * Reglas:
 *  - DownstreamException  → propaga el status code exacto del downstream (no enmascara 409 como 500).
 *  - Validation errors    → 400 con detalle de campos inválidos.
 *  - Cualquier otra cosa  → 502 Bad Gateway (el upstream tuvo un problema inesperado).
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final ObjectMapper objectMapper;

    // ── Errores de downstream (4xx / 5xx propagados desde Feign) ──────────

    @ExceptionHandler(DownstreamException.class)
    public ResponseEntity<Object> handleDownstream(DownstreamException ex) {
        log.warn("Error de downstream [{}] status={} body={}",
                ex.getOrigin(), ex.getStatus().value(), ex.getRawBody());

        // Intentar retransmitir el body raw del downstream tal cual
        // (el frontend ya conoce el contrato de error de queue-service y compra-service).
        Object responseBody = tryParseRawBody(ex.getRawBody(), ex);

        return ResponseEntity
                .status(ex.getStatus())
                .body(responseBody);
    }

    // ── Errores de validación de entrada (@Valid) ─────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));

        log.warn("Validación fallida: {}", details);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.builder()
                        .status(400)
                        .message("Datos de entrada inválidos: " + details)
                        .origin("api-gateway")
                        .build());
    }

    // ── Cualquier otra excepción no controlada ───────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        log.error("Error inesperado en api-gateway", ex);

        return ResponseEntity
                .status(HttpStatus.BAD_GATEWAY)
                .body(ErrorResponse.builder()
                        .status(502)
                        .message("Error de comunicación con el servicio interno")
                        .origin("api-gateway")
                        .build());
    }

    // ── Helper ───────────────────────────────────────────────────────────

    /**
     * Si el rawBody del downstream es JSON válido, lo retransmite tal cual.
     * Si no, construye un ErrorResponse con el texto.
     */
    private Object tryParseRawBody(String rawBody, DownstreamException ex) {
        if (rawBody == null || rawBody.isBlank()) {
            return ErrorResponse.builder()
                    .status(ex.getStatus().value())
                    .message(ex.getStatus().getReasonPhrase())
                    .origin(ex.getOrigin())
                    .build();
        }
        try {
            // Retransmitir el JSON del downstream sin tocarlo
            return objectMapper.readValue(rawBody, Object.class);
        } catch (Exception parseEx) {
            return ErrorResponse.builder()
                    .status(ex.getStatus().value())
                    .message(rawBody)
                    .origin(ex.getOrigin())
                    .build();
        }
    }
}