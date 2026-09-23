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
)

func main() {
	cfg := config.Load()
	logger := slog.New(slog.NewJSONHandler(os.Stdout, nil))
	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stop()

	repo, err := repository.New(ctx, cfg)
	if err != nil {
		logger.Error("database connection failed", "error", err)
		os.Exit(1)
	}
	defer repo.Close()

	go messaging.NewConsumer(cfg, repo, logger).Run(ctx)
	server := &http.Server{Addr: ":" + cfg.Port, Handler: api.NewRouter(repo, logger)}
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
