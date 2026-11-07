'use strict';

const { Router } = require('express');

/**
 * Endpoints de salud:
 *   - /health        : el proceso HTTP está vivo (no depende de nada externo).
 *   - /health/ready  : la base de datos necesaria para servir las consultas responde.
 *
 * RabbitMQ no forma parte de readiness: el consumidor reintenta si el broker cae
 * (ver rabbitConsumer.js) y la API sigue sirviendo el historial guardado.
 */
function crearHealthRouter(estaListaLaBaseDeDatos) {
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
  router.get('/health', (req, res) => {
    res.status(200).json({
      status: 'UP',
      service: 'notificaciones-service',
    });
  });

  /**
   * @openapi
   * /health/ready:
   *   get:
   *     summary: Comprueba que la base de datos necesaria para consultar el historial responde.
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
  router.get('/health/ready', async (req, res) => {
    let database = 'UP';
    try {
      if (!(await estaListaLaBaseDeDatos())) database = 'DOWN';
    } catch {
      database = 'DOWN';
    }

    const status = database === 'UP' ? 'UP' : 'DOWN';
    res.status(status === 'UP' ? 200 : 503).json({
      status,
      service: 'notificaciones-service',
      checks: { database },
    });
  });

  return router;
}

module.exports = { crearHealthRouter };
