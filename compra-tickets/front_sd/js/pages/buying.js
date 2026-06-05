/**
 * buying.js — Pantalla "¡Es tu turno!".
 *
 * Responsabilidades:
 *  1. Mostrar cuenta regresiva (TTL) con anillo SVG animado
 *  2. Polling del TTL real desde el backend cada 5s (fuente de verdad)
 *  3. Countdown local en JS para animación fluida entre polls
 *  4. Al confirmar: POST /api/purchase → navegar a /success
 *  5. Al llegar a 0: POST /api/purchase/expire → navegar a /expired
 *  6. Retornar cleanup para detener timers al salir
 */

const BuyingPage = (() => {

  const TTL_TOTAL = 600; // segundos (10 minutos) — se sincroniza con el backend
  const URGENT_THRESHOLD = 60; // segundos para mostrar alerta

  // Estado interno de la página
  let ttlActual     = TTL_TOTAL;
  let countdownId   = null;
  let expiradoYa    = false;

  function render(container, Session) {
    const { userId, ticketId } = Session.get();

    if (!userId) {
      Router.navigate('/');
      return;
    }

    // Resetear estado interno
    ttlActual   = TTL_TOTAL;
    expiradoYa  = false;

    container.innerHTML = `
      <div class="page" id="buying-page">

        <header class="page-header">
          <span class="eyebrow">¡Es tu turno!</span>
          <h1>Confirmá tu compra</h1>
          <p>
            Tenés tiempo limitado para completar la compra.
            Si el tiempo se agota, volvés al final de la fila.
          </p>
        </header>

        <div class="card" style="align-items: center;">
          <div class="countdown-ring">
            <svg width="120" height="120" viewBox="0 0 120 120" aria-hidden="true">
              <circle class="ring-bg"   cx="60" cy="60" r="52"/>
              <circle class="ring-fill" cx="60" cy="60" r="52"
                id="ring-fill"
                stroke-dasharray="326.7"
                stroke-dashoffset="0"/>
            </svg>
            <span class="countdown-time" id="countdown-time" aria-live="polite" aria-label="Tiempo restante">10:00</span>
            <span class="countdown-label">tiempo restante</span>
          </div>

          <div id="urgency-banner" style="display:none; width:100%; margin-top: 0.5rem;">
            <div class="error-banner" role="alert">
              <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true">
                <path d="M8 2L14.5 13H1.5L8 2Z"/>
                <line x1="8" y1="7" x2="8" y2="9.5"/>
                <circle cx="8" cy="11.5" r="0.5" fill="currentColor"/>
              </svg>
              ¡Menos de un minuto! Confirmá ahora o perdés tu lugar.
            </div>
          </div>
        </div>

        <div class="card">
          <div class="card-row">
            <span class="label">Estado</span>
            <span class="badge badge-buying">
              <span class="badge-dot"></span>
              Turno activo
            </span>
          </div>
          <div class="card-row">
            <span class="label">N.° de ticket</span>
            <span class="value" id="ticket-id-display">${ticketId ?? '—'}</span>
          </div>
        </div>

        <div style="display: flex; flex-direction: column; gap: 0.75rem;">
          <button id="btn-confirmar" class="btn btn-primary">
            Confirmar compra
          </button>
          <p class="help-text">
            Al confirmar se descuenta el ticket de la disponibilidad.
          </p>
        </div>

        <div id="error-container"></div>

      </div>
    `;

    // Sincronizar TTL desde el backend antes de arrancar el countdown
    sincronizarTTL(userId).then(() => {
      iniciarCountdown(userId);
    });

    // Polling liviano del TTL cada 5s para mantener sincronía
    const pollTTL = Polling.create({
      fn:        () => API.obtenerTTL(userId),
      interval:  5000,
      immediate: false,
      onSuccess: ({ ttl }) => {
        if (ttl >= 0) ajustarTTL(ttl);
      },
    });
    pollTTL.start();

    // Polling del estado para detectar cambios externos (ej: expiración por el backend)
    const pollEstado = Polling.create({
      fn:        () => API.obtenerEstado(userId),
      interval:  3000,
      immediate: false,
      onSuccess: (data) => {
        if (data.status === 'EXPIRED')   { cleanup(); Router.navigate('/expired'); }
        if (data.status === 'PURCHASED') { cleanup(); Router.navigate('/success'); }
      },
    });
    pollEstado.start();

    document.getElementById('btn-confirmar')
      .addEventListener('click', () => handleConfirmar(userId, ticketId));

    function cleanup() {
      pollTTL.stop();
      pollEstado.stop();
      if (countdownId) { clearInterval(countdownId); countdownId = null; }
    }

    return cleanup;
  }

  // ─── Countdown local ─────────────────────────────────────────

  function iniciarCountdown(userId) {
    if (countdownId) clearInterval(countdownId);

    countdownId = setInterval(async () => {
      ttlActual = Math.max(0, ttlActual - 1);
      renderCountdown(ttlActual);

      if (ttlActual <= 0 && !expiradoYa) {
        expiradoYa = true;
        clearInterval(countdownId);
        await handleExpiracion(userId);
      }
    }, 1000);
  }

  async function sincronizarTTL(userId) {
    const { data } = await API.obtenerTTL(userId);
    if (data && data.ttl >= 0) {
      ttlActual = data.ttl;
      renderCountdown(ttlActual);
    }
  }

  function ajustarTTL(ttlReal) {
    // Solo ajustar si la diferencia es > 2s para evitar saltos visuales bruscos
    if (Math.abs(ttlActual - ttlReal) > 2) {
      ttlActual = ttlReal;
      renderCountdown(ttlActual);
    }
  }

  // ─── Render del reloj y anillo ───────────────────────────────

  function renderCountdown(segundos) {
    const timeEl   = document.getElementById('countdown-time');
    const ringEl   = document.getElementById('ring-fill');
    const urgEl    = document.getElementById('urgency-banner');

    if (!timeEl) return;

    const mins = Math.floor(segundos / 60);
    const secs = segundos % 60;
    timeEl.textContent = `${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;

    // Anillo SVG: circunferencia = 2π × 52 ≈ 326.7
    const CIRCUNFERENCIA = 326.7;
    const progreso = segundos / TTL_TOTAL;
    const offset   = CIRCUNFERENCIA * (1 - progreso);

    if (ringEl) {
      ringEl.style.strokeDashoffset = offset.toFixed(2);
      ringEl.classList.toggle('urgent', segundos <= URGENT_THRESHOLD);
    }

    // Banner de urgencia
    if (urgEl) {
      urgEl.style.display = segundos <= URGENT_THRESHOLD ? 'block' : 'none';
    }
  }

  // ─── Acciones ────────────────────────────────────────────────

  async function handleConfirmar(userId, ticketId) {
    const btn = document.getElementById('btn-confirmar');
    const errorContainer = document.getElementById('error-container');

    if (!ticketId) {
      mostrarError(errorContainer, 'No se encontró el número de ticket. Recargá la página.');
      return;
    }

    btn.disabled    = true;
    btn.textContent = 'Confirmando…';
    errorContainer.innerHTML = '';

    const { data, error } = await API.confirmarCompra(userId, ticketId);

    if (error) {
      btn.disabled    = false;
      btn.textContent = 'Confirmar compra';

      if (error.status === 409) {
        mostrarError(
          errorContainer,
          'El ticket ya no está disponible o pertenece a otro usuario. Tu sesión puede haber expirado.'
        );
      } else if (error.status === 404) {
        mostrarError(errorContainer, 'No se encontró el ticket o el usuario. Verificá tu sesión.');
      } else {
        mostrarError(errorContainer, 'Error al confirmar. Intentá de nuevo en unos segundos.');
      }
      return;
    }

    Router.Session.set({ purchaseData: data });
    Router.navigate('/success');
  }

  async function handleExpiracion(userId) {
    await API.expirarCompra(userId);
    Router.navigate('/expired');
  }

  // ─── Helpers ─────────────────────────────────────────────────

  function mostrarError(container, mensaje) {
    if (!container) return;
    container.innerHTML = `
      <div class="error-banner" role="alert">
        <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true">
          <circle cx="8" cy="8" r="7"/>
          <line x1="8" y1="5" x2="8" y2="8.5"/>
          <circle cx="8" cy="11" r="0.5" fill="currentColor"/>
        </svg>
        ${mensaje}
      </div>
    `;
  }

  return { render };

})();
