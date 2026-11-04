'use strict';

const {
  context,
  propagation,
  trace,
  SpanKind,
  SpanStatusCode,
} = require('@opentelemetry/api');

const tracer = trace.getTracer('notificaciones-service/rabbitmq');

const rabbitHeaderGetter = {
  keys(carrier) {
    return Object.keys(carrier ?? {});
  },
  get(carrier, requestedKey) {
    if (!carrier) return undefined;
    const actualKey = Object.keys(carrier).find(
      (key) => key.toLowerCase() === requestedKey.toLowerCase(),
    );
    if (!actualKey) return undefined;
    const value = carrier[actualKey];
    if (Buffer.isBuffer(value)) return value.toString('utf8');
    if (Array.isArray(value)) {
      return value.map((item) => (Buffer.isBuffer(item) ? item.toString('utf8') : String(item)));
    }
    return value == null ? undefined : String(value);
  },
};

function recordSpanError(span, error) {
  span.recordException(error);
  span.setStatus({ code: SpanStatusCode.ERROR, message: error.message });
}

async function withRabbitConsumerSpan(message, queue, callback) {
  const headers = message?.properties?.headers ?? {};
  const parentContext = propagation.extract(context.active(), headers, rabbitHeaderGetter);

  return context.with(parentContext, () => tracer.startActiveSpan(
    `${queue} process`,
    {
      kind: SpanKind.CONSUMER,
      attributes: {
        'messaging.system': 'rabbitmq',
        'messaging.destination.name': queue,
        'messaging.operation.name': 'process',
      },
    },
    async (span) => {
      try {
        return await callback(span);
      } catch (error) {
        recordSpanError(span, error);
        throw error;
      } finally {
        span.end();
      }
    },
  ));
}

module.exports = { rabbitHeaderGetter, recordSpanError, withRabbitConsumerSpan };
