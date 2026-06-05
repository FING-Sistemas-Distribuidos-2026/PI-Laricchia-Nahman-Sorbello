/**
 * expired.js — Pantalla de expiración de turno.
 *
 * Muestra que el tiempo se agotó y ofrece volver a la cola.
 * Al reintentar: limpia el userId de sesión y vuelve al landing,
 * que creará un nuevo usuario y re-ingresará a la cola.
 */

const ExpiredPage = (() => {

  function render(container, Session) {
    container.innerHTML = `
      <div class="page" id="expired-page">

        <div style="display: flex; flex-direction: column; align-items: flex-start; gap: 1rem;">
          <div class="page-icon page-icon-warning" aria-hidden="true">
            <svg viewBox="0 0 32 32" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
              <path d="M16 4L29 27H3L16 4Z"/>
              <line x1="16" y1="14" x2="16" y2="20"/>
              <circle cx="16" cy="23.5" r="0.5" fill="currentColor"/>
            </svg>
          </div>

          <div class="page-header">
            <span class="eyebrow">Tiempo agotado</span>
            <h1>Tu turno expiró</h1>
            <p>
              El tiempo para confirmar la compra se terminó antes de que
              pudieras completarla. Tu ticket volvió al pool disponible.
            </p>
          </div>
        </div>

        <div class="card">
          <div class="card-row">
            <span class="label">Estado</span>
            <span class="badge badge-error">
              <span class="badge-dot static"></span>
              Expirado
            </span>
          </div>
          <div class="card-row">
            <span class="label">¿Qué pasó?</span>
            <span class="value" style="font-size: 0.8rem; color: var(--color-text-secondary); font-family: var(--font-body); text-align: right; max-width: 60%;">
              Los 10 minutos de reserva se agotaron
            </span>
          </div>
        </div>

        <div style="display: flex; flex-direction: column; gap: 0.75rem;">
          <button id="btn-reintentar" class="btn btn-primary">
            Volver a la cola
          </button>
          <button id="btn-salir" class="btn btn-ghost">
            No, gracias
          </button>
        </div>

        <p class="help-text">
          Al volver a la cola entrás como nuevo participante desde el final.
        </p>

      </div>
    `;

    document.getElementById('btn-reintentar').addEventListener('click', () => {
      Session.clear();
      Router.navigate('/');
    });

    document.getElementById('btn-salir').addEventListener('click', () => {
      Session.clear();
      // Mostrar mensaje de cierre en lugar de navegar en loop
      document.getElementById('expired-page').innerHTML = `
        <div class="page-header" style="text-align: center; align-items: center;">
          <h1 style="font-size: 1.8rem;">Hasta la próxima</h1>
          <p>Podés cerrar esta ventana cuando quieras.</p>
        </div>
      `;
    });
  }

  return { render };

})();
