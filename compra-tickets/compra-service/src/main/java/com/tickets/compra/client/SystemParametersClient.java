package com.tickets.compra.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class SystemParametersClient {
    private final RestClient restClient;

    public SystemParametersClient(@Value("${queue-service.url:http://localhost:8081}") String queueUrl) {
        this.restClient = RestClient.builder().baseUrl(queueUrl).build();
    }

    public String get(String key) {
        Map<String, Object> body = restClient.get()
                .uri("/api/params/{key}", key)
                .retrieve()
                .body(Map.class);
        return body != null ? body.get("value").toString() : null;
    }
}
