'use strict';

const swaggerJsdoc = require('swagger-jsdoc');

/**
 * Genera el documento OpenAPI a partir de los comentarios `@openapi` de las rutas
 * (ver api/*.js). Swagger UI se sirve en /docs y el JSON OpenAPI en /openapi.json,
 * ruta estable utilizada por el agregador del gateway.
 */
const especificacion = swaggerJsdoc({
  definition: {
    openapi: '3.0.3',
    info: {
      title: 'Notificaciones Service',
      version: '1.0.0',
      description:
        'Consume empleado.retirado y vacaciones.programadas (empleados_exchange) y ' +
        'usuario.creado, usuario.recuperacion, cuenta.activada y cuenta.desactivada (auth_exchange) desde RabbitMQ, guarda un historial de notificaciones ' +
        '(con deduplicación por id de evento) y lo expone por HTTP. El servicio no valida JWT ' +
        'por sí mismo: al acceder por el gateway, las rutas de notificaciones requieren Bearer JWT ' +
        'con rol USER o ADMIN.',
    },
    servers: [{ url: '/', description: 'Rutas HTTP expuestas por el servicio y el gateway' }],
    components: {
      securitySchemes: {
        BearerAuth: {
          type: 'http',
          scheme: 'bearer',
          bearerFormat: 'JWT',
          description: 'JWT validado por el gateway; envíelo como Authorization: Bearer <token>.',
        },
      },
      schemas: {
        Error: {
          type: 'object',
          required: ['error'],
          properties: { error: { type: 'string', example: 'Ocurrió un error interno del servidor.' } },
        },
        GatewayError: {
          type: 'object',
          required: ['error', 'message'],
          properties: {
            error: { type: 'string', enum: ['unauthorized', 'forbidden'] },
            message: { type: 'string' },
          },
        },
        HealthStatus: {
          type: 'object',
          required: ['status'],
          properties: {
            status: { type: 'string', enum: ['healthy', 'ready', 'not_ready'] },
            database: { type: 'string', enum: ['up', 'down'] },
          },
        },
      },
    },
    tags: [{ name: 'Notificaciones' }],
    paths: {
      '/docs/': {
        get: {
          summary: 'Abre Swagger UI',
          tags: ['Documentación'],
          responses: { 200: { description: 'Interfaz interactiva de Swagger.' } },
        },
      },
      '/openapi.json': {
        get: {
          summary: 'Obtiene esta especificación OpenAPI en JSON',
          tags: ['Documentación'],
          responses: { 200: { description: 'Especificación OpenAPI del servicio.' } },
        },
      },
    },
  },
  apis: ['./src/api/*.js'],
});

module.exports = especificacion;
