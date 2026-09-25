'use strict';

const swaggerJsdoc = require('swagger-jsdoc');

/**
 * Genera el documento OpenAPI a partir de los comentarios `@openapi` de las rutas
 * (ver api/notificacionesRouter.js). Se sirve en /docs (ver app.js), siguiendo el
 * mismo path que ya usa DepartamentosService (FastAPI) en este proyecto.
 */
const especificacion = swaggerJsdoc({
  definition: {
    openapi: '3.0.3',
    info: {
      title: 'Notificaciones Service',
      version: '1.0.0',
      description:
        'Reto 4 - Integrante 2. Consume eventos empleado.creado, empleado.retirado y ' +
        'vacaciones.programadas desde RabbitMQ, guarda un historial de notificaciones ' +
        '(con deduplicación por id de evento) y lo expone por HTTP.',
    },
    tags: [{ name: 'Notificaciones' }],
  },
  apis: ['./src/api/*.js'],
});

module.exports = especificacion;
