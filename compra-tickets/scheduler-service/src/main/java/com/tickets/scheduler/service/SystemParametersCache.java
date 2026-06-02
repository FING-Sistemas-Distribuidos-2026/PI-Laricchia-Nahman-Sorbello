package com.tickets.scheduler.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Caché local de parámetros del sistema.
 * Los lee desde compra-service cada 30 segundos.
 *
 * Por qué cachear y no leer en cada ciclo:
 * El scheduler corre cada 5s. Sin caché haría 2 HTTP calls extras
 * por ciclo solo para configuración = 24 calls/minuto innecesarios.
 * Con caché de 30s: máximo 2 calls/minuto, y si Aida está caída
 * el scheduler sigue funcionando con los últimos valores conocidos.
 */
@Slf4j
@Component
public class SystemParametersCache {

    private final RestClient restClient;

    // volatile: garantiza visibilidad entre hilos (el scheduler corre en hilo propio)
    // Valores iniciales = defaults seguros mientras compra-service no responde
    private volatile long maxConcurrentBuyers = 10L;
    private volatile long purchaseTtlSeconds  = 600L;

    public SystemParametersCache(
            @Value("${compra-service.url:http://localhost:8083}") String compraUrl) {
        this.restClient = RestClient.builder().baseUrl(compraUrl).build();
    }

    // @PostConstruct: carga inicial al arrancar el servicio
    // Sin esto, los primeros 30s el scheduler usaría solo los defaults
    @PostConstruct
    public void init() {
        refresh();
    }

    @Scheduled(fixedDelay = 30_000)
    public void refresh() {
        this.maxConcurrentBuyers = fetch("MAX_CONCURRENT_BUYERS", maxConcurrentBuyers);
        this.purchaseTtlSeconds  = fetch("PURCHASE_TTL_SECONDS",  purchaseTtlSeconds);
        log.debug("[ParamsCache] maxBuyers={} ttl={}s", maxConcurrentBuyers, purchaseTtlSeconds);
    }

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
                long valor = Long.parseLong(body.get("value").toString());
                log.debug("[ParamsCache] {} = {}", key, valor);
                return valor;
            }
        } catch (Exception e) {
            // Fail-open: si Aida no responde, mantenemos el último valor conocido
            // El scheduler sigue funcionando — no es correcto parar todo por un parámetro
            log.warn("[ParamsCache] No se pudo leer '{}': {}. Usando: {}", key, e.getMessage(), fallback);
        }
        return fallback;
    }
}