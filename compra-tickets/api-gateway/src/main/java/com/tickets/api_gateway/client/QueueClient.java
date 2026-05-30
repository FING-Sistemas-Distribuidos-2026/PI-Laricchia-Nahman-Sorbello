package com.tickets.api_gateway.client;
import com.tickets.api_gateway.dto.response.JoinQueueResponse;
import com.tickets.api_gateway.dto.response.QueueStatusResponse;
import com.tickets.api_gateway.dto.request.JoinQueueRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Cliente Feign hacia queue-service (puerto 8081).
 *
 * La URL se inyecta desde application.properties → queue.service.url
 * Esto permite cambiar entre local (localhost:8081) y Docker (queue-service:8081)
 * sin tocar código.
 */
@FeignClient(
        name  = "queue-service",
        url   = "${queue.service.url}"
)
public interface QueueClient {

    /**
     * Encola al usuario.
     * POST queue-service/api/queue/join
     */
    @PostMapping("/api/queue/join")
    JoinQueueResponse join(@RequestBody JoinQueueRequest request);

    /**
     * Consulta el estado del usuario en la cola.
     * GET queue-service/api/queue/status/{userId}
     */
    @GetMapping("/api/queue/status/{userId}")
    QueueStatusResponse getStatus(@PathVariable("userId") String userId);

    /*
    *//**
     * Consulta el TTL del slot del usuario.
     * GET queue-service/api/queue/ttl/{userId}
     *//*
    @GetMapping("/api/queue/ttl/{userId}")
    QueueTtlResponse getTtl(@PathVariable("userId") String userId);
    */
}