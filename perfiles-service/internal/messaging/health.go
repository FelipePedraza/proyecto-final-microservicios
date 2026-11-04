package messaging

import "sync/atomic"

// BrokerHealth comparte de forma segura el estado de la conexión AMQP con /health.
type BrokerHealth struct{ connected atomic.Bool }

func (h *BrokerHealth) SetConnected(value bool) { h.connected.Store(value) }
func (h *BrokerHealth) IsConnected() bool       { return h.connected.Load() }
