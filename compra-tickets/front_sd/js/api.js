/**
 * api.js — Capa centralizada de comunicación con el backend.
 *
 * Este archivo NO sabe nada de Redis.
 * Solo llama al API Gateway.
 *
 * Separación de endpoints:
 *
 * /api/queue/*   -> cola de espera
 * /api/buying/*  -> sesión activa de compra + TTL
 * /api/purchase  -> confirmación de compra
 */

const API = (() => {

  const BASE = 'http://localhost:8080';

  async function request(url, options = {}) {
    try {
      const res = await fetch(url, {
        headers: { 'Content-Type': 'application/json' },
        ...options,
      });

      let data = null;
      const contentType = res.headers.get('content-type') || '';

      if (contentType.includes('application/json')) {
        data = await res.json();
      }

      if (!res.ok) {
        return {
          data,
          error: {
            status: res.status,
            body: data,
          },
        };
      }

      return { data, error: null };

    } catch (err) {
      return {
        data: null,
        error: {
          status: 0,
          body: null,
          message: err.message,
        },
      };
    }
  }

  // ─────────────────────────────────────────────
  // Usuarios
  // ─────────────────────────────────────────────

  async function crearUsuario() {
    return request(`${BASE}/api/usuarios`, {
      method: 'POST',
    });
  }

  // ─────────────────────────────────────────────
  // Cola de espera
  // ─────────────────────────────────────────────

  async function unirseACola(userId) {
    return request(`${BASE}/api/queue/join`, {
      method: 'POST',
      body: JSON.stringify({ userId }),
    });
  }

  /**
   * Estado general desde la cola.
   *
   * Este endpoint puede devolver:
   * WAITING | BUYING | EXPIRED | PURCHASED | NOT_FOUND | REJECTED
   *
   * El gateway internamente primero pregunta a compra-service
   * si el usuario ya está BUYING, y si no, pregunta a queue-service.
   */
  async function obtenerEstado(userId) {
    return request(`${BASE}/api/queue/status/${userId}`);
  }

  async function obtenerStats() {
    return request(`${BASE}/api/queue/stats`);
  }

  // ─────────────────────────────────────────────
  // Compra activa / TTL
  // ─────────────────────────────────────────────

  /**
   * TTL de la ventana de compra activa.
   *
   * Sale de:
   * GET /api/buying/ttl/{userId}
   *
   * Respuesta esperada:
   * { "ttl": 123 }
   *
   * ttl > 0  -> puede seguir comprando
   * ttl = 0  -> venció
   * ttl = -2 -> no hay sesión activa
   */
  async function obtenerTTL(userId) {
    return request(`${BASE}/api/buying/ttl/${userId}`);
  }

  async function obtenerEstadoBuying(userId) {
    return request(`${BASE}/api/buying/status/${userId}`);
  }

  /**
   * Lo llama el front cuando el contador llega a 0.
   */
  async function expirarCompra(userId) {
    return request(`${BASE}/api/buying/expire/${userId}`, {
      method: 'POST',
    });
  }

  // ─────────────────────────────────────────────
  // Confirmación de compra
  // ─────────────────────────────────────────────

  async function confirmarCompra(userId, ticketId) {
    return request(`${BASE}/api/purchase`, {
      method: 'POST',
      body: JSON.stringify({ userId, ticketId }),
    });
  }

  // ─────────────────────────────────────────────
  // Parámetros
  // ─────────────────────────────────────────────

  async function obtenerParametro(key) {
    return request(`${BASE}/api/params/${key}`);
  }

  return {
    crearUsuario,

    unirseACola,
    obtenerEstado,
    obtenerStats,

    obtenerTTL,
    obtenerEstadoBuying,
    expirarCompra,

    confirmarCompra,

    obtenerParametro,
  };

})();