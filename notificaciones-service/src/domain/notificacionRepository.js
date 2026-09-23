'use strict';

/**
 * Acceso a datos de la tabla `notificaciones` (ver init.sql).
 *
 * Recibe el pool por parámetro en vez de importar `db/pool.js` directamente,
 * para poder inyectar un pool falso en las pruebas unitarias sin levantar
 * Postgres (ver test/notificacionService.test.js).
 */
class NotificacionRepository {
  constructor(pool) {
    this.pool = pool;
  }

  /**
   * Inserta la notificación si `evento_id` no existe todavía (deduplicación).
   * Devuelve la fila creada, o `null` si el evento ya se había procesado antes
   * (en ese caso NO se lanza error: es el caso normal de una redelivery de RabbitMQ).
   */
  async guardarSiEsNueva({ eventoId, tipoEvento, empleadoId, mensaje, payload }) {
    const resultado = await this.pool.query(
      `INSERT INTO notificaciones (evento_id, tipo_evento, empleado_id, mensaje, payload)
       VALUES ($1, $2, $3, $4, $5)
       ON CONFLICT (evento_id) DO NOTHING
       RETURNING id, evento_id, tipo_evento, empleado_id, mensaje, payload, creado_en`,
      [eventoId, tipoEvento, empleadoId, mensaje, payload],
    );

    return resultado.rows[0] ?? null;
  }

  /** Lista el historial completo, más reciente primero. Usa un límite razonable por defecto. */
  async listarTodas({ limite = 200 } = {}) {
    const resultado = await this.pool.query(
      `SELECT id, evento_id, tipo_evento, empleado_id, mensaje, payload, creado_en
       FROM notificaciones
       ORDER BY creado_en DESC
       LIMIT $1`,
      [limite],
    );

    return resultado.rows;
  }

  /** Historial de un empleado concreto, más reciente primero. */
  async listarPorEmpleado(empleadoId, { limite = 200 } = {}) {
    const resultado = await this.pool.query(
      `SELECT id, evento_id, tipo_evento, empleado_id, mensaje, payload, creado_en
       FROM notificaciones
       WHERE empleado_id = $1
       ORDER BY creado_en DESC
       LIMIT $2`,
      [empleadoId, limite],
    );

    return resultado.rows;
  }
}

module.exports = { NotificacionRepository };
