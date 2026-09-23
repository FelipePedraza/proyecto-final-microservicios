'use strict';

const { Router } = require('express');

/**
 * @openapi
 * components:
 *   schemas:
 *     Notificacion:
 *       type: object
 *       properties:
 *         id:
 *           type: integer
 *           example: 1
 *         eventoId:
 *           type: string
 *           description: Id del evento origen (usado para deduplicar).
 *           example: "3fa85f64-5717-4562-b3fc-2c963f66afa6"
 *         tipoEvento:
 *           type: string
 *           example: "empleado.creado"
 *         empleadoId:
 *           type: string
 *           example: "E001"
 *         mensaje:
 *           type: string
 *           example: "Notificación de bienvenida enviada a Juan Pérez (cargo: Desarrollador)."
 *         payload:
 *           type: object
 *           description: Envelope crudo del evento, tal como llegó por RabbitMQ.
 *         creadoEn:
 *           type: string
 *           format: date-time
 */

/** Convierte una fila de la tabla `notificaciones` (snake_case) al contrato JSON de la API. */
function aRespuesta(fila) {
  return {
    id: fila.id,
    eventoId: fila.evento_id,
    tipoEvento: fila.tipo_evento,
    empleadoId: fila.empleado_id,
    mensaje: fila.mensaje,
    payload: fila.payload,
    creadoEn: fila.creado_en,
  };
}

/**
 * Construye el router de `/notificaciones`. Recibe el `notificacionService` por
 * parámetro (en vez de importarlo) para poder montarlo en las pruebas con un
 * servicio de mentira, sin Postgres ni RabbitMQ (ver test/api.test.js).
 */
function crearNotificacionesRouter(notificacionService) {
  const router = Router();

  /**
   * @openapi
   * /notificaciones:
   *   get:
   *     summary: Lista el historial completo de notificaciones, más recientes primero.
   *     tags: [Notificaciones]
   *     responses:
   *       200:
   *         description: Historial de notificaciones.
   *         content:
   *           application/json:
   *             schema:
   *               type: array
   *               items:
   *                 $ref: '#/components/schemas/Notificacion'
   */
  router.get('/notificaciones', async (req, res, next) => {
    try {
      const filas = await notificacionService.listarTodas();
      res.json(filas.map(aRespuesta));
    } catch (err) {
      next(err);
    }
  });

  /**
   * @openapi
   * /notificaciones/{empleadoId}:
   *   get:
   *     summary: Lista el historial de notificaciones de un empleado, más recientes primero.
   *     tags: [Notificaciones]
   *     parameters:
   *       - in: path
   *         name: empleadoId
   *         required: true
   *         schema:
   *           type: string
   *         example: "E001"
   *     responses:
   *       200:
   *         description: Historial del empleado (lista vacía si nunca tuvo notificaciones).
   *         content:
   *           application/json:
   *             schema:
   *               type: array
   *               items:
   *                 $ref: '#/components/schemas/Notificacion'
   */
  router.get('/notificaciones/:empleadoId', async (req, res, next) => {
    try {
      const filas = await notificacionService.listarPorEmpleado(req.params.empleadoId);
      // Nota deliberada: se devuelve 200 con [] cuando el empleado no tiene notificaciones,
      // no 404. No poder distinguir "empleado sin notificaciones" de "empleado inexistente"
      // es aceptable aquí: este servicio no es dueño del catálogo de empleados (ese es
      // RegistroService) y no vale la pena consultarlo solo para decidir el código HTTP.
      res.json(filas.map(aRespuesta));
    } catch (err) {
      next(err);
    }
  });

  return router;
}

module.exports = { crearNotificacionesRouter, aRespuesta };
