import json
import logging

from app.logging_config import JsonTraceFormatter


def test_json_formatter_emite_contrato_y_trace_vacio_sin_span():
    record = logging.LogRecord(
        name="test", level=logging.INFO, pathname=__file__, lineno=1,
        msg="mensaje de prueba", args=(), exc_info=None,
    )

    value = json.loads(JsonTraceFormatter().format(record))

    assert value["level"] == "INFO"
    assert value["service"] == "departamento-service"
    assert value["message"] == "mensaje de prueba"
    assert value["traceId"] == ""
    assert value["spanId"] == ""
