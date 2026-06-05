package com.tickets.queue.service;

import com.tickets.queue.repository.SystemParameterRepository;
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
    private final SystemParameterRepository repo;

    private volatile long maxWaitingQueue     = 50L;
    private volatile long maxConcurrentBuyers = 5L;
    private volatile long purchaseTtlSeconds  = 300L;

    public SystemParametersCache(SystemParameterRepository repo) {
        this.repo = repo;
    }

    @PostConstruct
    public void init() { refresh(); }

    @Scheduled(fixedDelay = 30_000)
    public void refresh() {
        this.maxWaitingQueue     = fetch("MAX_WAITING_QUEUE",     maxWaitingQueue);
        this.maxConcurrentBuyers = fetch("MAX_CONCURRENT_BUYERS", maxConcurrentBuyers);
        this.purchaseTtlSeconds  = fetch("PURCHASE_TTL_SECONDS",  purchaseTtlSeconds);
    }

    public long getMaxWaitingQueue()     { return maxWaitingQueue; }
    public long getMaxConcurrentBuyers() { return maxConcurrentBuyers; }
    public long getPurchaseTtlSeconds()  { return purchaseTtlSeconds; }

    private long fetch(String key, long fallback) {
        try {
            return repo.findById(key)
                    .map(p -> Long.parseLong(p.getValue()))
                    .orElse(fallback);
        } catch (Exception e) {
            log.warn("[ParamsCache] No se pudo leer '{}': {}. Uso: {}", key, e.getMessage(), fallback);
            return fallback;
        }
    }
}