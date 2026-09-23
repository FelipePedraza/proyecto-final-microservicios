'use strict';

const { Router } = require('express');

/**
 * Endpoints de salud, con la misma distinción liveness/readiness que ya usan
 * RegistroService y DepartamentosService en este proyecto:
 *   - /health        : el proceso está vivo (no depende de nada externo).
 *   - /health/ready  : el servicio puede atender tráfico de verdad (su base responde).
 *
 * No se incluye el estado de RabbitMQ en /health/ready a propósito: si el broker cae,
 * el consumidor reintenta solo (ver rabbitConsumer.js) y los GET siguen funcionando
 * contra el historial ya guardado. No tiene sentido sacar al servicio de servicio por
 * una dependencia que no bloquea su función de lectura.
 */
function crearHealthRouter(estaListaLaBaseDeDatos) {
  const router = Router();

  router.get('/health', (req, res) => {
    res.json({ status: 'healthy' });
  });

  router.get('/health/ready', async (req, res) => {
    const dbOk = await estaListaLaBaseDeDatos();
    if (dbOk) {
      res.json({ status: 'ready', database: 'up' });
    } else {
      res.status(503).json({ status: 'not_ready', database: 'down' });
    }
  });

  return router;
}

module.exports = { crearHealthRouter };
