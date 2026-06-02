package com.tickets.queue.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Slf4j
@Component
public class SystemParametersCache {

    private final RestClient restClient;

    //defaults por si lo de la aidis esta caida, el sistema sigue funcionando con estos valores
    private volatile long maxWaitingQueue     = 50L;
    private volatile long maxConcurrentBuyers = 5L;
    private volatile long purchaseTtlSeconds  = 300L;

    public SystemParametersCache(@Value("${compra-service.url:http://localhost:8083}") String compraUrl) {
        this.restClient = RestClient.builder().baseUrl(compraUrl).build();
    }

    @PostConstruct
    public void init() {
        refresh();
    }

    @Scheduled(fixedDelay = 30_000)
    public void refresh() {
        this.maxWaitingQueue     = fetch("MAX_WAITING_QUEUE",     maxWaitingQueue);
        this.maxConcurrentBuyers = fetch("MAX_CONCURRENT_BUYERS", maxConcurrentBuyers);
        this.purchaseTtlSeconds  = fetch("PURCHASE_TTL_SECONDS",  purchaseTtlSeconds);
    }

    public long getMaxWaitingQueue()     { return maxWaitingQueue; }
    public long getMaxConcurrentBuyers() { return maxConcurrentBuyers; }
    public long getPurchaseTtlSeconds()  { return purchaseTtlSeconds; }

    @SuppressWarnings("unchecked")
    private long fetch(String key, long fallback) {
        try {
            Map<String, Object> body = restClient.get()
                    .uri("/api/params/{key}", key)
                    .retrieve()
                    .body(Map.class);
            if (body != null && body.get("value") != null) {
                long value = Long.parseLong(body.get("value").toString());
                log.debug("[ParamsCache] {} = {}", key, value);
                return value;
            }
        } catch (Exception e) {
            log.warn("[ParamsCache] No se pudo leer '{}' de compra-service: {}. Uso valor actual: {}",
                    key, e.getMessage(), fallback);
        }
        return fallback;
    }
}