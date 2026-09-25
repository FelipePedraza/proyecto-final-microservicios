'use strict';

const request = require('supertest');
const { crearApp } = require('../src/app');

/**
 * Servicio de notificaciones falso: implementa solo lo que el router usa
 * (listarTodas / listarPorEmpleado), con datos fijos, para probar la capa HTTP
 * sin Postgres ni RabbitMQ.
 */
function crearNotificacionServiceFalso() {
  const filas = [
    {
      id: 2,
      evento_id: 'evt-2',
      tipo_evento: 'empleado.retirado',
      empleado_id: 'E002',
      mensaje: 'Notificación de desvinculación registrada para Ana Gómez.',
      payload: { Id: 'evt-2', Type: 'empleado.retirado', Data: { Id: 'E002', Email: 'ana@empresa.com' } },
      destinatario: 'ana@empresa.com',
      creado_en: '2026-02-11T10:00:00.000Z',
    },
    {
      id: 1,
      evento_id: 'evt-1',
      tipo_evento: 'empleado.creado',
      empleado_id: 'E001',
      mensaje: 'Notificación de bienvenida enviada a Juan Pérez.',
      payload: { Id: 'evt-1', Type: 'empleado.creado', Data: { Id: 'E001', Email: 'juan@empresa.com' } },
      destinatario: 'juan@empresa.com',
      creado_en: '2026-02-10T10:00:00.000Z',
    },
  ];

  return {
    async listarTodas() {
      return filas;
    },
    async listarPorEmpleado(empleadoId) {
      return filas.filter((f) => f.empleado_id === empleadoId);
    },
  };
}

describe('API HTTP de notificaciones-service', () => {
  const app = crearApp({
    notificacionService: crearNotificacionServiceFalso(),
    estaListaLaBaseDeDatos: async () => true,
  });

  test('GET /health responde 200 sin depender de nada externo', async () => {
    const respuesta = await request(app).get('/health');

    expect(respuesta.status).toBe(200);
    expect(respuesta.body).toEqual({ status: 'healthy' });
  });

  test('GET /health/ready responde 200 cuando la base está arriba', async () => {
    const respuesta = await request(app).get('/health/ready');

    expect(respuesta.status).toBe(200);
    expect(respuesta.body).toEqual({ status: 'ready', database: 'up' });
  });

  test('GET /health/ready responde 503 cuando la base no responde', async () => {
    const appConDbCaida = crearApp({
      notificacionService: crearNotificacionServiceFalso(),
      estaListaLaBaseDeDatos: async () => false,
    });

    const respuesta = await request(appConDbCaida).get('/health/ready');

    expect(respuesta.status).toBe(503);
    expect(respuesta.body.status).toBe('not_ready');
  });

  test('GET /notificaciones devuelve el historial con la estructura del reto', async () => {
    const respuesta = await request(app).get('/notificaciones');

    expect(respuesta.status).toBe(200);
    expect(respuesta.body).toHaveLength(2);
    expect(respuesta.body[0]).toEqual({
      id: '2',
      tipo: 'DESVINCULACION',
      destinatario: 'ana@empresa.com',
      mensaje: 'Notificación de desvinculación registrada para Ana Gómez.',
      fechaEnvio: '2026-02-11T10:00:00.000Z',
      empleadoId: 'E002',
    });
    expect(respuesta.body[1].tipo).toBe('BIENVENIDA');
  });

  test('GET /notificaciones/:empleadoId filtra por empleado', async () => {
    const respuesta = await request(app).get('/notificaciones/E001');

    expect(respuesta.status).toBe(200);
    expect(respuesta.body).toHaveLength(1);
    expect(respuesta.body[0].empleadoId).toBe('E001');
  });

  test('GET /notificaciones/:empleadoId devuelve 200 con lista vacía si el empleado no tiene notificaciones', async () => {
    const respuesta = await request(app).get('/notificaciones/NO-EXISTE');

    expect(respuesta.status).toBe(200);
    expect(respuesta.body).toEqual([]);
  });

  test('una ruta no soportada responde 404 con el mismo formato que el resto del proyecto', async () => {
    const respuesta = await request(app).get('/otra-ruta');

    expect(respuesta.status).toBe(404);
    expect(respuesta.body).toEqual({ error: 'Recurso no encontrado' });
  });

  test('GET /docs sirve la documentación de Swagger', async () => {
    const respuesta = await request(app).get('/docs/');

    expect(respuesta.status).toBe(200);
    expect(respuesta.text).toContain('swagger');
  });
});
