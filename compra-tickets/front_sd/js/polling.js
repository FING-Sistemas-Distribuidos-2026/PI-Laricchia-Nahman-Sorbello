/**
 * polling.js — Motor de polling genérico.
 */

const Polling = (() => {

  function create({
                    fn,
                    interval = 2500,
                    onSuccess,
                    onError = () => {},
                    maxErrors = 5,
                    immediate = true,
                  }) {
    let timerId = null;
    let errorCount = 0;
    let running = false;

    async function tick() {
      if (!running) return;

      const { data, error } = await fn();

      if (error) {
        errorCount++;
        onError(error, errorCount);

        if (errorCount >= maxErrors) {
          console.warn(`[Polling] Detenido tras ${maxErrors} errores consecutivos.`);
          stop();
          return;
        }
      } else {
        errorCount = 0;
        onSuccess(data);
      }

      if (running) {
        timerId = setTimeout(tick, interval);
      }
    }

    function start() {
      if (running) return;

      running = true;
      errorCount = 0;

      if (immediate) {
        tick();
      } else {
        timerId = setTimeout(tick, interval);
      }
    }

    function stop() {
      running = false;

      if (timerId !== null) {
        clearTimeout(timerId);
        timerId = null;
      }
    }

    return { start, stop };
  }

  return { create };

})();