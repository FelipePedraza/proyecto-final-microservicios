'use strict';

const { rabbitHeaderGetter, withRabbitConsumerSpan } = require('../src/telemetry/rabbitTracing');

describe('RabbitMQ trace propagation', () => {
  test('reads W3C headers case-insensitively from AMQP buffers', () => {
    const headers = {
      TraceParent: Buffer.from('00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01'),
      baggage: Buffer.from('tenant=universidad'),
    };

    expect(rabbitHeaderGetter.get(headers, 'traceparent')).toBe(
      '00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01',
    );
    expect(rabbitHeaderGetter.get(headers, 'BAGGAGE')).toBe('tenant=universidad');
  });

  test('executes message processing inside a consumer span wrapper', async () => {
    const callback = jest.fn().mockResolvedValue('ok');

    await expect(withRabbitConsumerSpan(
      { properties: { headers: {} } },
      'notificaciones.empleados',
      callback,
    )).resolves.toBe('ok');
    expect(callback).toHaveBeenCalledTimes(1);
  });
});
