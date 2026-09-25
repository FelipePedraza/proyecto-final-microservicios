'use strict';

const {
  parsearEnvelope,
  construirMensaje,
  tipoNotificacion,
  obtenerDestinatario,
} = require('./eventoParser');

/**
 * Orquesta el caso de uso "procesar un evento entrante": lo valida, decide si ya se
 * procesó antes (deduplicación), simula el envío (log) y guarda el historial.
 *
 * Vive separado del transporte (RabbitMQ) y de la persistencia concreta (Postgres)
 * a propósito: recibe el `repository` por inyección, así que se puede probar con un
 * repositorio falso en memoria (ver test/notificacionService.test.js) sin Postgres.
 */
class NotificacionService {
  constructor(repository) {
    this.repository = repository;
  }

  /**
   * Procesa un envelope crudo (ya parseado de JSON, tal como llega del mensaje de
   * RabbitMQ). Devuelve la fila guardada, o `null` si el evento ya se había procesado
   * (duplicado) o si su tipo no es uno de los que este servicio maneja.
   *
   * Lanza `EventoInvalidoError` (ver eventoParser.js) si el envelope no trae los
   * campos mínimos: quien llama decide qué hacer con un evento mal formado
   * (rabbitConsumer.js lo manda a la cola de mensajes muertos).
   */
  async procesarEvento(envelopeCrudo, { tiposSoportados }) {
    const { eventoId, tipoEvento, empleadoId, data } = parsearEnvelope(envelopeCrudo);

    if (!tiposSoportados.includes(tipoEvento)) {
      // Llega por el exchange fanout (compartido con empleado.actualizado y otros
      // eventos) pero no es un tipo que a este servicio le interese.
      return null;
    }

    const mensaje = construirMensaje(tipoEvento, data);
    const destinatario =
      obtenerDestinatario(data) ??
      (await this.repository.buscarDestinatario(empleadoId)) ??
      'no informado';

    const guardada = await this.repository.guardarSiEsNueva({
      eventoId,
      tipoEvento,
      empleadoId,
      mensaje,
      payload: envelopeCrudo,
    });

    if (guardada) {
      console.log(
        `[NOTIFICACIÓN] Tipo: ${tipoNotificacion(tipoEvento)} | Para: ${destinatario} | Mensaje: "${mensaje}"`,
      );
    } else {
      console.log(
        `[notificacion] Evento ${eventoId} (${tipoEvento}) ya se había procesado antes; se ignora (deduplicación).`,
      );
    }

    return guardada;
  }

  async listarTodas(opciones) {
    return this.repository.listarTodas(opciones);
  }

  async listarPorEmpleado(empleadoId, opciones) {
    return this.repository.listarPorEmpleado(empleadoId, opciones);
  }
}

module.exports = { NotificacionService };
