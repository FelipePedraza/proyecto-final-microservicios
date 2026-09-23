'use strict';

/**
 * Traduce un EventEnvelope crudo (JSON publicado por empleados-service, ver
 * Reto-1/RegistroService/RegistroService/Infrastructure/Messaging/EventEnvelope.cs)
 * en los datos que este servicio necesita para guardar una notificación.
 *
 * Contrato observado en el código fuente de empleados-service (no hay un catálogo de
 * eventos aparte, así que esto es la fuente de verdad):
 *
 *   {
 *     "Id": "<guid>",              // id del EVENTO -> clave de deduplicación
 *     "Type": "empleado.creado",
 *     "Version": "1.0",
 *     "OccurredAt": "2026-...",
 *     "Producer": "empleados-service",
 *     "Data": { "Id": "E001", "Nombre": "Juan", "Apellido": "Pérez", ... }
 *   }
 *
 * `JsonSerializer.Serialize(envelope)` se llama en el productor SIN opciones
 * personalizadas, así que las claves salen en PascalCase (el nombre exacto de la
 * propiedad de C#), no en camelCase. Aun así, `campo()` abajo acepta ambas formas:
 * es más barato tolerar las dos que depender de un detalle de serialización de otro
 * servicio que no controlamos y que podría cambiar.
 */

/** Busca un campo probando varias formas de escribirlo (PascalCase, camelCase, ...). */
function campo(objeto, ...nombres) {
  if (!objeto || typeof objeto !== 'object') return undefined;
  for (const nombre of nombres) {
    if (objeto[nombre] !== undefined && objeto[nombre] !== null) {
      return objeto[nombre];
    }
  }
  return undefined;
}

class EventoInvalidoError extends Error {}

/**
 * Valida y normaliza el envelope. Lanza EventoInvalidoError si falta algo esencial
 * (id del evento, tipo, o el id del empleado dentro de `Data`): esos mensajes van a la
 * cola de mensajes muertos en vez de reintentarse indefinidamente (ver rabbitConsumer.js).
 */
function parsearEnvelope(envelopeCrudo) {
  const eventoId = campo(envelopeCrudo, 'Id', 'id');
  const tipoEvento = campo(envelopeCrudo, 'Type', 'type');
  const data = campo(envelopeCrudo, 'Data', 'data') || {};
  const empleadoId = campo(data, 'Id', 'id', 'EmpleadoId', 'empleadoId');

  if (!eventoId || !tipoEvento || !empleadoId) {
    throw new EventoInvalidoError(
      `Envelope incompleto: faltan campos obligatorios (eventoId=${eventoId}, ` +
        `tipoEvento=${tipoEvento}, empleadoId=${empleadoId}).`,
    );
  }

  return { eventoId: String(eventoId), tipoEvento: String(tipoEvento), empleadoId: String(empleadoId), data };
}

/**
 * Construye el texto de la notificación simulada según el tipo de evento.
 * Es deliberadamente texto plano: el reto pide "generar logs simulando
 * notificaciones", no integrar un proveedor real de email/SMS.
 */
function construirMensaje(tipoEvento, data) {
  switch (tipoEvento) {
    case 'empleado.creado': {
      const nombre = campo(data, 'Nombre', 'nombre') ?? '';
      const apellido = campo(data, 'Apellido', 'apellido') ?? '';
      const cargo = campo(data, 'Cargo', 'cargo');
      const nombreCompleto = `${nombre} ${apellido}`.trim() || `empleado ${campo(data, 'Id', 'id')}`;
      return cargo
        ? `Notificación de bienvenida enviada a ${nombreCompleto} (cargo: ${cargo}).`
        : `Notificación de bienvenida enviada a ${nombreCompleto}.`;
    }

    case 'vacaciones.programadas': {
      // No hay todavía un productor de este evento en el repositorio (lo publicará otro
      // integrante). Se acepta cualquier forma razonable de fechas y se degrada con
      // gracia si no vienen, en vez de rechazar el evento.
      const desde = campo(data, 'FechaInicio', 'fechaInicio', 'Desde', 'desde');
      const hasta = campo(data, 'FechaFin', 'fechaFin', 'Hasta', 'hasta');
      const empleadoId = campo(data, 'Id', 'id', 'EmpleadoId', 'empleadoId');
      return desde && hasta
        ? `Notificación de vacaciones programadas enviada al empleado ${empleadoId}: del ${desde} al ${hasta}.`
        : `Notificación de vacaciones programadas enviada al empleado ${empleadoId}.`;
    }

    default:
      // No debería llegar aquí: rabbitConsumer.js filtra por tipo soportado antes de
      // llamar al servicio. Se deja como red de seguridad.
      return `Notificación genérica enviada (evento ${tipoEvento}).`;
  }
}

module.exports = { parsearEnvelope, construirMensaje, EventoInvalidoError };
