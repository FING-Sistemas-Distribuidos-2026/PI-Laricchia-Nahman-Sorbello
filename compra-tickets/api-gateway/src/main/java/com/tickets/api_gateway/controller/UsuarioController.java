package com.tickets.api_gateway.controller;

import com.tickets.api_gateway.client.PurchaseClient;
import com.tickets.api_gateway.dto.response.UsuarioResponse;
import com.tickets.api_gateway.dto.request.CreateUsuarioRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador público de usuarios.
 *
 * Los usuarios se gestionan en compra-service (Aida); el gateway
 * simplemente enruta sin modificar el contrato.
 */
@Slf4j
@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final PurchaseClient purchaseClient;

    // ── POST /api/usuarios ────────────────────────────────────────────────

    /**
     * Crea un nuevo usuario.
     *
     * @return { id, creadoEn } con HTTP 201 Created
     */
    @PostMapping
    public ResponseEntity<UsuarioResponse> create(
            @RequestBody(required = false) CreateUsuarioRequest request) {

        log.debug("CREATE usuario");
        // Si el frontend no manda body, usamos un objeto vacío
        CreateUsuarioRequest body = request != null ? request : new CreateUsuarioRequest();
        UsuarioResponse response = purchaseClient.createUsuario();
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ── GET /api/usuarios/{id} ────────────────────────────────────────────

    /**
     * Obtiene un usuario por su id.
     *
     * @param id identificador del usuario
     * @return { id, creadoEn }
     */
    @GetMapping("/{id}")
    public ResponseEntity<UsuarioResponse> getById(
            @PathVariable String id) {

        log.debug("GET usuario | id={}", id);
        UsuarioResponse response = purchaseClient.getUsuario(id);
        return ResponseEntity.ok(response);
    }
}