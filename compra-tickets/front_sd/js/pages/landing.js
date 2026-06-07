/**
 * landing.js — Pantalla inicial.
 *
 * Flujo:
 *
 * 1. Crear usuario.
 * 2. Unirlo a la cola.
 * 3. Si entra WAITING, ir a /queue.
 * 4. Si por alguna razón ya está BUYING, ir a /buying.
 * 5. Si la cola está llena, ir a /rejected.
 */

const LandingPage = (() => {

  function render(container, Session) {
    container.innerHTML = `
      <div class="page" id="landing-page">

        <header class="page-header">
          <span class="eyebrow">Evento único · 2026</span>
          <h1>La noche que no podés perderte</h1>
          <p>
            Miles de personas están esperando. El sistema te asigna
            un lugar en la fila y te avisa cuando sea tu turno para comprar.
          </p>
        </header>

        <div class="card">
          <div class="card-row">
            <span class="label">Fecha</span>
            <span class="value">15 Nov 2026</span>
          </div>
          <div class="card-row">
            <span class="label">Lugar</span>
            <span class="value">Estadio Central</span>
          </div>
          <div class="card-row">
            <span class="label">Apertura</span>
            <span class="value">20:00 hs</span>
          </div>
        </div>

        <div style="display: flex; flex-direction: column; gap: 0.75rem;">
          <button id="btn-comprar" class="btn btn-primary">
            Quiero comprar mi entrada
          </button>
          <p class="help-text">
            Al continuar entrás a la fila virtual. Te avisamos cuando sea tu turno.
          </p>
        </div>

        <div id="error-container"></div>

      </div>
    `;

    document
        .getElementById('btn-comprar')
        .addEventListener('click', handleComprar);
  }

  async function handleComprar() {
    const btn = document.getElementById('btn-comprar');
    const errorContainer = document.getElementById('error-container');

    setLoading(btn, true);
    errorContainer.innerHTML = '';

    const { data: usuario, error: errorUsuario } = await API.crearUsuario();

    if (errorUsuario || !usuario?.id) {
      mostrarError(
          errorContainer,
          'No pudimos crear tu sesión. Verificá tu conexión e intentá de nuevo.'
      );
      setLoading(btn, false);
      return;
    }

    const userId = usuario.id;

    Router.Session.set({
      userId,
      ticketId: null,
      purchaseData: null,
    });

    const { data: colaData, error: errorCola } = await API.unirseACola(userId);

    if (errorCola) {
      const body = errorCola.body;

      if (body?.status === 'BUYING') {
        Router.Session.set({
          ticketId: body.ticketId,
          ttlRemaining: body.ttlRemaining,
        });
        Router.navigate('/buying');
        return;
      }

      if (body?.status === 'WAITING') {
        Router.navigate('/queue');
        return;
      }

      if (body?.status === 'REJECTED') {
        Router.navigate('/rejected');
        return;
      }

      mostrarError(
          errorContainer,
          'No se pudo acceder a la cola. El sistema puede estar saturado. Intentá en unos segundos.'
      );

      setLoading(btn, false);
      return;
    }

    if (colaData?.status === 'REJECTED') {
      Router.navigate('/rejected');
      return;
    }

    if (colaData?.status === 'BUYING') {
      Router.Session.set({
        ticketId: colaData.ticketId,
        ttlRemaining: colaData.ttlRemaining,
      });
      Router.navigate('/buying');
      return;
    }

    Router.navigate('/queue');
  }

  function setLoading(btn, loading) {
    btn.disabled = loading;
    btn.textContent = loading
        ? 'Ingresando a la cola…'
        : 'Quiero comprar mi entrada';
  }

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