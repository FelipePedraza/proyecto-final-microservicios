'use strict';

const { Pool } = require('pg');
const env = require('../config/env');

/**
 * Pool de conexiones a la base de datos propia de notificaciones-service.
 *
 * Es un pool único compartido por todo el proceso (patrón estándar de `pg`):
 * tanto las rutas HTTP como el consumidor de RabbitMQ piden conexiones aquí,
 * nunca abren una conexión propia.
 */
const pool = new Pool({
  host: env.db.host,
  port: env.db.port,
  database: env.db.database,
  user: env.db.user,
  password: env.db.password,
});

// Un error async del pool (p. ej. la base se cae mientras una conexión está idle)
// no debe tumbar el proceso: se registra y `pg` reemplaza la conexión sola.
pool.on('error', (err) => {
  console.error('[db] Error inesperado en una conexión inactiva del pool:', err.message);
});

/**
 * Comprueba que la base responde. Se usa en GET /health/ready.
 */
async function estaLista() {
  try {
    await pool.query('SELECT 1');
    return true;
  } catch {
    return false;
  }
}

module.exports = { pool, estaLista };
