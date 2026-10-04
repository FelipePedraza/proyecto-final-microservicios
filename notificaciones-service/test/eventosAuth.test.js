'use strict';

const { NotificacionService } = require('../src/domain/notificacionService');
const { parsearEnvelope, construirMensaje, ocultarTokens, tipoNotificacion } = require('../src/domain/eventoParser');
const { tiposDeEventoSoportados: TIPOS_SOPORTADOS } = require('../src/config/env');

class FakeRepository {
  constructor() {
    this.filas = [];
  }

  async guardarSiEsNueva(fila) {
    if (this.filas.some((f) => f.eventoId === fila.eventoId)) return null;
    this.filas.push(fila);
    return fila;
  }

  async buscarDestinatario() {
    return null;
  }
}

describe('eventos de auth-service en notificaciones', () => {
  let log;

  beforeEach(() => {
    log = jest.spyOn(console, 'log').mockImplementation(() => {});
  });

  afterEach(() => {
    log.mockRestore();
  });

  test('usuario.creado: log SEGURIDAD con email y token de activación', async () => {
    const service = new NotificacionService(new FakeRepository());

    await service.procesarEvento(
      {
        id: 'evt-1',
        type: 'usuario.creado',
        version: '1.0',
        data: { empleadoId: 'E001', email: 'juan@empresa.com', tokenActivacion: 'tok-abc', expiraEn: '2026-10-04T12:00:00Z' },
      },
      { tiposSoportados: TIPOS_SOPORTADOS },
    );

    expect(log).toHaveBeenCalledWith(
      expect.stringMatching(/^\[NOTIFICACIÓN\] Tipo: SEGURIDAD \| Para: juan@empresa\.com \| Mensaje: .*tok-abc/),
    );
  });

  test('usuario.recuperacion con version int y sin empleadoId: se procesa y el log lleva el token', async () => {
    const repo = new FakeRepository();
    const service = new NotificacionService(repo);

    const guardada = await service.procesarEvento(
      {
        id: 'evt-2',
        type: 'usuario.recuperacion',
        version: 1,
        data: { email: 'ana@empresa.com', tokenRecuperacion: 'tok-rec', expiraEn: '2026-10-04T12:15:00Z' },
      },
      { tiposSoportados: TIPOS_SOPORTADOS },
    );

    expect(guardada).not.toBeNull();
    expect(guardada.empleadoId).toBe('ana@empresa.com');
    expect(log).toHaveBeenCalledWith(
      expect.stringMatching(/Tipo: SEGURIDAD \| Para: ana@empresa\.com \| Mensaje: .*tok-rec/),
    );
  });

  test('el token no se guarda en el historial (ni en el mensaje ni en el payload)', async () => {
    const repo = new FakeRepository();
    const service = new NotificacionService(repo);

    await service.procesarEvento(
      { id: 'evt-3', type: 'usuario.creado', data: { empleadoId: 'E001', email: 'a@b.co', tokenActivacion: 'tok-secreto' } },
      { tiposSoportados: TIPOS_SOPORTADOS },
    );

    const guardada = repo.filas[0];
    expect(JSON.stringify(guardada)).not.toContain('tok-secreto');
    expect(guardada.payload.data.tokenActivacion).toBe('[OCULTO]');
  });

  test('cuenta.activada distingue ACTIVACION_INICIAL de FIN_VACACIONES', () => {
    const inicial = construirMensaje('cuenta.activada', { motivo: 'ACTIVACION_INICIAL' });
    const fin = construirMensaje('cuenta.activada', { motivo: 'FIN_VACACIONES' });

    expect(inicial).toContain('activada correctamente');
    expect(fin).toContain('vacaciones terminaron');
    expect(inicial).not.toEqual(fin);
  });

  test('cuenta.desactivada distingue VACACIONES de RETIRO', () => {
    const vacaciones = construirMensaje('cuenta.desactivada', { motivo: 'VACACIONES', permanente: false });
    const retiro = construirMensaje('cuenta.desactivada', { motivo: 'RETIRO', permanente: true });

    expect(vacaciones).toContain('suspendida temporalmente');
    expect(retiro).toContain('desactivada permanentemente');
  });

  test('cuenta.activada y cuenta.desactivada se registran con Tipo: CUENTA', async () => {
    const service = new NotificacionService(new FakeRepository());

    await service.procesarEvento(
      { id: 'evt-4', type: 'cuenta.desactivada', version: 1, data: { empleadoId: 'E001', email: 'juan@empresa.com', motivo: 'RETIRO', permanente: true } },
      { tiposSoportados: TIPOS_SOPORTADOS },
    );

    expect(log).toHaveBeenCalledWith(
      expect.stringMatching(/Tipo: CUENTA \| Para: juan@empresa\.com \| Mensaje: .*retiro/),
    );
  });

  test('el parser acepta version como int y como string', () => {
    const conInt = parsearEnvelope({ id: 'a', type: 'cuenta.activada', version: 1, data: { empleadoId: 'E1' } });
    const conString = parsearEnvelope({ id: 'b', type: 'cuenta.activada', version: '1.0', data: { empleadoId: 'E1' } });
    const sinVersion = parsearEnvelope({ id: 'c', type: 'cuenta.activada', data: { empleadoId: 'E1' } });

    expect(conInt.version).toBe('1');
    expect(conString.version).toBe('1.0');
    expect(sinVersion.version).toBeNull();
  });

  test('ocultarTokens no modifica el envelope original', () => {
    const original = { data: { tokenActivacion: 'x', email: 'a@b.co' } };

    const copia = ocultarTokens(original);

    expect(original.data.tokenActivacion).toBe('x');
    expect(copia.data.tokenActivacion).toBe('[OCULTO]');
    expect(copia.data.email).toBe('a@b.co');
  });

  test('al retirar, DESVINCULACION (Reto 4) y cuenta.desactivada RETIRO dicen cosas claramente distintas', () => {
    const desvinculacion = construirMensaje('empleado.retirado', {
      Id: 'E001',
      Nombre: 'Juan',
      Apellido: 'Pérez',
      FechaRetiro: '2026-10-04T10:00:00Z',
    });
    const cuenta = construirMensaje('cuenta.desactivada', { motivo: 'RETIRO', permanente: true });

    expect(desvinculacion).toContain('desvinculación');
    expect(cuenta).toContain('cuenta');
    expect(cuenta).not.toContain('desvinculación');
    expect(desvinculacion).not.toContain('cuenta');
    expect(tipoNotificacion('empleado.retirado')).toBe('DESVINCULACION');
    expect(tipoNotificacion('cuenta.desactivada')).toBe('CUENTA');
  });
});
