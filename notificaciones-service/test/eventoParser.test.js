'use strict';

const { parsearEnvelope, construirMensaje, EventoInvalidoError } = require('../src/domain/eventoParser');

describe('parsearEnvelope', () => {
  test('extrae eventoId, tipoEvento y empleadoId de un envelope en PascalCase (formato real de empleados-service)', () => {
    const envelope = {
      Id: 'evt-1',
      Type: 'empleado.creado',
      Version: '1.0',
      OccurredAt: '2026-02-10T10:00:00Z',
      Producer: 'empleados-service',
      Data: { Id: 'E001', Nombre: 'Juan', Apellido: 'Pérez' },
    };

    const resultado = parsearEnvelope(envelope);

    expect(resultado).toEqual({
      eventoId: 'evt-1',
      tipoEvento: 'empleado.creado',
      empleadoId: 'E001',
      data: envelope.Data,
    });
  });

  test('tolera un envelope en camelCase', () => {
    const envelope = { id: 'evt-2', type: 'empleado.creado', data: { id: 'E002' } };

    const resultado = parsearEnvelope(envelope);

    expect(resultado.eventoId).toBe('evt-2');
    expect(resultado.empleadoId).toBe('E002');
  });

  test('lanza EventoInvalidoError si falta el id del evento', () => {
    const envelope = { Type: 'empleado.creado', Data: { Id: 'E001' } };

    expect(() => parsearEnvelope(envelope)).toThrow(EventoInvalidoError);
  });

  test('lanza EventoInvalidoError si falta el id del empleado dentro de Data', () => {
    const envelope = { Id: 'evt-3', Type: 'empleado.creado', Data: {} };

    expect(() => parsearEnvelope(envelope)).toThrow(EventoInvalidoError);
  });

  test('lanza EventoInvalidoError si Data no viene', () => {
    const envelope = { Id: 'evt-4', Type: 'empleado.creado' };

    expect(() => parsearEnvelope(envelope)).toThrow(EventoInvalidoError);
  });
});

describe('construirMensaje', () => {
  test('empleado.creado incluye nombre, apellido y cargo cuando están presentes', () => {
    const mensaje = construirMensaje('empleado.creado', { Nombre: 'Juan', Apellido: 'Pérez', Cargo: 'Dev' });

    expect(mensaje).toContain('Juan Pérez');
    expect(mensaje).toContain('Dev');
  });

  test('empleado.creado degrada con gracia si falta el nombre', () => {
    const mensaje = construirMensaje('empleado.creado', { Id: 'E001' });

    expect(mensaje).toContain('empleado E001');
  });

  test('empleado.retirado identifica al empleado y agrega la fecha de retiro', () => {
    const mensaje = construirMensaje('empleado.retirado', {
      Id: 'E001',
      Nombre: 'Juan',
      Apellido: 'Pérez',
      FechaRetiro: '2026-09-25T10:30:00Z',
    });

    expect(mensaje).toContain('desvinculación');
    expect(mensaje).toContain('Juan Pérez (empleado E001)');
    expect(mensaje).toContain('2026-09-25T10:30:00Z');
  });

  test('empleado.retirado funciona sin nombre ni fecha de retiro', () => {
    const mensaje = construirMensaje('empleado.retirado', { Id: 'E001' });

    expect(mensaje).toContain('empleado E001');
    expect(mensaje).not.toContain('undefined');
  });

  test('vacaciones.programadas incluye las fechas cuando están presentes', () => {
    const mensaje = construirMensaje('vacaciones.programadas', {
      EmpleadoId: 'E001',
      FechaInicio: '2026-03-01',
      FechaFin: '2026-03-10',
    });

    expect(mensaje).toContain('2026-03-01');
    expect(mensaje).toContain('2026-03-10');
  });

  test('vacaciones.programadas degrada con gracia si faltan las fechas', () => {
    const mensaje = construirMensaje('vacaciones.programadas', { EmpleadoId: 'E001' });

    expect(mensaje).toContain('E001');
    expect(mensaje).not.toContain('undefined');
  });
});
