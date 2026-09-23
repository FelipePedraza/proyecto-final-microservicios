'use strict';

const { NotificacionService } = require('../src/domain/notificacionService');
const { EventoInvalidoError } = require('../src/domain/eventoParser');

/**
 * Repositorio falso en memoria: implementa el mismo contrato que
 * NotificacionRepository (ver src/domain/notificacionRepository.js) pero sin
 * Postgres, para probar la lógica de deduplicación de forma aislada y rápida.
 */
class FakeRepository {
  constructor() {
    this.filas = [];
  }

  async guardarSiEsNueva({ eventoId, tipoEvento, empleadoId, mensaje, payload }) {
    if (this.filas.some((f) => f.evento_id === eventoId)) {
      return null; // simula el ON CONFLICT DO NOTHING
    }
    const fila = {
      id: this.filas.length + 1,
      evento_id: eventoId,
      tipo_evento: tipoEvento,
      empleado_id: empleadoId,
      mensaje,
      payload,
      creado_en: new Date().toISOString(),
    };
    this.filas.push(fila);
    return fila;
  }

  async listarTodas() {
    return [...this.filas].sort((a, b) => b.id - a.id);
  }

  async listarPorEmpleado(empleadoId) {
    return this.filas.filter((f) => f.empleado_id === empleadoId).sort((a, b) => b.id - a.id);
  }
}

const TIPOS_SOPORTADOS = ['empleado.creado', 'vacaciones.programadas'];

function envelopeEmpleadoCreado(eventoId, empleadoId = 'E001') {
  return {
    Id: eventoId,
    Type: 'empleado.creado',
    Data: { Id: empleadoId, Nombre: 'Juan', Apellido: 'Pérez', Cargo: 'Dev' },
  };
}

describe('NotificacionService.procesarEvento', () => {
  test('guarda una notificación nueva y la deja consultable', async () => {
    const service = new NotificacionService(new FakeRepository());

    const guardada = await service.procesarEvento(envelopeEmpleadoCreado('evt-1'), {
      tiposSoportados: TIPOS_SOPORTADOS,
    });

    expect(guardada).not.toBeNull();
    expect(guardada.empleado_id).toBe('E001');
    const historial = await service.listarPorEmpleado('E001');
    expect(historial).toHaveLength(1);
  });

  test('deduplica: procesar el mismo eventoId dos veces solo guarda una fila', async () => {
    const service = new NotificacionService(new FakeRepository());
    const envelope = envelopeEmpleadoCreado('evt-repetido');

    const primera = await service.procesarEvento(envelope, { tiposSoportados: TIPOS_SOPORTADOS });
    const segunda = await service.procesarEvento(envelope, { tiposSoportados: TIPOS_SOPORTADOS });

    expect(primera).not.toBeNull();
    expect(segunda).toBeNull(); // la redelivery no crea una segunda fila
    const historial = await service.listarTodas();
    expect(historial).toHaveLength(1);
  });

  test('ignora (sin error) un tipo de evento que no está en la lista soportada', async () => {
    const service = new NotificacionService(new FakeRepository());
    const envelope = { Id: 'evt-2', Type: 'empleado.actualizado', Data: { Id: 'E001' } };

    const resultado = await service.procesarEvento(envelope, { tiposSoportados: TIPOS_SOPORTADOS });

    expect(resultado).toBeNull();
    expect(await service.listarTodas()).toHaveLength(0);
  });

  test('propaga EventoInvalidoError para un envelope sin los campos mínimos', async () => {
    const service = new NotificacionService(new FakeRepository());
    const envelopeIncompleto = { Type: 'empleado.creado' };

    await expect(
      service.procesarEvento(envelopeIncompleto, { tiposSoportados: TIPOS_SOPORTADOS }),
    ).rejects.toThrow(EventoInvalidoError);
  });

  test('dos empleados distintos quedan separados en listarPorEmpleado', async () => {
    const service = new NotificacionService(new FakeRepository());
    await service.procesarEvento(envelopeEmpleadoCreado('evt-a', 'E001'), { tiposSoportados: TIPOS_SOPORTADOS });
    await service.procesarEvento(envelopeEmpleadoCreado('evt-b', 'E002'), { tiposSoportados: TIPOS_SOPORTADOS });

    expect(await service.listarPorEmpleado('E001')).toHaveLength(1);
    expect(await service.listarPorEmpleado('E002')).toHaveLength(1);
    expect(await service.listarPorEmpleado('E999')).toHaveLength(0);
  });
});
