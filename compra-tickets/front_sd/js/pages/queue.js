/**
 * queue.js — Pantalla de espera en cola.
 *
 * Esta pantalla solo muestra WAITING.
 *
 * Cuando el gateway informa BUYING, significa que el scheduler ya sacó
 * al usuario de waiting_queue y compra-service creó su sesión en
 * buying:sessions.
 */

const QueuePage = (() => {

  function render(container, Session) {
    const { userId } = Session.get();

    if (!userId) {
      Router.navigate('/');
      return;
    }

    container.innerHTML = `
      <div class="page" id="queue-page">

        <header class="page-header">
          <span class="eyebrow">Cola de espera</span>
          <h1>Estás en la fila</h1>
          <p>
            Estamos procesando las compras de a grupos.
            Te avisamos cuando sea tu turno.
          </p>
        </header>

        <div class="card">
          <div class="queue-position">
            <span class="number" id="queue-pos">—</span>
            <span class="sub">tu posición</span>
          </div>

          <div class="progress-bar" role="progressbar" aria-label="Progreso en la cola">
            <div class="fill" id="queue-bar" style="width: 0%"></div>
          </div>

          <div style="margin-top: 1rem;">
            <div class="card-row">
              <span class="label">Personas esperando</span>
              <span class="value" id="queue-total">—</span>
            </div>

            <div class="card-row">
              <span class="label">Estado</span>
              <span class="badge badge-waiting" id="queue-badge">
                <span class="badge-dot"></span>
                Esperando
              </span>
            </div>
          </div>
        </div>

        <p class="help-text" id="queue-refresh-hint">
          Actualizando cada pocos segundos…
        </p>

        <div id="error-container"></div>

      </div>
    `;

    cargarStats();

    const poll = Polling.create({
      fn: () => API.obtenerEstado(userId),
      interval: 2500,
      maxErrors: 5,
      onSuccess: (data) => manejarEstado(data),
      onError: (err, count) => manejarErrorPolling(count),
    });

    poll.start();

    return () => poll.stop();
  }

  function manejarEstado(data) {
    if (!data) return;

    switch (data.status) {
      case 'WAITING':
        actualizarUI(data.position, data.totalWaiting);
        break;

      case 'BUYING':
        Router.Session.set({
          ticketId: data.ticketId,
          ttlRemaining: data.ttlRemaining,
        });
        Router.navigate('/buying');
        break;

      case 'EXPIRED':
        Router.navigate('/expired');
        break;

      case 'PURCHASED':
        Router.navigate('/success');
        break;

      case 'REJECTED':
        Router.navigate('/rejected');
        break;

      case 'NOT_FOUND':
        Router.Session.clear();
        Router.navigate('/');
        break;

      default:
        break;
    }
  }

  function actualizarUI(posicion, total) {
    const elPos = document.getElementById('queue-pos');
    const elTotal = document.getElementById('queue-total');
    const elBar = document.getElementById('queue-bar');
    const errorContainer = document.getElementById('error-container');

    if (!elPos) return;

    elPos.textContent = posicion ?? '—';

    if (elTotal) {
      elTotal.textContent = total ?? '—';
    }

    if (posicion && total && total > 0) {
      const pct = Math.max(0, Math.min(100, ((total - posicion) / total) * 100));
      elBar.style.width = `${pct.toFixed(1)}%`;
      elBar.setAttribute('aria-valuenow', pct.toFixed(0));
    }

    if (errorContainer) {
      errorContainer.innerHTML = '';
    }
  }

  function manejarErrorPolling(count) {
    const errorContainer = document.getElementById('error-container');
    if (!errorContainer) return;

    if (count >= 3) {
      errorContainer.innerHTML = `
        <div class="error-banner" role="alert">
          <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true">
            <circle cx="8" cy="8" r="7"/>
            <line x1="8" y1="5" x2="8" y2="8.5"/>
            <circle cx="8" cy="11" r="0.5" fill="currentColor"/>
          </svg>
          Sin conexión con el servidor. Tu lugar en la cola debería mantenerse.
        </div>
      `;
    }
  }

  async function cargarStats() {
    const { data } = await API.obtenerStats();
    if (!data) return;

    const elTotal = document.getElementById('queue-total');

    if (elTotal && data.waiting !== undefined) {
      elTotal.textContent = data.waiting;
    }
  }

  return { render };

})();