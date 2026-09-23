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
)

type raw map[string]any
type Envelope struct { ID string; Type string; Data raw }
func field(m raw, names ...string) any { for _, n := range names { if v, ok := m[n]; ok && v != nil { return v } }; return nil }
func parse(body []byte) (Envelope, error) {
	var root raw; if err := json.Unmarshal(body,&root); err != nil { return Envelope{},err }
	id, _ := field(root,"id","Id").(string); typ, _ := field(root,"type","Type").(string)
	data, _ := field(root,"data","Data").(map[string]any); if data == nil { data=raw{} }
	employee, _ := field(data,"id","Id","empleadoId","EmpleadoId").(string)
	if id=="" || typ=="" || employee=="" { return Envelope{}, &invalidEnvelope{} }
	return Envelope{ID:id,Type:typ,Data:data},nil
}
type invalidEnvelope struct{}; func (*invalidEnvelope) Error() string { return "envelope inválido" }
func text(data raw, names ...string) string { v:=field(data,names...); s,_:=v.(string); return s }

type Consumer struct { cfg config.Config; repo *repository.Repository; logger *slog.Logger }
func NewConsumer(c config.Config,r *repository.Repository,l *slog.Logger)*Consumer{return &Consumer{c,r,l}}
func (c *Consumer) Run(ctx context.Context) {
	for ctx.Err()==nil {
		if err:=c.consume(ctx); err!=nil { c.logger.Error("rabbit connection failed","error",err); select {case <-ctx.Done(): return; case <-time.After(3*time.Second):} }
	}
}
func (c *Consumer) consume(ctx context.Context) error {
	conn,err:=amqp091.Dial("amqp://"+c.cfg.RabbitUser+":"+c.cfg.RabbitPassword+"@"+c.cfg.RabbitHost+":"+c.cfg.RabbitPort); if err!=nil{return err}; defer conn.Close()
	ch,err:=conn.Channel(); if err!=nil{return err}; defer ch.Close()
	if err=ch.ExchangeDeclare(c.cfg.RabbitExchange, "fanout", true, false, false, false, nil); err!=nil { return err }
	dlq:=c.cfg.RabbitQueue+".dlq"
	if _, err = ch.QueueDeclare(dlq, true, false, false, false, nil); err != nil { return err }
	q, err := ch.QueueDeclare(c.cfg.RabbitQueue, true, false, false, false, amqp091.Table{"x-dead-letter-exchange": "", "x-dead-letter-routing-key": dlq}); if err != nil { return err }
	if err = ch.QueueBind(q.Name, "", c.cfg.RabbitExchange, false, nil); err != nil { return err }
	if err = ch.Qos(1, 0, false); err != nil { return err }
	msgs, err := ch.Consume(q.Name, "", false, false, false, false, nil); if err != nil { return err }
	c.logger.Info("rabbit consumer ready","queue",q.Name)
	for { select { case <-ctx.Done(): return nil; case msg,ok:=<-msgs:
		if !ok{return context.Canceled}; c.handle(ctx,ch,msg)
	} }
}
func (c *Consumer) handle(ctx context.Context, ch *amqp091.Channel, msg amqp091.Delivery) {
	e, err := parse(msg.Body)
	if err != nil {
		c.logger.Warn("event discarded", "error", err)
		_ = ch.Nack(msg.DeliveryTag, false, false)
		return
	}
	c.logger.Info("event received", "id", e.ID, "type", e.Type)
	if e.Type != "empleado.creado" && e.Type != "empleado.actualizado" && e.Type != "empleado.retirado" {
		c.logger.Info("event discarded", "id", e.ID, "type", e.Type)
		_ = ch.Ack(msg.DeliveryTag, false)
		return
	}
	employee := text(e.Data, "id", "Id", "empleadoId", "EmpleadoId")
	name := strings.TrimSpace(text(e.Data, "nombre", "Nombre") + " " + text(e.Data, "apellido", "Apellido"))
	if name == "" { name = employee }
	inserted, err := c.repo.Process(ctx, e.ID, e.Type, employee, name, text(e.Data, "email", "Email"))
	if err != nil {
		c.logger.Error("event processing failed", "error", err)
		_ = ch.Nack(msg.DeliveryTag, false, true)
		return
	}
	if !inserted { c.logger.Info("duplicate event", "id", e.ID) } else { c.logger.Info("event processed", "id", e.ID) }
	_ = ch.Ack(msg.DeliveryTag, false)
}
