/**
 * router.js — Mini router basado en hash (#).
 *
 * Rutas:
 *   #/          → LandingPage
 *   #/queue     → QueuePage
 *   #/buying    → BuyingPage
 *   #/success   → SuccessPage
 *   #/expired   → ExpiredPage
 *   #/rejected  → RejectedPage
 *
 * Uso desde cualquier página:
 *   Router.navigate('/queue');
 *
 * Estado global de sesión: guardado en sessionStorage para sobrevivir
 * un F5 accidental, pero se limpia al cerrar la pestaña.
 */

const Router = (() => {

  // ─── Estado de sesión del usuario ───────────────────────────
  const SESSION_KEY = 'ticketera_session';

  const Session = {
    get() {
      try {
        return JSON.parse(sessionStorage.getItem(SESSION_KEY)) || {};
      } catch {
        return {};
      }
    },
    set(data) {
      sessionStorage.setItem(SESSION_KEY, JSON.stringify({ ...this.get(), ...data }));
    },
    clear() {
      sessionStorage.removeItem(SESSION_KEY);
    },
  };

  // ─── Mapa de rutas → funciones de render ────────────────────
  const routes = {
    '/':         LandingPage.render,
    '/queue':    QueuePage.render,
    '/buying':   BuyingPage.render,
    '/success':  SuccessPage.render,
    '/expired':  ExpiredPage.render,
    '/rejected': RejectedPage.render,
  };

  // ─── Página activa actual (para cleanup) ─────────────────────
  let currentCleanup = null;

  function navigate(path) {
    window.location.hash = path;
  }

  function handleRoute() {
    // Limpiar página anterior si dejó timers o polling
    if (typeof currentCleanup === 'function') {
      currentCleanup();
      currentCleanup = null;
    }

    const hash = window.location.hash.replace('#', '') || '/';
    const renderFn = routes[hash] || routes['/'];

    const app = document.getElementById('app');
    app.innerHTML = '';

    // Cada página puede retornar una función de cleanup
    currentCleanup = renderFn(app, Session) || null;
  }

  function init() {
    window.addEventListener('hashchange', handleRoute);
    handleRoute(); // Render inicial
  }

  return { navigate, init, Session };

})();

// Arrancar el router cuando el DOM esté listo
document.addEventListener('DOMContentLoaded', () => Router.init());
