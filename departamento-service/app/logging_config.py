import json
import logging
import sys
from datetime import datetime, timezone

try:
    from opentelemetry import trace
except ImportError:  # Permite validar el formateador sin instalar el SDK completo.
    trace = None


class JsonTraceFormatter(logging.Formatter):
    """Serializa cada registro en una línea JSON y correlaciona la traza activa."""

    def format(self, record: logging.LogRecord) -> str:
        span_context = trace.get_current_span().get_span_context() if trace else None
        payload = {
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "level": record.levelname,
            "service": "departamento-service",
            "logger": record.name,
            "message": record.getMessage(),
            "traceId": format(span_context.trace_id, "032x") if span_context and span_context.is_valid else "",
            "spanId": format(span_context.span_id, "016x") if span_context and span_context.is_valid else "",
        }
        if record.exc_info:
            payload["exception"] = self.formatException(record.exc_info)
        return json.dumps(payload, ensure_ascii=False, separators=(",", ":"))


def configure_json_logging() -> None:
    handler = logging.StreamHandler(sys.stdout)
    handler.setFormatter(JsonTraceFormatter())
    root = logging.getLogger()
    root.handlers.clear()
    root.addHandler(handler)
    root.setLevel(logging.INFO)
