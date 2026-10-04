'use strict';

/**
 * Configuración del servicio, leída de variables de entorno.
 *
 * Todo tiene un valor por defecto pensado para correr el servicio con
 * `npm run dev` en la máquina local, apuntando a un Postgres y un RabbitMQ
 * publicados en localhost (como hace `docker compose` con sus `ports:`).
 * En docker-compose.yml estos valores se sobrescriben con los nombres DNS
 * internos de los contenedores (p. ej. `notificaciones-db`, `message-broker`).
 */
const env = {
  puerto: parseInt(process.env.PORT || '8082', 10),

  db: {
    host: process.env.DB_HOST || 'localhost',
    port: parseInt(process.env.DB_PORT || '5432', 10),
    database: process.env.DB_NAME || 'notificaciones_db',
    user: process.env.DB_USER || 'notificaciones_user',
    password: process.env.DB_PASSWORD || 'notificaciones_password',
  },

  rabbit: {
    // Debe coincidir con el `message-broker` de docker-compose.yml (usuario/clave
    // definidos allí como RABBITMQ_DEFAULT_USER / RABBITMQ_DEFAULT_PASS = admin/admin).
    host: process.env.RABBITMQ_HOST || 'localhost',
    port: parseInt(process.env.RABBITMQ_PORT || '5672', 10),
    user: process.env.RABBITMQ_USER || 'admin',
    password: process.env.RABBITMQ_PASSWORD || 'admin',
    // Exchange declarado por empleados-service (RabbitMqPublisher.cs): fanout, durable.
    // Debe declararse aquí con los MISMOS parámetros, o RabbitMQ rechaza la declaración
    // con un error de canal (PRECONDITION_FAILED) si el exchange ya existe distinto.
    exchange: process.env.RABBITMQ_EXCHANGE || 'empleados_exchange',
    exchangeType: 'fanout',
    // Cola propia y durable: sobrevive a un reinicio de notificaciones-service sin perder
    // los eventos que hayan llegado mientras estaba caído (siempre que ya existiera antes).
    queue: process.env.RABBITMQ_QUEUE || 'notificaciones.empleados',
    // Cola de mensajes muertos: para eventos que no se pueden procesar (JSON inválido,
    // envelope sin los campos esperados). Evita un bucle infinito de redelivery.
    deadLetterQueue: process.env.RABBITMQ_DLQ || 'notificaciones.empleados.dlq',
    // Exchange donde auth-service publica usuario.creado, usuario.recuperacion, cuenta.activada y
    // cuenta.desactivada (fanout, durable). Se declara aquí con los mismos argumentos que en
    // auth-service para que funcione sin importar cuál de los dos arranque primero.
    authExchange: process.env.RABBITMQ_AUTH_EXCHANGE || 'auth_exchange',
    authQueue: process.env.RABBITMQ_AUTH_QUEUE || 'notificaciones.auth',
    authDeadLetterQueue: process.env.RABBITMQ_AUTH_DLQ || 'notificaciones.auth.dlq',
    // Reintentos de conexión al arrancar (RabbitMQ puede tardar más que este servicio
    // en levantar; ver messaging/rabbitConsumer.js).
    reconexionMs: parseInt(process.env.RABBITMQ_RECONEXION_MS || '5000', 10),
  },

  // Tipos de evento que este servicio sabe procesar. Cualquier otro tipo que llegue
  // por un exchange fanout (p. ej. empleado.actualizado) se reconoce y se descarta
  // sin error: los exchanges son compartidos por varios servicios.
  // empleado.creado ya no genera notificación: el correo de bienvenida sale con
  // usuario.creado (que lleva el token de activación).
  tiposDeEventoSoportados: [
    'empleado.retirado',
    'vacaciones.programadas',
    'usuario.creado',
    'usuario.recuperacion',
    'cuenta.activada',
    'cuenta.desactivada',
  ],
};

module.exports = env;
