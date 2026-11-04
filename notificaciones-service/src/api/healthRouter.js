'use strict';

const { Router } = require('express');

/**
 * Endpoints de salud, con la misma distinción liveness/readiness que ya usan
 * EmpleadoService y DepartamentoService en este proyecto:
 *   - /health        : el proceso está vivo (no depende de nada externo).
 *   - /health/ready  : el servicio puede atender tráfico de verdad (su base responde).
 *
 * No se incluye el estado de RabbitMQ en /health/ready a propósito: si el broker cae,
 * el consumidor reintenta solo (ver rabbitConsumer.js) y los GET siguen funcionando
 * contra el historial ya guardado. No tiene sentido sacar al servicio de servicio por
 * una dependencia que no bloquea su función de lectura.
 */
function crearHealthRouter(estaListaLaBaseDeDatos, estaListoRabbit) {
  const router = Router();

  /**
   * @openapi
   * /health:
   *   get:
   *     summary: Comprueba que el proceso HTTP está vivo.
   *     tags: [Salud]
   *     responses:
   *       200:
   *         description: El proceso está activo.
   *         content:
   *           application/json:
   *             schema:
   *               $ref: '#/components/schemas/HealthStatus'
   */
  const comprobarSalud = async (req, res) => {
    const [database, messageBroker] = await Promise.all([
      estaListaLaBaseDeDatos().then((ok) => (ok ? 'UP' : 'DOWN')).catch(() => 'DOWN'),
      Promise.resolve().then(estaListoRabbit).then((ok) => (ok ? 'UP' : 'DOWN')).catch(() => 'DOWN'),
    ]);
    const status = database === 'UP' && messageBroker === 'UP' ? 'UP' : 'DOWN';
    res.status(status === 'UP' ? 200 : 503).json({
      status,
      service: 'notificaciones-service',
      checks: { database, messageBroker },
    });
  };

  router.get('/health', comprobarSalud);

  /**
   * @openapi
   * /health/ready:
   *   get:
   *     summary: Comprueba que el servicio puede consultar su base de datos.
   *     tags: [Salud]
   *     responses:
   *       200:
   *         description: La base de datos está disponible.
   *         content:
   *           application/json:
   *             schema:
   *               $ref: '#/components/schemas/HealthStatus'
   *       503:
   *         description: La base de datos no está disponible.
   *         content:
   *           application/json:
   *             schema:
   *               $ref: '#/components/schemas/HealthStatus'
   */
  router.get('/health/ready', comprobarSalud);

  return router;
}

module.exports = { crearHealthRouter };
