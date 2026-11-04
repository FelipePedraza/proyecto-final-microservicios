'use strict';

const { context, trace } = require('@opentelemetry/api');

function serializeArgument(value) {
  if (value instanceof Error) return { message: value.message, stack: value.stack };
  if (typeof value === 'string') return value;
  try {
    return JSON.parse(JSON.stringify(value));
  } catch {
    return String(value);
  }
}

function formatLog(level, args, now = new Date()) {
  const spanContext = trace.getSpan(context.active())?.spanContext();
  return JSON.stringify({
    timestamp: now.toISOString(),
    level,
    service: 'notificaciones-service',
    message: args.map((item) => (typeof item === 'string' ? item : JSON.stringify(serializeArgument(item)))).join(' '),
    traceId: spanContext?.traceId ?? '',
    spanId: spanContext?.spanId ?? '',
  });
}

function installJsonConsole() {
  if (console.__jsonLoggingInstalled) return;
  const output = {
    log: console.log.bind(console),
    info: console.info.bind(console),
    warn: console.warn.bind(console),
    error: console.error.bind(console),
  };
  console.log = (...args) => output.log(formatLog('INFO', args));
  console.info = (...args) => output.info(formatLog('INFO', args));
  console.warn = (...args) => output.warn(formatLog('WARN', args));
  console.error = (...args) => output.error(formatLog('ERROR', args));
  Object.defineProperty(console, '__jsonLoggingInstalled', { value: true });
}

module.exports = { formatLog, installJsonConsole };
