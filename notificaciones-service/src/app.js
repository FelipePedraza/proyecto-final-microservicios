'use strict';

const express = require('express');
const swaggerUi = require('swagger-ui-express');
const swaggerSpec = require('./docs/swagger');
const { crearNotificacionesRouter } = require('./api/notificacionesRouter');
const { crearHealthRouter } = require('./api/healthRouter');
const { metricsEndpoint, metricsMiddleware } = require('./metrics');

/**
 * Construye la app de Express, sin arrancarla (`listen`) ni tocar RabbitMQ.
 * Separado de server.js para que las pruebas de los endpoints (test/api.test.js)
 * puedan montarla con supertest inyectando un `notificacionService` falso, en vez de
 * depender de Postgres.
 */
function crearApp({ notificacionService, estaListaLaBaseDeDatos }) {
  const app = express();
  app.use(express.json());

  // /metrics queda fuera del middleware para no medir el propio scraping.
  app.get('/metrics', metricsEndpoint);
  app.use(metricsMiddleware);

  app.use('/docs', swaggerUi.serve, swaggerUi.setup(swaggerSpec));
  app.get('/openapi.json', (req, res) => res.json(swaggerSpec));

  app.use(crearHealthRouter(estaListaLaBaseDeDatos));
  app.use(crearNotificacionesRouter(notificacionService));

  // Mismo formato de error que el resto de servicios del proyecto: { "error": "..." }.
  app.use((req, res) => {
    res.status(404).json({ error: 'Recurso no encontrado' });
  });

  // Manejador de errores final: nada debe escapar con un stack trace crudo.
  // eslint-disable-next-line no-unused-vars
  app.use((err, req, res, next) => {
    console.error('[app] Error no controlado:', err);
    res.status(500).json({ error: 'Ocurrió un error interno del servidor.' });
  });

  return app;
}

module.exports = { crearApp };
