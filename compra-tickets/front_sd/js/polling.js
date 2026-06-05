/**
 * polling.js — Motor de polling genérico y reutilizable.
 *
 * Uso:
 *   const poll = Polling.create({
 *     fn:        () => API.obtenerEstado(userId),
 *     interval:  2500,
 *     onSuccess: (data) => { ... },
 *     onError:   (err)  => { ... },
 *     maxErrors: 3,
 *   });
 *
 *   poll.start();
 *   poll.stop();   // llamar al salir de la página
 */

const Polling = (() => {

  /**
   * Crea una instancia de polling.
   *
   * @param {Object} options
   * @param {Function} options.fn           - Función async que retorna { data, error }
   * @param {number}  [options.interval]    - Intervalo en ms (default: 2500)
   * @param {Function} options.onSuccess    - Callback con el data cuando OK
   * @param {Function} [options.onError]    - Callback con el error
   * @param {number}  [options.maxErrors]   - Errores consecutivos antes de detenerse (default: 5)
   * @param {boolean} [options.immediate]   - Ejecutar inmediatamente al iniciar (default: true)
   */
  function create({
    fn,
    interval  = 2500,
    onSuccess,
    onError   = () => {},
    maxErrors = 5,
    immediate = true,
  }) {
    let timerId      = null;
    let errorCount   = 0;
    let running      = false;

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

      // Reprogramar solo si sigue activo (onSuccess podría haber llamado stop())
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
