'use strict';

const amqp = require('amqplib');
const env = require('../config/env');
const { EventoInvalidoError } = require('../domain/eventoParser');

/**
 * Consumidor de RabbitMQ: se conecta al mismo exchange fanout que declara
 * empleados-service (`RabbitMqPublisher.cs`), liga una cola propia y durable, y por
 * cada mensaje llama a `notificacionService.procesarEvento`.
 *
 * Topología (todo `durable: true`, sobrevive a un reinicio del broker):
 *
 *   empleados_exchange (fanout)          notificaciones.empleados.dlq
 *          │                                        ▲
 *          ├──► notificaciones.empleados ────(reject sin requeue)
 *                        │
 *                        └──► NotificacionService.procesarEvento()
 *
 * Se declara el exchange con `assertExchange` usando los MISMOS parámetros que el
 * productor (fanout, durable). RabbitMQ exige que quien declare un exchange que ya
 * existe use exactamente los mismos parámetros, o cierra el canal con un error
 * PRECONDITION_FAILED; por eso esto no es una suposición, tiene que calzar con
 * RabbitMqPublisher.cs.
 *
 * Política de ack (entrega "at-least-once", nunca "exactly-once"):
 *   - Procesado con éxito (nuevo o duplicado)  -> ack.
 *   - Envelope mal formado (EventoInvalidoError) -> reject SIN requeue -> va a la DLQ.
 *     Reintentarlo no serviría: el mensaje nunca va a estar "bien formado" solo.
 *   - Cualquier otro error (Postgres caído, etc.) -> nack CON requeue. Es una falla
 *     transitoria: vale la pena que RabbitMQ lo vuelva a entregar más tarde.
 */
class RabbitConsumer {
  constructor(notificacionService) {
    this.notificacionService = notificacionService;
    this.connection = null;
    this.channel = null;
    this.cerrando = false;
  }

  /**
   * Se conecta y empieza a consumir. Si RabbitMQ todavía no está arriba (arranque de
   * docker-compose sin healthcheck que lo garantice, o el broker tarda en levantar)
   * reintenta con espera fija en vez de morir: este servicio debe poder arrancar
   * incluso si el broker no está listo todavía.
   */
  async iniciar() {
    while (!this.cerrando) {
      try {
        await this._conectarYConsumir();
        return;
      } catch (err) {
        console.error(
          `[rabbit] No se pudo conectar (se reintenta en ${env.rabbit.reconexionMs} ms): ${err.message}`,
        );
        await new Promise((resolve) => setTimeout(resolve, env.rabbit.reconexionMs));
      }
    }
  }

  async _conectarYConsumir() {
    const url = `amqp://${env.rabbit.user}:${env.rabbit.password}@${env.rabbit.host}:${env.rabbit.port}`;
    this.connection = await amqp.connect(url);
    this.channel = await this.connection.createChannel();

    // Si la conexión se cae más tarde (no en el arranque), se reintenta igual.
    this.connection.on('close', () => {
      if (!this.cerrando) {
        console.warn('[rabbit] Conexión cerrada inesperadamente; reintentando...');
        this.iniciar();
      }
    });
    this.connection.on('error', (err) => {
      console.error('[rabbit] Error de conexión:', err.message);
    });

    await this.channel.assertExchange(env.rabbit.exchange, env.rabbit.exchangeType, { durable: true });

    // Cola de mensajes muertos: sin exchange propio, se liga por nombre directamente.
    await this.channel.assertQueue(env.rabbit.deadLetterQueue, { durable: true });

    // Cola principal, con dead-lettering hacia la DLQ vía el exchange por defecto ("").
    await this.channel.assertQueue(env.rabbit.queue, {
      durable: true,
      arguments: {
        'x-dead-letter-exchange': '',
        'x-dead-letter-routing-key': env.rabbit.deadLetterQueue,
      },
    });
    await this.channel.bindQueue(env.rabbit.queue, env.rabbit.exchange, '');

    // Un mensaje sin confirmar a la vez: este servicio no necesita alto throughput y
    // así se evita que una ráfaga de eventos sature el pool de Postgres.
    await this.channel.prefetch(1);

    console.log(
      `[rabbit] Conectado. Escuchando "${env.rabbit.queue}" ligada a "${env.rabbit.exchange}" (fanout).`,
    );

    await this.channel.consume(env.rabbit.queue, (mensaje) => this._manejarMensaje(mensaje), {
      noAck: false,
    });
  }

  async _manejarMensaje(mensaje) {
    if (!mensaje) return; // el canal se canceló (p. ej. al cerrar el broker)

    let envelope;
    try {
      envelope = JSON.parse(mensaje.content.toString('utf8'));
    } catch (err) {
      console.error('[rabbit] Mensaje descartado: no es JSON válido.', err.message);
      this.channel.nack(mensaje, false, false); // a la DLQ, no tiene sentido reintentar
      return;
    }

    try {
      await this.notificacionService.procesarEvento(envelope, {
        tiposSoportados: env.tiposDeEventoSoportados,
      });
      this.channel.ack(mensaje);
    } catch (err) {
      if (err instanceof EventoInvalidoError) {
        console.error(`[rabbit] Evento inválido, se envía a la DLQ: ${err.message}`);
        this.channel.nack(mensaje, false, false);
        return;
      }

      // Falla transitoria (p. ej. Postgres caído): se reintenta más tarde.
      console.error('[rabbit] Error procesando el evento, se reencola:', err.message);
      this.channel.nack(mensaje, false, true);
    }
  }

  async cerrar() {
    this.cerrando = true;
    try {
      await this.channel?.close();
      await this.connection?.close();
    } catch {
      // Se está apagando el proceso; no hay nada más que hacer con este error.
    }
  }
}

module.exports = { RabbitConsumer };
