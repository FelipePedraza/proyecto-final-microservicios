package telemetry

import (
	"bytes"
	"context"
	"encoding/json"
	"log/slog"
	"testing"

	"go.opentelemetry.io/otel/trace"
)

func TestTraceHandlerAddsCorrelationToJSON(t *testing.T) {
	var output bytes.Buffer
	logger := slog.New(TraceHandler{Handler: slog.NewJSONHandler(&output, nil)}).
		With("service", "perfiles-service")
	spanContext := trace.NewSpanContext(trace.SpanContextConfig{
		TraceID: trace.TraceID{1},
		SpanID:  trace.SpanID{2},
	})
	ctx := trace.ContextWithSpanContext(context.Background(), spanContext)

	logger.InfoContext(ctx, "perfil actualizado")

	var value map[string]any
	if err := json.Unmarshal(output.Bytes(), &value); err != nil {
		t.Fatal(err)
	}
	if value["service"] != "perfiles-service" || value["traceId"] == "" || value["spanId"] == "" {
		t.Fatalf("missing structured correlation fields: %s", output.String())
	}
}
