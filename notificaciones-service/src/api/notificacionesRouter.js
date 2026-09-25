'use strict';

const { Router } = require('express');
const { tipoNotificacion, obtenerDestinatario } = require('../domain/eventoParser');

/**
 * @openapi
 * components:
 *   schemas:
 *     Notificacion:
 *       type: object
 *       required: [id, tipo, destinatario, mensaje, fechaEnvio, empleadoId]
 *       properties:
 *         id:
 *           type: string
 *           example: "1"
 *         tipo:
 *           type: string
 *           enum: [BIENVENIDA, DESVINCULACION, VACACIONES]
 *           example: DESVINCULACION
 *         destinatario:
 *           type: string
 *           format: email
 *           example: "juan.perez@empresa.com"
 *         mensaje:
 *           type: string
 *           example: "Su cuenta ha sido desvinculada."
 *         fechaEnvio:
 *           type: string
 *           format: date-time
 *         empleadoId:
 *           type: string
 *           example: "E001"
 */

/** Convierte una fila de la tabla `notificaciones` (snake_case) al contrato JSON de la API. */
function aRespuesta(fila) {
  const payload = fila.payload ?? {};
  const data = payload.Data ?? payload.data ?? {};

  return {
    id: String(fila.id),
    tipo: tipoNotificacion(fila.tipo_evento),
    destinatario: fila.destinatario ?? obtenerDestinatario(data) ?? 'no informado',
    mensaje: fila.mensaje,
    fechaEnvio: fila.creado_en,
    empleadoId: fila.empleado_id,
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
