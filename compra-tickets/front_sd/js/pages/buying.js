/**
 * buying.js — Pantalla "¡Es tu turno!".
 *
 * Responsabilidades:
 *
 * 1. Mostrar cuenta regresiva.
 * 2. Consultar TTL desde /api/buying/ttl/{userId}.
 * 3. Cuando el TTL llega a 0, llamar /api/buying/expire/{userId}.
 * 4. Antes de confirmar compra, volver a consultar TTL.
 * 5. Si TTL > 0, llamar /api/purchase.
 * 6. Si backend responde PURCHASED, navegar a success.
 * 7. Si backend responde EXPIRED, navegar a expired.
 */

const BuyingPage = (() => {

  const TTL_TOTAL_DEFAULT = 600;
  const URGENT_THRESHOLD = 60;

  let ttlActual = TTL_TOTAL_DEFAULT;
  let ttlTotal = TTL_TOTAL_DEFAULT;

  let countdownId = null;
  let expiradoYa = false;
  let confirmando = false;

  function render(container, Session) {
    const { userId, ticketId } = Session.get();

    if (!userId) {
      Router.navigate('/');
      return;
    }

    ttlActual = TTL_TOTAL_DEFAULT;
    ttlTotal = TTL_TOTAL_DEFAULT;
    expiradoYa = false;
    confirmando = false;

    container.innerHTML = `
      <div class="page" id="buying-page">

        <header class="page-header">
          <span class="eyebrow">¡Es tu turno!</span>
          <h1>Confirmá tu compra</h1>
          <p>
            Tenés tiempo limitado para completar la compra.
            Si el tiempo se agota, el ticket vuelve a estar disponible.
          </p>
        </header>

        <div class="card" style="align-items: center;">
          <div class="countdown-ring">
            <svg width="120" height="120" viewBox="0 0 120 120" aria-hidden="true">
              <circle class="ring-bg" cx="60" cy="60" r="52"/>
              <circle
                class="ring-fill"
                cx="60"
                cy="60"
                r="52"
                id="ring-fill"
                stroke-dasharray="326.7"
                stroke-dashoffset="0"
              />
            </svg>

            <span
              class="countdown-time"
              id="countdown-time"
              aria-live="polite"
              aria-label="Tiempo restante"
            >
              10:00
            </span>

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
            Antes de confirmar, el sistema vuelve a validar que tu tiempo no haya expirado.
          </p>
        </div>

        <div id="error-container"></div>

      </div>
    `;

    document
        .getElementById('btn-confirmar')
        .addEventListener('click', () => handleConfirmar(userId, ticketId));

    sincronizarTTLInicial(userId).then(() => {
      iniciarCountdown(userId);
    });

    const pollTTL = Polling.create({
      fn: () => API.obtenerTTL(userId),
      interval: 5000,
      immediate: false,
      maxErrors: 5,
      onSuccess: (data) => {
        if (!data) return;

        const ttl = Number(data.ttl);

        if (ttl === -2) {
          Router.navigate('/expired');
          return;
        }

        if (ttl <= 0) {
          handleExpiracion(userId);
          return;
        }

        ajustarTTL(ttl);
      },
      onError: () => {
        /**
         * No expiramos por error de red.
         * El usuario puede estar sin conexión momentáneamente.
         */
      },
    });

    const pollEstado = Polling.create({
      fn: () => API.obtenerEstadoBuying(userId),
      interval: 3000,
      immediate: false,
      maxErrors: 5,
      onSuccess: (data) => {
        if (!data) return;

        if (data.status === 'EXPIRED') {
          cleanup();
          Router.navigate('/expired');
          return;
        }

        if (data.status === 'PURCHASED') {
          cleanup();
          Router.navigate('/success');
          return;
        }

        if (data.status === 'NOT_FOUND') {
          cleanup();
          Router.navigate('/expired');
        }
      },
    });

    pollTTL.start();
    pollEstado.start();

    function cleanup() {
      pollTTL.stop();
      pollEstado.stop();

      if (countdownId) {
        clearInterval(countdownId);
        countdownId = null;
      }
    }

    return cleanup;
  }

  // ─────────────────────────────────────────────
  // TTL
  // ─────────────────────────────────────────────

  async function sincronizarTTLInicial(userId) {
    const { data, error } = await API.obtenerTTL(userId);

    if (error || !data) {
      renderCountdown(ttlActual);
      return;
    }

    const ttl = Number(data.ttl);

    if (ttl === -2) {
      Router.navigate('/expired');
      return;
    }

    if (ttl <= 0) {
      await handleExpiracion(userId);
      return;
    }

    ttlActual = ttl;
    ttlTotal = Math.max(ttl, TTL_TOTAL_DEFAULT);

    renderCountdown(ttlActual);
  }

  function iniciarCountdown(userId) {
    if (countdownId) {
      clearInterval(countdownId);
    }

    countdownId = setInterval(async () => {
      ttlActual = Math.max(0, ttlActual - 1);
      renderCountdown(ttlActual);

      if (ttlActual <= 0 && !expiradoYa) {
        await handleExpiracion(userId);
      }
    }, 1000);
  }

  function ajustarTTL(ttlReal) {
    if (Number.isNaN(ttlReal)) {
      return;
    }

    /**
     * Ajustamos solo si la diferencia es grande para evitar saltos visuales.
     */
    if (Math.abs(ttlActual - ttlReal) > 2) {
      ttlActual = ttlReal;
      ttlTotal = Math.max(ttlTotal, ttlReal);
      renderCountdown(ttlActual);
    }
  }

  function renderCountdown(segundos) {
    const timeEl = document.getElementById('countdown-time');
    const ringEl = document.getElementById('ring-fill');
    const urgencyEl = document.getElementById('urgency-banner');

    if (!timeEl) return;

    const mins = Math.floor(segundos / 60);
    const secs = segundos % 60;

    timeEl.textContent = `${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;

    const CIRCUNFERENCIA = 326.7;
    const progreso = ttlTotal > 0 ? segundos / ttlTotal : 0;
    const offset = CIRCUNFERENCIA * (1 - progreso);

    if (ringEl) {
      ringEl.style.strokeDashoffset = offset.toFixed(2);
      ringEl.classList.toggle('urgent', segundos <= URGENT_THRESHOLD);
    }

    if (urgencyEl) {
      urgencyEl.style.display = segundos <= URGENT_THRESHOLD ? 'block' : 'none';
    }
  }

  // ─────────────────────────────────────────────
  // Confirmar compra
  // ─────────────────────────────────────────────

  async function handleConfirmar(userId, ticketId) {
    const btn = document.getElementById('btn-confirmar');
    const errorContainer = document.getElementById('error-container');

    if (confirmando) return;

    if (!ticketId) {
      mostrarError(
          errorContainer,
          'No se encontró el número de ticket. Volvé a la cola e intentá nuevamente.'
      );
      return;
    }

    confirmando = true;
    btn.disabled = true;
    btn.textContent = 'Validando tiempo…';
    errorContainer.innerHTML = '';

    /**
     * Primero verificamos TTL desde el BFF.
     * Esto mantiene el comportamiento que ustedes querían:
     * "si no expiró, compra; si expiró, error".
     */
    const { data: ttlData, error: ttlError } = await API.obtenerTTL(userId);

    if (ttlError || !ttlData) {
      confirmando = false;
      btn.disabled = false;
      btn.textContent = 'Confirmar compra';

      mostrarError(
          errorContainer,
          'No pudimos validar tu tiempo restante. Intentá de nuevo en unos segundos.'
      );

      return;
    }

    const ttl = Number(ttlData.ttl);

    if (ttl <= 0) {
      await handleExpiracion(userId);
      return;
    }

    btn.textContent = 'Confirmando…';

    const { data, error } = await API.confirmarCompra(userId, ticketId);

    if (error) {
      confirmando = false;
      btn.disabled = false;
      btn.textContent = 'Confirmar compra';

      if (error.status === 409) {
        mostrarError(
            errorContainer,
            'El ticket ya no está disponible o tu tiempo de compra expiró.'
        );
      } else if (error.status === 404) {
        mostrarError(
            errorContainer,
            'No se encontró el ticket o el usuario. Verificá tu sesión.'
        );
      } else {
        mostrarError(
            errorContainer,
            'Error al confirmar. Intentá de nuevo en unos segundos.'
        );
      }

      return;
    }

    if (data?.status === 'EXPIRED') {
      await handleExpiracion(userId);
      return;
    }

    if (data?.status !== 'PURCHASED') {
      confirmando = false;
      btn.disabled = false;
      btn.textContent = 'Confirmar compra';

      mostrarError(
          errorContainer,
          'No se pudo confirmar la compra. Verificá el estado de tu sesión.'
      );

      return;
    }

    Router.Session.set({
      purchaseData: data,
      ticketId: data.ticketId ?? ticketId,
    });

    Router.navigate('/success');
  }

  // ─────────────────────────────────────────────
  // Expirar
  // ─────────────────────────────────────────────

  async function handleExpiracion(userId) {
    if (expiradoYa) return;

    expiradoYa = true;

    if (countdownId) {
      clearInterval(countdownId);
      countdownId = null;
    }

    try {
      await API.expirarCompra(userId);
    } finally {
      Router.navigate('/expired');
    }
  }

  // ─────────────────────────────────────────────
  // Helpers UI
  // ─────────────────────────────────────────────

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