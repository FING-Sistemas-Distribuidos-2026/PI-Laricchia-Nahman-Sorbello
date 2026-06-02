package com.tickets.api_gateway.exception;

import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Decodificador de errores Feign.
 *
 * Convierte las respuestas 4xx / 5xx de los downstream services en
 * DownstreamException, preservando el status code y el body original.
 *
 * Esto evita que un 409 (cola llena) de queue-service aparezca como 500
 * en el frontend.
 */
@Slf4j
@Component
public class FeignErrorDecoder implements ErrorDecoder {

    private final ErrorDecoder defaultDecoder = new Default();

    @Override
    public Exception decode(String methodKey, Response response) {
        String body = extractBody(response);
        int status = response.status();

        log.warn("[FeignErrorDecoder] downstream error | method={} status={} body={}",
                methodKey, status, body);

        // Para 4xx propagamos con el body exacto del downstream
        if (status >= 400 && status < 500) {
            return new DownstreamException(
                    HttpStatus.valueOf(status),
                    body,
                    resolveOrigin(methodKey)
            );
        }

        // Para 5xx también propagamos (el GlobalExceptionHandler decide qué mostrar)
        if (status >= 500) {
            return new DownstreamException(
                    HttpStatus.valueOf(status),
                    body.isBlank() ? "Error interno en servicio downstream" : body,
                    resolveOrigin(methodKey)
            );
        }

        return defaultDecoder.decode(methodKey, response);
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private String extractBody(Response response) {
        if (response.body() == null) return "";
        try (InputStream is = response.body().asInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("No se pudo leer el body del error downstream", e);
            return "";
        }
    }

    /**
     * Extrae el nombre del servicio del methodKey de Feign.
     * Formato: "NombreClient#metodo(Tipo)"
     */
    private String resolveOrigin(String methodKey) {
        if (methodKey == null) return "unknown";
        if (methodKey.contains("QueueClient"))    return "queue-service";
        if (methodKey.contains("PurchaseClient")) return "compra-service";
        return methodKey;
    }
}