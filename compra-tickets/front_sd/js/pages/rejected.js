/**
 * rejected.js — Pantalla de cola llena.
 */

const RejectedPage = (() => {

  function render(container, Session) {
    container.innerHTML = `
      <div class="page" id="rejected-page">

        <div style="display: flex; flex-direction: column; align-items: flex-start; gap: 1rem;">
          <div class="page-icon page-icon-error" aria-hidden="true">
            <svg viewBox="0 0 32 32" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
              <circle cx="16" cy="16" r="13"/>
              <line x1="11" y1="11" x2="21" y2="21"/>
              <line x1="21" y1="11" x2="11" y2="21"/>
            </svg>
          </div>

          <div class="page-header">
            <span class="eyebrow">Cola llena</span>
            <h1>No hay lugar por ahora</h1>
            <p>
              La sala de espera está al máximo de su capacidad.
              Intentá de nuevo en unos minutos.
            </p>
          </div>
        </div>

        <div class="card">
          <div class="card-row">
            <span class="label">Estado del sistema</span>
            <span class="value" id="rejected-stats">Cargando…</span>
          </div>

          <div class="card-row">
            <span class="label">¿Qué hacer?</span>
            <span class="value" style="font-size: 0.8rem; color: var(--color-text-secondary); font-family: var(--font-body); text-align: right; max-width: 60%;">
              Reintentá en unos minutos.
            </span>
          </div>
        </div>

        <div style="display: flex; flex-direction: column; gap: 0.75rem;">
          <button id="btn-reintentar" class="btn btn-primary">
            Intentar de nuevo
          </button>
        </div>

        <p class="help-text">
          Las entradas son limitadas. La disponibilidad puede agotarse.
        </p>

      </div>
    `;

    cargarStats();

    document.getElementById('btn-reintentar').addEventListener('click', () => {
      Session.clear();
      Router.navigate('/');
    });
  }

  async function cargarStats() {
    const el = document.getElementById('rejected-stats');
    if (!el) return;

    const { data } = await API.obtenerStats();

    if (data?.waiting !== undefined) {
      el.textContent = `${data.waiting} esperando`;
    } else {
      el.textContent = 'No disponible';
    }
  }

  return { render };

})();