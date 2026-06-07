/**
 * success.js — Pantalla de compra exitosa.
 *
 * Solo se llega acá si compra-service respondió PURCHASED.
 */

const SuccessPage = (() => {

  function render(container, Session) {
    const { userId, ticketId, purchaseData } = Session.get();

    const displayTicketId = purchaseData?.ticketId ?? ticketId ?? '—';
    const displayUserId = userId ? `${userId.slice(0, 8)}…` : '—';

    container.innerHTML = `
      <div class="page" id="success-page">

        <div style="display: flex; flex-direction: column; align-items: flex-start; gap: 1rem;">
          <div class="page-icon page-icon-success" aria-hidden="true">
            <svg viewBox="0 0 32 32" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
              <circle cx="16" cy="16" r="13"/>
              <polyline points="10,16 14,20 22,12"/>
            </svg>
          </div>

          <div class="page-header">
            <span class="eyebrow">Compra confirmada</span>
            <h1>¡Tu entrada es tuya!</h1>
            <p>
              La compra se procesó correctamente. Guardá los datos de abajo
              como comprobante.
            </p>
          </div>
        </div>

        <div class="card">
          <div class="card-row">
            <span class="label">Estado</span>
            <span class="badge badge-success">
              <span class="badge-dot static"></span>
              Confirmado
            </span>
          </div>

          <div class="card-row">
            <span class="label">N.° de ticket</span>
            <span class="value">${displayTicketId}</span>
          </div>

          <div class="card-row">
            <span class="label">ID de usuario</span>
            <span class="value">${displayUserId}</span>
          </div>
        </div>

        <p style="font-size: 0.85rem; color: var(--color-text-secondary); line-height: 1.6;">
          Recibirás la confirmación oficial por el canal que indicó la organización del evento.
          Ante cualquier problema, presentá el N.° de ticket al ingresar.
        </p>

        <div class="help-text">
          Podés cerrar esta ventana de forma segura.
        </div>

      </div>
    `;

    Session.clear();
  }

  return { render };

})();