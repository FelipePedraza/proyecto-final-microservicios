'use strict';

const { formatLog } = require('../src/logging/jsonConsole');

test('formatea una línea JSON con servicio y campos de correlación', () => {
  const value = JSON.parse(formatLog('INFO', ['mensaje', { empleadoId: 'E001' }], new Date(0)));

  expect(value).toEqual({
    timestamp: '1970-01-01T00:00:00.000Z',
    level: 'INFO',
    service: 'notificaciones-service',
    message: 'mensaje {"empleadoId":"E001"}',
    traceId: '',
    spanId: '',
  });
});
