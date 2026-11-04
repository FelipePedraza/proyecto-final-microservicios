package observability;

import ch.qos.logback.classic.spi.ILoggingEvent;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer;

public final class TraceLoggingCustomizer
        implements StructuredLoggingJsonMembersCustomizer<ILoggingEvent> {

    @Override
    public void customize(JsonWriter.Members<ILoggingEvent> members) {
        members.add("traceId", () -> current().isValid() ? current().getTraceId() : "");
        members.add("spanId", () -> current().isValid() ? current().getSpanId() : "");
    }

    private static SpanContext current() {
        return Span.current().getSpanContext();
    }
}

