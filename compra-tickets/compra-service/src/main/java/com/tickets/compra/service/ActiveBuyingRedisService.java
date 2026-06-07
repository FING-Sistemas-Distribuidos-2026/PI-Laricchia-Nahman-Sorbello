package com.tickets.compra.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class ActiveBuyingRedisService {

    /**
     * HASH principal de usuarios que ya están comprando.
     *
     * Estructura:
     *
     * HSET buying:sessions {userId} {sessionJson}
     *
     * No es cola.
     * No comparte nada con waiting_queue.
     */
    public static final String BUYING_HASH_KEY = "buying:sessions";

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public ActiveBuyingRedisService(
            RedisTemplate<String, String> redisTemplate,
            ObjectMapper objectMapper
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void putSession(UUID userId, Long ticketId, long ttlSeconds) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(ttlSeconds);

        ActiveBuyingSession session = new ActiveBuyingSession(
                userId.toString(),
                ticketId,
                "BUYING",
                now,
                expiresAt,
                false
        );

        redisTemplate.opsForHash().put(
                BUYING_HASH_KEY,
                userId.toString(),
                toJson(session)
        );

        log.info(
                "[BuyingHash] HSET {} {} ticketId={} ttl={}s expiresAt={}",
                BUYING_HASH_KEY,
                userId,
                ticketId,
                ttlSeconds,
                expiresAt
        );
    }

    public Optional<ActiveBuyingSession> findSession(UUID userId) {
        Object raw = redisTemplate.opsForHash().get(
                BUYING_HASH_KEY,
                userId.toString()
        );

        if (raw == null) {
            return Optional.empty();
        }

        return Optional.of(fromJson(raw.toString()));
    }

    /**
     * Lee el HASH completo.
     *
     * Esto se usa solamente para persistir snapshot cada 30 segundos.
     * No se usa para decidir expiraciones.
     */
    public List<ActiveBuyingSession> findAllSessions() {
        Map<Object, Object> entries = redisTemplate.opsForHash()
                .entries(BUYING_HASH_KEY);

        List<ActiveBuyingSession> sessions = new ArrayList<>();

        for (Object value : entries.values()) {
            try {
                sessions.add(fromJson(value.toString()));
            } catch (Exception e) {
                log.warn("[BuyingHash] Sesión inválida ignorada: {}", value);
            }
        }

        return sessions;
    }

    public void removeSession(UUID userId) {
        redisTemplate.opsForHash().delete(
                BUYING_HASH_KEY,
                userId.toString()
        );

        log.info("[BuyingHash] HDEL {} {}", BUYING_HASH_KEY, userId);
    }

    public long countSessions() {
        Long size = redisTemplate.opsForHash().size(BUYING_HASH_KEY);
        return size != null ? size : 0L;
    }

    public boolean isExpired(ActiveBuyingSession session) {
        return !session.expiresAt().isAfter(Instant.now());
    }

    public long ttlRemainingSeconds(UUID userId) {
        return findSession(userId)
                .map(this::ttlRemainingSeconds)
                .orElse(-2L);
    }

    public long ttlRemainingSeconds(ActiveBuyingSession session) {
        long remaining = session.expiresAt().getEpochSecond()
                - Instant.now().getEpochSecond();

        return Math.max(0L, remaining);
    }

    public void markPersisted(UUID userId) {
        findSession(userId).ifPresent(session -> {
            ActiveBuyingSession updated = new ActiveBuyingSession(
                    session.userId(),
                    session.ticketId(),
                    session.status(),
                    session.activatedAt(),
                    session.expiresAt(),
                    true
            );

            redisTemplate.opsForHash().put(
                    BUYING_HASH_KEY,
                    userId.toString(),
                    toJson(updated)
            );
        });
    }

    private String toJson(ActiveBuyingSession session) {
        try {
            return objectMapper.writeValueAsString(session);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "No se pudo serializar sesión BUYING",
                    e
            );
        }
    }

    private ActiveBuyingSession fromJson(String json) {
        try {
            return objectMapper.readValue(json, ActiveBuyingSession.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "No se pudo leer sesión BUYING: " + json,
                    e
            );
        }
    }

    public record ActiveBuyingSession(
            String userId,
            Long ticketId,
            String status,
            Instant activatedAt,
            Instant expiresAt,
            boolean persisted
    ) {
    }
}