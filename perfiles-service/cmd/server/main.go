package main

import (
	"context"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/FelipePedraza/proyecto-final-microservicios/perfiles-service/internal/api"
	"github.com/FelipePedraza/proyecto-final-microservicios/perfiles-service/internal/config"
	"github.com/FelipePedraza/proyecto-final-microservicios/perfiles-service/internal/messaging"
	"github.com/FelipePedraza/proyecto-final-microservicios/perfiles-service/internal/repository"
	"github.com/FelipePedraza/proyecto-final-microservicios/perfiles-service/internal/telemetry"
	"go.opentelemetry.io/contrib/instrumentation/net/http/otelhttp"
)

func main() {
	cfg := config.Load()
	logger := slog.New(telemetry.TraceHandler{
		Handler: slog.NewJSONHandler(os.Stdout, nil),
	}).With("service", "perfiles-service")
	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stop()

	shutdownTelemetry, err := telemetry.Setup(ctx, cfg.OtelServiceName, cfg.OtelZipkinEndpoint)
	if err != nil {
		logger.Error("openTelemetry setup failed", "error", err)
		os.Exit(1)
	}
	defer func() {
		shutdownCtx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		if err := shutdownTelemetry(shutdownCtx); err != nil {
			logger.Error("openTelemetry shutdown failed", "error", err)
		}
	}()

	repo, err := repository.New(ctx, cfg)
	if err != nil {
		logger.Error("database connection failed", "error", err)
		os.Exit(1)
	}
	defer repo.Close()

	brokerHealth := &messaging.BrokerHealth{}
	go messaging.NewConsumer(cfg, repo, logger, brokerHealth).Run(ctx)
	// NewHandler extrae traceparent/baggage y crea el span servidor de cada petición.
	tracedRouter := otelhttp.NewHandler(
		api.NewRouter(repo, logger, brokerHealth.IsConnected),
		cfg.OtelServiceName,
	)
	server := &http.Server{Addr: ":" + cfg.Port, Handler: tracedRouter}
	go func() {
		logger.Info("http server started", "port", cfg.Port)
		if err := server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			logger.Error("http server failed", "error", err)
			stop()
		}
	}()
	<-ctx.Done()
	shutdownCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	_ = server.Shutdown(shutdownCtx)
}
