/**
 * router.js — Mini router basado en hash.
 */

const Router = (() => {

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
      sessionStorage.setItem(
          SESSION_KEY,
          JSON.stringify({
            ...this.get(),
            ...data,
          })
      );
    },

    clear() {
      sessionStorage.removeItem(SESSION_KEY);
    },
  };

  const routes = {
    '/': LandingPage.render,
    '/queue': QueuePage.render,
    '/buying': BuyingPage.render,
    '/success': SuccessPage.render,
    '/expired': ExpiredPage.render,
    '/rejected': RejectedPage.render,
  };

  let currentCleanup = null;

  function navigate(path) {
    window.location.hash = path;
  }

  function handleRoute() {
    if (typeof currentCleanup === 'function') {
      currentCleanup();
      currentCleanup = null;
    }

    const hash = window.location.hash.replace('#', '') || '/';
    const renderFn = routes[hash] || routes['/'];

    const app = document.getElementById('app');
    app.innerHTML = '';

    currentCleanup = renderFn(app, Session) || null;
  }

  function init() {
    window.addEventListener('hashchange', handleRoute);
    handleRoute();
  }

  return {
    navigate,
    init,
    Session,
  };

})();

document.addEventListener('DOMContentLoaded', () => Router.init());