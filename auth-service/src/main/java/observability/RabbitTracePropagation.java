package observability;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import org.springframework.amqp.core.MessageProperties;

import java.nio.charset.StandardCharsets;

public final class RabbitTracePropagation {

    private static final String INSTRUMENTATION_SCOPE = "microservicios.rabbitmq";

    private static final TextMapGetter<MessageProperties> GETTER =
            (carrier, key) -> {
                if (carrier == null) {
                    return null;
                }
                Object value = carrier.getHeaders().get(key);
                if (value instanceof byte[] bytes) {
                    return new String(bytes, StandardCharsets.UTF_8);
                }
                return value == null ? null : value.toString();
            };

    private RabbitTracePropagation() {
    }

    public static Context extract(MessageProperties properties) {
        return GlobalOpenTelemetry.getPropagators()
                .getTextMapPropagator()
                .extract(Context.current(), properties, GETTER);
    }

    public static void inject(Context context, MessageProperties properties) {
        GlobalOpenTelemetry.getPropagators()
                .getTextMapPropagator()
                .inject(context, properties, (carrier, key, value) -> {
                    if (carrier != null && key != null && value != null) {
                        carrier.setHeader(key, value);
                    }
                });
    }

    public static Span startProducer(Context parent, String exchange, String eventType) {
        Span span = GlobalOpenTelemetry.getTracer(INSTRUMENTATION_SCOPE)
                .spanBuilder(exchange + " publish")
                .setParent(parent)
                .setSpanKind(SpanKind.PRODUCER)
                .startSpan();
        addMessagingAttributes(span, exchange, "publish", eventType);
        return span;
    }

    public static Span startConsumer(Context parent, String queue, String eventType) {
        Span span = GlobalOpenTelemetry.getTracer(INSTRUMENTATION_SCOPE)
                .spanBuilder(queue + " process")
                .setParent(parent)
                .setSpanKind(SpanKind.CONSUMER)
                .startSpan();
        addMessagingAttributes(span, queue, "process", eventType);
        return span;
    }

    private static void addMessagingAttributes(
            Span span,
            String destination,
            String operation,
            String eventType
    ) {
        span.setAttribute("messaging.system", "rabbitmq");
        span.setAttribute("messaging.destination.name", destination);
        span.setAttribute("messaging.operation.name", operation);
        if (eventType != null) {
            span.setAttribute("messaging.message.type", eventType);
        }
    }
}
