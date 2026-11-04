package telemetry

import (
	"context"
	"log/slog"

	"go.opentelemetry.io/otel/trace"
)

// TraceHandler agrega los identificadores OTel al registro JSON cuando hay un span activo.
type TraceHandler struct{ slog.Handler }

func (handler TraceHandler) Handle(ctx context.Context, record slog.Record) error {
	spanContext := trace.SpanContextFromContext(ctx)
	if spanContext.IsValid() {
		record.AddAttrs(
			slog.String("traceId", spanContext.TraceID().String()),
			slog.String("spanId", spanContext.SpanID().String()),
		)
	} else {
		record.AddAttrs(slog.String("traceId", ""), slog.String("spanId", ""))
	}
	return handler.Handler.Handle(ctx, record)
}

func (handler TraceHandler) WithAttrs(attrs []slog.Attr) slog.Handler {
	return TraceHandler{Handler: handler.Handler.WithAttrs(attrs)}
}

func (handler TraceHandler) WithGroup(name string) slog.Handler {
	return TraceHandler{Handler: handler.Handler.WithGroup(name)}
}
