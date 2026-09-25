'use strict';

function emailDesdePayload(alias) {
  return `COALESCE(
    NULLIF(${alias}.payload #>> '{Data,Email}', ''),
    NULLIF(${alias}.payload #>> '{Data,email}', ''),
    NULLIF(${alias}.payload #>> '{data,Email}', ''),
    NULLIF(${alias}.payload #>> '{data,email}', '')
  )`;
}

function destinatarioSelect(alias) {
  return `COALESCE(
    ${emailDesdePayload(alias)},
    (
      SELECT ${emailDesdePayload('origen')}
      FROM notificaciones AS origen
      WHERE origen.empleado_id = ${alias}.empleado_id
        AND origen.tipo_evento = 'empleado.creado'
      ORDER BY origen.creado_en ASC
      LIMIT 1
    )
  ) AS destinatario`;
}

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
      `SELECT n.id, n.evento_id, n.tipo_evento, n.empleado_id, n.mensaje, n.payload, n.creado_en,
              ${destinatarioSelect('n')}
       FROM notificaciones AS n
       ORDER BY n.creado_en DESC
       LIMIT $1`,
      [limite],
    );

    return resultado.rows;
  }

  /** Historial de un empleado concreto, más reciente primero. */
  async listarPorEmpleado(empleadoId, { limite = 200 } = {}) {
    const resultado = await this.pool.query(
      `SELECT n.id, n.evento_id, n.tipo_evento, n.empleado_id, n.mensaje, n.payload, n.creado_en,
              ${destinatarioSelect('n')}
       FROM notificaciones AS n
       WHERE n.empleado_id = $1
       ORDER BY n.creado_en DESC
       LIMIT $2`,
      [empleadoId, limite],
    );

    return resultado.rows;
  }

  async buscarDestinatario(empleadoId) {
    const resultado = await this.pool.query(
      `SELECT ${emailDesdePayload('n')} AS email
       FROM notificaciones AS n
       WHERE n.empleado_id = $1
         AND n.tipo_evento = 'empleado.creado'
       ORDER BY n.creado_en ASC
       LIMIT 1`,
      [empleadoId],
    );

    return resultado.rows[0]?.email ?? null;
  }
}

module.exports = { NotificacionRepository };
