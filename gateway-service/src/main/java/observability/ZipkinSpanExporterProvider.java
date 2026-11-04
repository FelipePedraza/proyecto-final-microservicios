package observability;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.traces.ConfigurableSpanExporterProvider;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;

/**
 * Exportador HTTP JSON para la API Zipkin v2 exigida por el reto.
 *
 * OpenTelemetry retiró su exportador Zipkin oficial después de la versión 1.55.
 * Este proveedor conserva el nombre estándar "zipkin", usa solo APIs públicas
 * del SDK actual y evita mezclar artefactos binariamente incompatibles.
 */
public final class ZipkinSpanExporterProvider implements ConfigurableSpanExporterProvider {

    @Override
    public SpanExporter createExporter(ConfigProperties config) {
        String endpoint = config.getString(
                "otel.exporter.zipkin.endpoint",
                "http://zipkin:9411/api/v2/spans");
        return new ZipkinHttpSpanExporter(URI.create(endpoint));
    }

    @Override
    public String getName() {
        return "zipkin";
    }

    private static final class ZipkinHttpSpanExporter implements SpanExporter {
        private static final AttributeKey<String> SERVICE_NAME =
                AttributeKey.stringKey("service.name");

        private final HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        private final URI endpoint;

        private ZipkinHttpSpanExporter(URI endpoint) {
            this.endpoint = endpoint;
        }

        @Override
        public CompletableResultCode export(Collection<SpanData> spans) {
            if (spans.isEmpty()) {
                return CompletableResultCode.ofSuccess();
            }

            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(toJson(spans), StandardCharsets.UTF_8))
                    .build();

            CompletableResultCode result = new CompletableResultCode();
            client.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .whenComplete((response, error) -> {
                        if (error == null && response.statusCode() >= 200 && response.statusCode() < 300) {
                            result.succeed();
                        } else {
                            result.fail();
                        }
                    });
            return result;
        }

        @Override
        public CompletableResultCode flush() {
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode shutdown() {
            return CompletableResultCode.ofSuccess();
        }

        private static String toJson(Collection<SpanData> spans) {
            StringBuilder json = new StringBuilder("[");
            boolean first = true;
            for (SpanData span : spans) {
                if (!first) {
                    json.append(',');
                }
                first = false;
                appendSpan(json, span);
            }
            return json.append(']').toString();
        }

        private static void appendSpan(StringBuilder json, SpanData span) {
            json.append('{');
            field(json, "traceId", span.getTraceId());
            json.append(',');
            field(json, "id", span.getSpanId());

            if (span.getParentSpanContext().isValid()) {
                json.append(',');
                field(json, "parentId", span.getParentSpanId());
            }

            json.append(',');
            field(json, "name", span.getName());
            json.append(",\"timestamp\":").append(span.getStartEpochNanos() / 1_000);
            json.append(",\"duration\":")
                    .append(Math.max(1, (span.getEndEpochNanos() - span.getStartEpochNanos()) / 1_000));

            if (span.getKind() != SpanKind.INTERNAL) {
                json.append(',');
                field(json, "kind", span.getKind().name());
            }

            String serviceName = span.getResource().getAttribute(SERVICE_NAME);
            json.append(",\"localEndpoint\":{");
            field(json, "serviceName", serviceName == null ? "unknown-service" : serviceName);
            json.append('}');

            json.append(",\"tags\":{");
            boolean firstTag = true;
            for (Map.Entry<AttributeKey<?>, Object> entry : span.getAttributes().asMap().entrySet()) {
                if (!firstTag) {
                    json.append(',');
                }
                firstTag = false;
                field(json, entry.getKey().getKey(), String.valueOf(entry.getValue()));
            }
            if (span.getStatus().getStatusCode() == StatusCode.ERROR) {
                if (!firstTag) {
                    json.append(',');
                }
                field(json, "error", span.getStatus().getDescription().isBlank()
                        ? "true"
                        : span.getStatus().getDescription());
            }
            json.append("}}");
        }

        private static void field(StringBuilder json, String name, String value) {
            json.append('"').append(escape(name)).append("\":\"")
                    .append(escape(value)).append('"');
        }

        private static String escape(String value) {
            StringBuilder escaped = new StringBuilder(value.length() + 8);
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                switch (c) {
                    case '"' -> escaped.append("\\\"");
                    case '\\' -> escaped.append("\\\\");
                    case '\b' -> escaped.append("\\b");
                    case '\f' -> escaped.append("\\f");
                    case '\n' -> escaped.append("\\n");
                    case '\r' -> escaped.append("\\r");
                    case '\t' -> escaped.append("\\t");
                    default -> {
                        if (c < 0x20) {
                            escaped.append(String.format("\\u%04x", (int) c));
                        } else {
                            escaped.append(c);
                        }
                    }
                }
            }
            return escaped.toString();
        }
    }
}

