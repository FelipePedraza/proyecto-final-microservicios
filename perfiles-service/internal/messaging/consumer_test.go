package messaging

import (
	"context"
	"testing"

	"github.com/rabbitmq/amqp091-go"
	"go.opentelemetry.io/otel/baggage"
	"go.opentelemetry.io/otel/propagation"
	"go.opentelemetry.io/otel/trace"
)

func TestParseAcceptsPascalCase(t *testing.T) {
	event, err := parse([]byte(`{"Id":"evt-1","Type":"empleado.creado","Data":{"Id":"E001","Nombre":"Ana","Apellido":"Pérez","Email":"ana@example.com"}}`))
	if err != nil || event.ID != "evt-1" || event.Type != "empleado.creado" || text(event.Data, "Email", "email") != "ana@example.com" {
		t.Fatalf("unexpected parsed event: %#v, %v", event, err)
	}
}

func TestParseAcceptsCamelCase(t *testing.T) {
	event, err := parse([]byte(`{"id":"evt-2","type":"empleado.actualizado","data":{"empleadoId":"E002","nombre":"Luis","email":"luis@example.com"}}`))
	if err != nil || event.ID != "evt-2" || text(event.Data, "id", "empleadoId") != "E002" {
		t.Fatalf("unexpected parsed event: %#v, %v", event, err)
	}
}

func TestParseRejectsIncompleteEnvelope(t *testing.T) {
	if _, err := parse([]byte(`{"id":"evt-3","type":"empleado.creado","data":{}}`)); err == nil {
		t.Fatal("expected incomplete envelope error")
	}
}

func TestAMQPHeadersCarryW3CTraceAndBaggage(t *testing.T) {
	carrier := amqpHeaderCarrier(amqp091.Table{
		"TraceParent": []byte("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"),
		"baggage":     "tenant=universidad",
	})
	propagator := propagation.NewCompositeTextMapPropagator(
		propagation.TraceContext{},
		propagation.Baggage{},
	)

	ctx := propagator.Extract(context.Background(), carrier)
	spanContext := trace.SpanContextFromContext(ctx)
	if got := spanContext.TraceID().String(); got != "4bf92f3577b34da6a3ce929d0e0e4736" {
		t.Fatalf("unexpected trace ID: %s", got)
	}
	if got := baggage.FromContext(ctx).Member("tenant").Value(); got != "universidad" {
		t.Fatalf("unexpected baggage value: %s", got)
	}
}
