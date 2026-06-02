package com.tickets.scheduler.client;

import com.tickets.scheduler.client.dto.ActivateResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Encapsula toda comunicación HTTP con compra-service.
 * SchedulerService nunca maneja HTTP directamente — delega acá.
 *
 * Por qué RestClient y no OpenFeign:
 * El scheduler no expone endpoints propios ni necesita el contrato
 * declarativo de Feign. RestClient es suficiente y más simple para
 * un cliente que solo hace llamadas salientes.
 * Si el equipo decide estandarizar en Feign, el cambio es solo acá.
 */
@Slf4j
@Component
public class CompraClient {

    // Resultado posible de intentar activar un usuario
    public enum ActivateResult {
        SUCCESS,          // ticket reservado, todo bien
        NO_TICKETS,       // 409 sin tickets disponibles → detener ciclo completo
        USER_NOT_FOUND,   // 404 → usuario no existe en BD, descartar y continuar
        USER_NOT_WAITING, // 409 usuario en estado incorrecto → descartar y continuar
        NETWORK_ERROR     // error de red → devolver usuario a la cola
    }

    public record ActivateOutcome(ActivateResult result, Long ticketId) {}

    private final RestClient restClient;

    public CompraClient(@Value("${compra-service.url:http://localhost:8083}") String compraUrl) {
        this.restClient = RestClient.builder().baseUrl(compraUrl).build();
    }

    /**
     * Llama a POST /api/queue/activate/{userId}.
     * Aida reserva el ticket con lock pesimista (FOR UPDATE SKIP LOCKED).
     * Devuelve un ActivateOutcome con el resultado y el ticketId si tuvo éxito.
     *
     * Casos de error y por qué los manejamos diferente:
     * - 404: el userId no existe en la BD de Aida (usuario fantasma en Redis).
     *        No tiene sentido devolverlo a la cola. Descartamos.
     * - 409 sin tickets: no hay tickets disponibles para nadie.
     *        Detener el ciclo completo — no tiene sentido activar más usuarios.
     * - 409 estado incorrecto: ese usuario específico no está en WAITING.
     *        Puede ser un caso borde. Descartamos y continuamos con el siguiente.
     * - Error de red: Aida está caída o hay timeout.
     *        Devolvemos al usuario al frente de la cola para el próximo ciclo.
     */
    public ActivateOutcome activateUser(String userId) {
        try {
            ActivateResponse response = restClient.post()
                    .uri("/api/queue/activate/{userId}", userId)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        int code = res.getStatusCode().value();
                        // Usamos una excepción con el código para distinguir casos en el catch
                        throw new ClientErrorException(code, userId);
                    })
                    .body(ActivateResponse.class);

            if (response == null || response.ticketId() == null) {
                log.error("[CompraClient] activate/{} devolvió response vacío", userId);
                return new ActivateOutcome(ActivateResult.NETWORK_ERROR, null);
            }

            log.info("[CompraClient] activate/{} exitoso. TicketId: {}", userId, response.ticketId());
            return new ActivateOutcome(ActivateResult.SUCCESS, response.ticketId());

        } catch (ClientErrorException e) {
            return manejarErrorCliente(e, userId);
        } catch (Exception e) {
            log.error("[CompraClient] Error de red en activate/{}: {}", userId, e.getMessage());
            return new ActivateOutcome(ActivateResult.NETWORK_ERROR, null);
        }
    }

    /**
     * Llama a POST /api/purchase/expire/{userId}.
     * Aida libera el ticket en PostgreSQL y marca al usuario como EXPIRED.
     * Es idempotente: se puede llamar más de una vez sin romper nada.
     *
     * CRÍTICO: este método se llama ANTES de limpiar Redis.
     * Si se invierte el orden y Redis se limpia primero, el ticket
     * queda RESERVED para siempre en PostgreSQL.
     */
    public boolean expireUser(String userId) {
        try {
            restClient.post()
                    .uri("/api/purchase/expire/{userId}", userId)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        log.warn("[CompraClient] expire/{} devolvió {}", userId, res.getStatusCode());
                        throw new RuntimeException("Error expirando " + userId);
                    })
                    .toBodilessEntity();
            log.info("[CompraClient] expire/{} exitoso", userId);
            return true;
        } catch (Exception e) {
            log.error("[CompraClient] Error en expire/{}: {}", userId, e.getMessage());
            return false;
        }
    }

    private ActivateOutcome manejarErrorCliente(ClientErrorException e, String userId) {
        if (e.statusCode == 404) {
            log.warn("[CompraClient] activate/{} → 404. Usuario no existe en BD. Descartando.", userId);
            return new ActivateOutcome(ActivateResult.USER_NOT_FOUND, null);
        }
        if (e.statusCode == 409) {
            // Distinguir "sin tickets" de "estado incorrecto" requiere leer el body.
            // Por ahora tratamos todo 409 como NO_TICKETS para ser conservadores
            // (detener el ciclo es más seguro que continuar sin tickets).
            // TODO: pedirle a Aida que diferencie con un campo "reason" en el response.
            log.warn("[CompraClient] activate/{} → 409. Sin tickets o estado incorrecto.", userId);
            return new ActivateOutcome(ActivateResult.NO_TICKETS, null);
        }
        log.error("[CompraClient] activate/{} → {}. Error inesperado.", userId, e.statusCode);
        return new ActivateOutcome(ActivateResult.NETWORK_ERROR, null);
    }

    // Excepción interna para pasar el status code al catch
    private static class ClientErrorException extends RuntimeException {
        final int statusCode;
        ClientErrorException(int statusCode, String userId) {
            super("Error " + statusCode + " para usuario " + userId);
            this.statusCode = statusCode;
        }
    }
}