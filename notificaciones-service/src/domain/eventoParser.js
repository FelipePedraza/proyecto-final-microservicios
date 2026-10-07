'use strict';

/**
 * Traduce un EventEnvelope crudo (JSON publicado por empleados-service, ver
 * empleado-service/empleadoService/infrastructure/messaging/EventEnvelope.cs)
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

const TIPOS_NOTIFICACION = Object.freeze({
  'empleado.creado': 'BIENVENIDA',
  'empleado.retirado': 'DESVINCULACION',
  'vacaciones.programadas': 'VACACIONES',
  'usuario.creado': 'SEGURIDAD',
  'usuario.recuperacion': 'SEGURIDAD',
  'cuenta.activada': 'CUENTA',
  'cuenta.desactivada': 'CUENTA',
});

/** Eventos publicados por auth-service en `auth_exchange`. */
const EVENTOS_AUTH = Object.freeze([
  'usuario.creado',
  'usuario.recuperacion',
  'cuenta.activada',
  'cuenta.desactivada',
]);

/** Campos del payload que contienen tokens (secretos de un solo uso). */
const CAMPOS_TOKEN = Object.freeze(['tokenActivacion', 'tokenRecuperacion', 'token']);

function tipoNotificacion(tipoEvento) {
  return TIPOS_NOTIFICACION[tipoEvento] ?? tipoEvento;
}

function obtenerDestinatario(data) {
  const email = campo(data, 'Email', 'email');
  return typeof email === 'string' && email.trim() ? email.trim() : null;
}

/**
 * Valida y normaliza el envelope. Lanza EventoInvalidoError si falta algo esencial
 * (id del evento, tipo, o el id del empleado dentro de `Data`): esos mensajes van a la
 * cola de mensajes muertos en vez de reintentarse indefinidamente (ver rabbitConsumer.js).
 */
function parsearEnvelope(envelopeCrudo) {
  const eventoId = campo(envelopeCrudo, 'Id', 'id');
  const tipoEvento = campo(envelopeCrudo, 'Type', 'type');
  const data = campo(envelopeCrudo, 'Data', 'data') || {};
  let empleadoId = campo(data, 'Id', 'id', 'EmpleadoId', 'empleadoId');

  // usuario.recuperacion (auth-service) solo trae email, sin empleadoId: se usa el email
  // como identificador del historial para no mandar el evento a la DLQ.
  if (!empleadoId && EVENTOS_AUTH.includes(tipoEvento)) {
    empleadoId = campo(data, 'Email', 'email');
  }

  if (!eventoId || !tipoEvento || !empleadoId) {
    throw new EventoInvalidoError(
      `Envelope incompleto: faltan campos obligatorios (eventoId=${eventoId}, ` +
        `tipoEvento=${tipoEvento}, empleadoId=${empleadoId}).`,
    );
  }

  return {
    eventoId: String(eventoId),
    tipoEvento: String(tipoEvento),
    empleadoId: String(empleadoId),
    version: normalizarVersion(campo(envelopeCrudo, 'Version', 'version')),
    data,
  };
}

/**
 * `version` llega como int en los eventos de auth-service (ej. 1) y como string en el resto
 * ("1.0"). Se acepta cualquiera de las dos formas y se normaliza a string (o null si falta).
 */
function normalizarVersion(version) {
  if (version === undefined || version === null || version === '') return null;
  return String(version);
}

/** Copia de un valor JSON con los campos de token reemplazados por "[OCULTO]" (para persistir). */
function ocultarTokens(valor) {
  if (Array.isArray(valor)) return valor.map(ocultarTokens);
  if (valor && typeof valor === 'object') {
    return Object.fromEntries(
      Object.entries(valor).map(([clave, contenido]) => [
        clave,
        CAMPOS_TOKEN.includes(clave) ? '[OCULTO]' : ocultarTokens(contenido),
      ]),
    );
  }
  return valor;
}

/**
 * Construye el texto de la notificación simulada según el tipo de evento.
 * Es deliberadamente texto plano: el reto pide "generar logs simulando
 * notificaciones", no integrar un proveedor real de email/SMS.
 */
function construirMensaje(tipoEvento, data, { incluirToken = true } = {}) {
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

    case 'empleado.retirado': {
      const empleadoId = campo(data, 'Id', 'id', 'EmpleadoId', 'empleadoId');
      const nombreCompleto = `${campo(data, 'Nombre', 'nombre') ?? ''} ${campo(data, 'Apellido', 'apellido') ?? ''}`.trim();
      const empleado = nombreCompleto ? `${nombreCompleto} (empleado ${empleadoId})` : `empleado ${empleadoId}`;
      const fechaRetiro = campo(data, 'FechaRetiro', 'fechaRetiro');
      return fechaRetiro
        ? `Notificación de desvinculación registrada para ${empleado}, con fecha de retiro ${fechaRetiro}.`
        : `Notificación de desvinculación registrada para ${empleado}.`;
    }

    case 'vacaciones.programadas': {
      // El productor publica empleadoId, fechaInicio y fechaFin; se toleran también
      // variantes PascalCase y se degrada con gracia si falta alguna fecha.
      const desde = campo(data, 'FechaInicio', 'fechaInicio', 'Desde', 'desde');
      const hasta = campo(data, 'FechaFin', 'fechaFin', 'Hasta', 'hasta');
      const empleadoId = campo(data, 'Id', 'id', 'EmpleadoId', 'empleadoId');
      return desde && hasta
        ? `Notificación de vacaciones programadas enviada al empleado ${empleadoId}: del ${desde} al ${hasta}.`
        : `Notificación de vacaciones programadas enviada al empleado ${empleadoId}.`;
    }

    case 'usuario.creado': {
      const token = incluirToken ? campo(data, 'tokenActivacion', 'TokenActivacion', 'token') : '[OCULTO]';
      const expira = campo(data, 'expiraEn', 'ExpiraEn');
      return (
        `Bienvenido: tu cuenta fue creada. Activa tu cuenta y define tu contraseña con este token: ${token}` +
        (expira ? ` (válido hasta ${expira}).` : '.')
      );
    }

    case 'usuario.recuperacion': {
      const token = incluirToken ? campo(data, 'tokenRecuperacion', 'TokenRecuperacion', 'token') : '[OCULTO]';
      const expira = campo(data, 'expiraEn', 'ExpiraEn');
      return (
        `Solicitud de recuperación de contraseña. Usa este token para restablecerla: ${token}` +
        (expira ? ` (expira ${expira}).` : '.')
      );
    }

    case 'cuenta.activada': {
      const motivo = campo(data, 'motivo', 'Motivo');
      if (motivo === 'ACTIVACION_INICIAL') {
        return 'Tu cuenta fue activada correctamente. Ya puedes iniciar sesión.';
      }
      if (motivo === 'FIN_VACACIONES') {
        return 'Tus vacaciones terminaron: tu cuenta fue reactivada y ya puedes volver a iniciar sesión.';
      }
      return `Tu cuenta fue activada${motivo ? ` (motivo: ${motivo})` : ''}.`;
    }

    case 'cuenta.desactivada': {
      const motivo = campo(data, 'motivo', 'Motivo');
      if (motivo === 'VACACIONES') {
        return 'Tu cuenta fue suspendida temporalmente mientras estás de vacaciones. Se reactivará cuando terminen.';
      }
      if (motivo === 'RETIRO') {
        return 'Tu cuenta fue desactivada permanentemente por tu retiro de la empresa.';
      }
      const permanente = campo(data, 'permanente', 'Permanente') === true;
      return `Tu cuenta fue ${permanente ? 'desactivada permanentemente' : 'suspendida temporalmente'}${
        motivo ? ` (motivo: ${motivo})` : ''
      }.`;
    }

    default:
      // No debería llegar aquí: rabbitConsumer.js filtra por tipo soportado antes de
      // llamar al servicio. Se deja como red de seguridad.
      return `Notificación genérica enviada (evento ${tipoEvento}).`;
  }
}

module.exports = {
  parsearEnvelope,
  ocultarTokens,
  normalizarVersion,
  EVENTOS_AUTH,
  construirMensaje,
  tipoNotificacion,
  obtenerDestinatario,
  EventoInvalidoError,
};
