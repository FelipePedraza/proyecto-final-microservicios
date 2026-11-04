package messaging

import (
	"context"
	"encoding/json"
	"log/slog"
	"strings"
	"time"

	"github.com/FelipePedraza/proyecto-final-microservicios/perfiles-service/internal/config"
	"github.com/FelipePedraza/proyecto-final-microservicios/perfiles-service/internal/repository"
	"github.com/rabbitmq/amqp091-go"
	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/codes"
	"go.opentelemetry.io/otel/propagation"
	"go.opentelemetry.io/otel/trace"
)

type raw map[string]any

type Envelope struct {
	ID   string
	Type string
	Data raw
}

type amqpHeaderCarrier amqp091.Table

func (carrier amqpHeaderCarrier) Get(key string) string {
	for header, value := range carrier {
		if !strings.EqualFold(header, key) {
			continue
		}
		switch typed := value.(type) {
		case string:
			return typed
		case []byte:
			return string(typed)
		default:
			return ""
		}
	}
	return ""
}

func (carrier amqpHeaderCarrier) Set(key, value string) { carrier[key] = value }

func (carrier amqpHeaderCarrier) Keys() []string {
	keys := make([]string, 0, len(carrier))
	for key := range carrier {
		keys = append(keys, key)
	}
	return keys
}

var _ propagation.TextMapCarrier = amqpHeaderCarrier{}

func field(m raw, names ...string) any {
	for _, name := range names {
		if value, ok := m[name]; ok && value != nil {
			return value
		}
	}
	return nil
}

func parse(body []byte) (Envelope, error) {
	var root raw
	if err := json.Unmarshal(body, &root); err != nil {
		return Envelope{}, err
	}
	id, _ := field(root, "id", "Id").(string)
	typ, _ := field(root, "type", "Type").(string)
	data, _ := field(root, "data", "Data").(map[string]any)
	if data == nil {
		data = raw{}
	}
	employee, _ := field(data, "id", "Id", "empleadoId", "EmpleadoId").(string)
	if id == "" || typ == "" || employee == "" {
		return Envelope{}, &invalidEnvelope{}
	}
	return Envelope{ID: id, Type: typ, Data: data}, nil
}

type invalidEnvelope struct{}

func (*invalidEnvelope) Error() string { return "envelope inválido" }

func text(data raw, names ...string) string {
	value := field(data, names...)
	result, _ := value.(string)
	return result
}

type Consumer struct {
	cfg    config.Config
	repo   *repository.Repository
	logger *slog.Logger
	health *BrokerHealth
}

func NewConsumer(c config.Config, r *repository.Repository, l *slog.Logger, health *BrokerHealth) *Consumer {
	return &Consumer{cfg: c, repo: r, logger: l, health: health}
}

func (c *Consumer) Run(ctx context.Context) {
	for ctx.Err() == nil {
		if err := c.consume(ctx); err != nil {
			c.logger.Error("rabbit connection failed", "error", err)
			select {
			case <-ctx.Done():
				return
			case <-time.After(3 * time.Second):
			}
		}
	}
}

func (c *Consumer) consume(ctx context.Context) error {
	c.health.SetConnected(false)
	conn, err := amqp091.Dial("amqp://" + c.cfg.RabbitUser + ":" + c.cfg.RabbitPassword + "@" + c.cfg.RabbitHost + ":" + c.cfg.RabbitPort)
	if err != nil {
		return err
	}
	defer conn.Close()
	defer c.health.SetConnected(false)
	ch, err := conn.Channel()
	if err != nil {
		return err
	}
	defer ch.Close()
	if err = ch.ExchangeDeclare(c.cfg.RabbitExchange, "fanout", true, false, false, false, nil); err != nil {
		return err
	}
	dlq := c.cfg.RabbitQueue + ".dlq"
	if _, err = ch.QueueDeclare(dlq, true, false, false, false, nil); err != nil {
		return err
	}
	q, err := ch.QueueDeclare(c.cfg.RabbitQueue, true, false, false, false, amqp091.Table{
		"x-dead-letter-exchange": "", "x-dead-letter-routing-key": dlq,
	})
	if err != nil {
		return err
	}
	if err = ch.QueueBind(q.Name, "", c.cfg.RabbitExchange, false, nil); err != nil {
		return err
	}
	if err = ch.Qos(1, 0, false); err != nil {
		return err
	}
	msgs, err := ch.Consume(q.Name, "", false, false, false, false, nil)
	if err != nil {
		return err
	}
	c.logger.Info("rabbit consumer ready", "queue", q.Name)
	c.health.SetConnected(true)
	for {
		select {
		case <-ctx.Done():
			return nil
		case msg, ok := <-msgs:
			if !ok {
				return context.Canceled
			}
			c.handle(ctx, ch, msg)
		}
	}
}

func (c *Consumer) handle(ctx context.Context, ch *amqp091.Channel, msg amqp091.Delivery) {
	parent := otel.GetTextMapPropagator().Extract(ctx, amqpHeaderCarrier(msg.Headers))
	messageContext, span := otel.Tracer("perfiles-service/rabbitmq").Start(
		parent,
		c.cfg.RabbitQueue+" process",
		trace.WithSpanKind(trace.SpanKindConsumer),
		trace.WithAttributes(
			attribute.String("messaging.system", "rabbitmq"),
			attribute.String("messaging.destination.name", c.cfg.RabbitQueue),
			attribute.String("messaging.operation.name", "process"),
		),
	)
	defer span.End()

	event, err := parse(msg.Body)
	if err != nil {
		span.RecordError(err)
		span.SetStatus(codes.Error, err.Error())
		c.logger.WarnContext(messageContext, "event discarded", "error", err)
		_ = ch.Nack(msg.DeliveryTag, false, false)
		return
	}
	span.SetAttributes(attribute.String("messaging.message.type", event.Type))
	c.logger.InfoContext(messageContext, "event received", "id", event.ID, "type", event.Type)
	if event.Type != "empleado.creado" && event.Type != "empleado.actualizado" && event.Type != "empleado.retirado" {
		c.logger.InfoContext(messageContext, "event discarded", "id", event.ID, "type", event.Type)
		_ = ch.Ack(msg.DeliveryTag, false)
		return
	}
	employee := text(event.Data, "id", "Id", "empleadoId", "EmpleadoId")
	name := strings.TrimSpace(text(event.Data, "nombre", "Nombre") + " " + text(event.Data, "apellido", "Apellido"))
	if name == "" {
		name = employee
	}
	inserted, err := c.repo.Process(
		messageContext, event.ID, event.Type, employee, name, text(event.Data, "email", "Email"),
	)
	if err != nil {
		span.RecordError(err)
		span.SetStatus(codes.Error, err.Error())
		c.logger.ErrorContext(messageContext, "event processing failed", "error", err)
		_ = ch.Nack(msg.DeliveryTag, false, true)
		return
	}
	if !inserted {
		c.logger.InfoContext(messageContext, "duplicate event", "id", event.ID)
	} else {
		c.logger.InfoContext(messageContext, "event processed", "id", event.ID)
	}
	_ = ch.Ack(msg.DeliveryTag, false)
}
