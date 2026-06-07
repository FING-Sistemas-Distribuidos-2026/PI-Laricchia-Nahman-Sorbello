package com.tickets.api_gateway.client;

import com.tickets.api_gateway.dto.request.PurchaseRequest;
import com.tickets.api_gateway.dto.response.ExpireResponse;
import com.tickets.api_gateway.dto.response.PurchaseResponse;
import com.tickets.api_gateway.dto.response.QueueActivationResponse;
import com.tickets.api_gateway.dto.response.QueueStatusResponse;
import com.tickets.api_gateway.dto.response.QueueTtlResponse;
import com.tickets.api_gateway.dto.response.SystemParameterResponse;
import com.tickets.api_gateway.dto.response.UsuarioResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@FeignClient(
        name = "compra-service",
        url = "${purchase.service.url}"
)
public interface PurchaseClient {

    @PostMapping("/api/purchase")
    PurchaseResponse purchase(@RequestBody PurchaseRequest request);

    @PostMapping("/api/purchase/expire/{userId}")
    ExpireResponse expire(@PathVariable("userId") String userId);

    @GetMapping("/api/purchase/session/{userId}")
    QueueStatusResponse getBuyingSession(@PathVariable("userId") String userId);

    @GetMapping("/api/purchase/ttl/{userId}")
    QueueTtlResponse getBuyingTtl(@PathVariable("userId") String userId);

    @PostMapping("/api/queue/activate/{userId}")
    QueueActivationResponse activate(@PathVariable("userId") UUID userId);

    @GetMapping("/api/params/{key}")
    SystemParameterResponse getParameter(@PathVariable("key") String key);

    @PostMapping("/api/usuarios")
    UsuarioResponse createUsuario();

    @GetMapping("/api/usuarios/{id}")
    UsuarioResponse getUsuario(@PathVariable("id") String id);
}