/**
 * api.js — Capa centralizada de comunicación con el backend.
 *
 * Todos los endpoints están agrupados por microservicio.
 * Cada función devuelve una Promise con { data, error }.
 * Nunca tira excepciones hacia afuera: los errores se manejan acá.
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
        return { data, error: { status: res.status, body: data } };
      }

      return { data, error: null };
    } catch (err) {
      return { data: null, error: { status: 0, body: null, message: err.message } };
    }
  }

  async function crearUsuario() {
    return request(`${BASE}/api/usuarios`, { method: 'POST' });
  }

  async function confirmarCompra(userId, ticketId) {
    return request(`${BASE}/api/purchase`, {
      method: 'POST',
      body: JSON.stringify({ userId, ticketId }),
    });
  }

  async function expirarCompra(userId) {
    return request(`${BASE}/api/purchase/expire/${userId}`, { method: 'POST' });
  }

  async function obtenerParametro(key) {
    return request(`${BASE}/api/params/${key}`);
  }

  async function unirseACola(userId) {
    return request(`${BASE}/api/queue/join`, {
      method: 'POST',
      body: JSON.stringify({ userId }),
    });
  }

  async function obtenerEstado(userId) {
    return request(`${BASE}/api/queue/status/${userId}`);
  }

  async function obtenerTTL(userId) {
    return request(`${BASE}/api/queue/ttl/${userId}`);
  }

  async function obtenerStats() {
    return request(`${BASE}/api/queue/stats`);
  }

  return {
    crearUsuario,
    confirmarCompra,
    expirarCompra,
    obtenerParametro,
    unirseACola,
    obtenerEstado,
    obtenerTTL,
    obtenerStats,
  };

})();