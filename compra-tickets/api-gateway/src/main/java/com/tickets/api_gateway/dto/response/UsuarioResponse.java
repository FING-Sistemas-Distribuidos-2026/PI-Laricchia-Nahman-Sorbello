package com.tickets.api_gateway.dto.response;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Respuesta de POST /api/usuarios y GET /api/usuarios/{id}
 * Contrato: { id, creadoEn }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioResponse {
    private String id;
    private String creadoEn;
}