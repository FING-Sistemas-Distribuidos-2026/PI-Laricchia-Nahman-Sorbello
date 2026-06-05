/**
 * landing.js — Pantalla inicial.
 *
 * Responsabilidades:
 *  1. Mostrar el evento y el botón "QUIERO COMPRAR"
 *  2. Al hacer click: POST /api/usuarios → guardar userId en Session
 *  3. POST /api/queue/join → manejar respuesta
 *     - 201 WAITING  → navegar a /queue
 *     - 409 BUYING   → ya tiene sesión activa → navegar a /buying
 *     - 409 WAITING  → ya estaba en cola → navegar a /queue
 *     - 409 REJECTED → cola llena → navegar a /rejected
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

    document.getElementById('btn-comprar').addEventListener('click', handleComprar);
  }

  async function handleComprar() {
    const btn = document.getElementById('btn-comprar');
    const errorContainer = document.getElementById('error-container');

    setLoading(btn, true);
    errorContainer.innerHTML = '';

    try {
      await entrarACola();
    } catch (err) {
      // Nunca debería llegar acá (api.js captura todo), pero por si acaso
      mostrarError(errorContainer, 'Ocurrió un error inesperado. Intentá de nuevo.');
      setLoading(btn, false);
    }
  }

  async function entrarACola() {
    const btn = document.getElementById('btn-comprar');
    const errorContainer = document.getElementById('error-container');
    const Session = Router.Session;

    // ── Paso 1: crear usuario ────────────────────────────────
    const { data: usuario, error: errorUsuario } = await API.crearUsuario();

    if (errorUsuario) {
      mostrarError(
        errorContainer,
        'No pudimos conectarnos al servidor. Verificá tu conexión e intentá de nuevo.'
      );
      setLoading(btn, false);
      return;
    }

    const userId = usuario.id;
    Session.set({ userId });

    // ── Paso 2: unirse a la cola ─────────────────────────────
    const { data: colaData, error: errorCola } = await API.unirseACola(userId);

    if (errorCola) {
      // 409 tiene datos útiles en el body
      if (errorCola.status === 409 && errorCola.body) {
        const status = errorCola.body.status;

        if (status === 'BUYING') {
          // Ya tiene una sesión activa de compra
          Router.navigate('/buying');
          return;
        }
        if (status === 'WAITING') {
          // Ya estaba en la cola
          Router.navigate('/queue');
          return;
        }
        if (status === 'REJECTED') {
          // Cola llena
          Router.navigate('/rejected');
          return;
        }
      }

      mostrarError(
        errorContainer,
        'No se pudo acceder a la cola. El sistema puede estar saturado. Intentá en unos segundos.'
      );
      setLoading(btn, false);
      return;
    }

    // 201 → WAITING
    Router.navigate('/queue');
  }

  // ─── Helpers de UI ──────────────────────────────────────────

  function setLoading(btn, loading) {
    btn.disabled = loading;
    btn.textContent = loading ? 'Ingresando a la cola…' : 'Quiero comprar mi entrada';
  }

  function mostrarError(container, mensaje) {
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
