/**
 * queue.js — Pantalla de espera en cola.
 *
 * Responsabilidades:
 *  1. Mostrar posición actual y total de gente esperando
 *  2. Polling cada 2.5s a GET /api/queue/status/{userId}
 *  3. Actualizar la UI sin parpadeo (solo cambia los valores)
 *  4. Cuando status cambia a BUYING  → navegar a /buying
 *  5. Cuando status cambia a EXPIRED → navegar a /expired
 *  6. Si el userId no existe en Session → volver a /
 *  7. Retornar función de cleanup para detener el polling al salir
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
              <span class="label">Comprando ahora</span>
              <span class="value" id="queue-buying">—</span>
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

    // Cargar stats generales del sistema en paralelo
    cargarStats();

    // Iniciar polling de estado
    const poll = Polling.create({
      fn:        () => API.obtenerEstado(userId),
      interval:  2500,
      onSuccess: (data) => manejarEstado(data, userId),
      onError:   (err, count) => manejarErrorPolling(err, count),
      maxErrors: 5,
    });

    poll.start();

    // Retornar cleanup: el router lo llama al cambiar de página
    return () => poll.stop();
  }

  // ─── Handlers de estado ──────────────────────────────────────

  function manejarEstado(data, userId) {
    if (!data) return;

    switch (data.status) {

      case 'WAITING':
        actualizarUI(data.position, data.totalWaiting);
        break;

      case 'BUYING':
        // Guardar ticketId en sesión antes de navegar (Opción A)
        Router.Session.set({ ticketId: data.ticketId });
        Router.navigate('/buying');
        break;

      case 'EXPIRED':
        Router.navigate('/expired');
        break;

      case 'PURCHASED':
        Router.navigate('/success');
        break;

      case 'NOT_FOUND':
        // El userId no existe en el backend → volver al inicio
        Router.Session.clear();
        Router.navigate('/');
        break;
    }
  }

  function manejarErrorPolling(err, count) {
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
          Sin conexión con el servidor (intento ${count}).
          Tu lugar en la cola está reservado. Reconnectando…
        </div>
      `;
    }
  }

  // ─── Actualización de UI ─────────────────────────────────────

  function actualizarUI(posicion, total) {
    const elPos    = document.getElementById('queue-pos');
    const elTotal  = document.getElementById('queue-total');
    const elBar    = document.getElementById('queue-bar');
    const errorContainer = document.getElementById('error-container');

    if (!elPos) return; // La página ya se desmontó

    elPos.textContent   = posicion ?? '—';
    if (elTotal) elTotal.textContent = total ?? '—';

    // Barra de progreso: cuánto avanzaste desde el total inicial
    if (posicion && total && total > 0) {
      const pct = Math.max(0, Math.min(100, ((total - posicion) / total) * 100));
      elBar.style.width = `${pct.toFixed(1)}%`;
      elBar.setAttribute('aria-valuenow', pct.toFixed(0));
    }

    // Limpiar errores previos si volvió la conexión
    if (errorContainer) errorContainer.innerHTML = '';
  }

  async function cargarStats() {
    const { data } = await API.obtenerStats();
    if (!data) return;

    const elBuying = document.getElementById('queue-buying');
    if (elBuying) elBuying.textContent = data.buying ?? '—';
  }

  return { render };

})();
