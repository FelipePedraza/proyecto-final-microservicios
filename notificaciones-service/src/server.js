'use strict';

const env = require('./config/env');
const { pool, estaLista } = require('./db/pool');
const { NotificacionRepository } = require('./domain/notificacionRepository');
const { NotificacionService } = require('./domain/notificacionService');
const { RabbitConsumer } = require('./messaging/rabbitConsumer');
const { crearApp } = require('./app');

async function main() {
  const repository = new NotificacionRepository(pool);
  const notificacionService = new NotificacionService(repository);

  const app = crearApp({ notificacionService, estaListaLaBaseDeDatos: estaLista });
  const servidorHttp = app.listen(env.puerto, () => {
    console.log(`[http] notificaciones-service escuchando en el puerto ${env.puerto}`);
    console.log(`[http] Swagger disponible en /docs`);
  });

  // El consumidor arranca en paralelo al servidor HTTP: si RabbitMQ todavía no está
  // listo, la API sigue respondiendo igual (ver comentario en rabbitConsumer.js).
  const rabbitConsumer = new RabbitConsumer(notificacionService);
  rabbitConsumer.iniciar().catch((err) => {
    // `iniciar()` ya reintenta internamente; si igual rechaza es un error de
    // programación, no una caída transitoria del broker.
    console.error('[rabbit] Fallo irrecuperable iniciando el consumidor:', err);
  });

  const apagar = async (señal) => {
    console.log(`[server] ${señal} recibido, cerrando...`);
    await rabbitConsumer.cerrar();
    await new Promise((resolve) => servidorHttp.close(resolve));
    await pool.end();
    process.exit(0);
  };

  process.on('SIGTERM', () => apagar('SIGTERM'));
  process.on('SIGINT', () => apagar('SIGINT'));
}

main().catch((err) => {
  console.error('[server] Error fatal al arrancar:', err);
  process.exit(1);
});
