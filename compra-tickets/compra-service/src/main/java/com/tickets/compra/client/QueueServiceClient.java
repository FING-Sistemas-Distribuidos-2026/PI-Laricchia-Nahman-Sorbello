package com.tickets.compra.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.util.UUID;

@Slf4j
@Component
public class QueueServiceClient {

    private final RestClient restClient;

    public QueueServiceClient(
            @Value("${queue-service.url:http://localhost:8081}") String queueUrl) {
        this.restClient = RestClient.builder().baseUrl(queueUrl).build();
    }

    public void cleanupRedis(UUID userId) {
        try {
            restClient.delete()
                    .uri("/api/queue/user/{userId}", userId.toString())
                    .retrieve()
                    .toBodilessEntity();
            log.info("[QueueServiceClient] Cleanup OK para {}", userId);
        } catch (Exception e) {
            log.error("[QueueServiceClient] Error limpiando {}: {}",
                    userId, e.getMessage());
        }
    }
}
