package com.tickets.scheduler.client;

import com.tickets.scheduler.client.dto.ActivateResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Slf4j
@Component
public class CompraClient {

    public enum ActivateResult {
        SUCCESS, NO_TICKETS, USER_NOT_FOUND, USER_NOT_WAITING, NETWORK_ERROR
    }

    public record ActivateOutcome(ActivateResult result, Long ticketId) {}

    private final RestClient restClient;

    public CompraClient(@Value("${compra-service.url:http://localhost:8083}") String compraUrl) {
        this.restClient = RestClient.builder().baseUrl(compraUrl).build();
    }

    /**
     * Activa al usuario en compra-service.
     * userId ahora es UUID (cambio coordinado con queue-service y compra-service).
     */
    public ActivateOutcome activateUser(UUID userId) {
        try {
            ActivateResponse response = restClient.post()
                    .uri("/api/queue/activate/{userId}", userId.toString())
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        throw new ClientErrorException(res.getStatusCode().value(), userId);
                    })
                    .body(ActivateResponse.class);

            if (response == null || response.ticketId() == null) {
                log.error("[CompraClient] activate/{} devolvió response vacío", userId);
                return new ActivateOutcome(ActivateResult.NETWORK_ERROR, null);
            }

            log.info("[CompraClient] activate/{} OK. TicketId: {}", userId, response.ticketId());
            return new ActivateOutcome(ActivateResult.SUCCESS, response.ticketId());

        } catch (ClientErrorException e) {
            return manejarErrorCliente(e, userId);
        } catch (Exception e) {
            log.error("[CompraClient] Error de red activate/{}: {}", userId, e.getMessage());
            return new ActivateOutcome(ActivateResult.NETWORK_ERROR, null);
        }
    }

    public boolean expireUser(UUID userId) {
        try {
            restClient.post()
                    .uri("/api/purchase/expire/{userId}", userId.toString())
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        log.warn("[CompraClient] expire/{} → {}", userId, res.getStatusCode());
                        throw new RuntimeException("Error expirando " + userId);
                    })
                    .toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.error("[CompraClient] Error expire/{}: {}", userId, e.getMessage());
            return false;
        }
    }

    private ActivateOutcome manejarErrorCliente(ClientErrorException e, UUID userId) {
        if (e.statusCode == 404) {
            log.warn("[CompraClient] activate/{} → 404. Usuario inexistente.", userId);
            return new ActivateOutcome(ActivateResult.USER_NOT_FOUND, null);
        }
        if (e.statusCode == 409) {
            log.warn("[CompraClient] activate/{} → 409. Sin tickets o estado incorrecto.", userId);
            return new ActivateOutcome(ActivateResult.NO_TICKETS, null);
        }
        log.error("[CompraClient] activate/{} → {}", userId, e.statusCode);
        return new ActivateOutcome(ActivateResult.NETWORK_ERROR, null);
    }

    private static class ClientErrorException extends RuntimeException {
        final int statusCode;
        ClientErrorException(int statusCode, UUID userId) {
            super("Error " + statusCode + " para " + userId);
            this.statusCode = statusCode;
        }
    }
}