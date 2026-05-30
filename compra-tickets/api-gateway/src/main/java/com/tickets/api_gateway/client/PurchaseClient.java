package com.tickets.api_gateway.client;

import com.tickets.api_gateway.dto.response.ExpireResponse;
import com.tickets.api_gateway.dto.response.PurchaseResponse;
import com.tickets.api_gateway.dto.response.SystemParameterResponse;
import com.tickets.api_gateway.dto.response.UsuarioResponse;
import com.tickets.api_gateway.dto.request.PurchaseRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Cliente Feign hacia compra-service (puerto 8083).
 *
 * La URL se inyecta desde application.properties → purchase.service.url
 */
@FeignClient(
        name = "compra-service",
        url  = "${purchase.service.url}"
)
public interface PurchaseClient {

    /**
     * Ejecuta la compra de una entrada.
     * POST compra-service/api/purchase
     */
    @PostMapping("/api/purchase")
    PurchaseResponse purchase(@RequestBody PurchaseRequest request);

    @GetMapping("/api/params/{key}")
    SystemParameterResponse getParameter(@PathVariable("key") String key);

    /**
     * Expira el slot de compra de un usuario.
     * POST compra-service/api/purchase/expire/{userId}
     */
    @PostMapping("/api/purchase/expire/{userId}")
    ExpireResponse expire(@PathVariable("userId") String userId);

    @PostMapping("/api/usuarios")
    UsuarioResponse createUsuario();

    /*
    *//**
     * Crea un nuevo usuario.
     * POST compra-service/api/usuarios
     *//*
    @PostMapping("/api/usuarios")
    UsuarioResponse createUsuario(@RequestBody CreateUsuarioRequest request);

*/
    /**
     * Obtiene un usuario por id.
     * GET compra-service/api/usuarios/{id}
     */
    @GetMapping("/api/usuarios/{id}")
    UsuarioResponse getUsuario(@PathVariable("id") String id);
}