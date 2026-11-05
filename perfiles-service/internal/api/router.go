package api

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"log/slog"
	"net/http"
	"strconv"
	"time"

	profiledocs "github.com/FelipePedraza/proyecto-final-microservicios/perfiles-service/internal/docs"
	"github.com/FelipePedraza/proyecto-final-microservicios/perfiles-service/internal/domain"
	"github.com/FelipePedraza/proyecto-final-microservicios/perfiles-service/internal/repository"
	"github.com/go-chi/chi/v5"
	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promauto"
	"github.com/prometheus/client_golang/prometheus/promhttp"
	httpSwagger "github.com/swaggo/http-swagger"
)

var metricsRegistry = prometheus.NewRegistry()

var httpRequestsTotal = promauto.With(metricsRegistry).NewCounterVec(
	prometheus.CounterOpts{
		Name: "http_requests_total",
		Help: "Total de solicitudes HTTP procesadas.",
		ConstLabels: prometheus.Labels{
			"service": "perfiles-service",
		},
	},
	[]string{"method", "route", "status_code"},
)

var httpRequestDuration = promauto.With(metricsRegistry).NewHistogramVec(
	prometheus.HistogramOpts{
		Name:    "http_request_duration_seconds",
		Help:    "Duración de las solicitudes HTTP en segundos.",
		Buckets: prometheus.DefBuckets,
		ConstLabels: prometheus.Labels{
			"service": "perfiles-service",
		},
	},
	[]string{"method", "route", "status_code"},
)

func init() {
	metricsRegistry.MustRegister(
		prometheus.NewGoCollector(),
		prometheus.NewProcessCollector(prometheus.ProcessCollectorOpts{}),
	)
}

type metricsResponseWriter struct {
	http.ResponseWriter
	statusCode int
}

func (w *metricsResponseWriter) WriteHeader(statusCode int) {
	w.statusCode = statusCode
	w.ResponseWriter.WriteHeader(statusCode)
}

func metricsMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		started := time.Now()
		wrapped := &metricsResponseWriter{ResponseWriter: w, statusCode: http.StatusOK}
		next.ServeHTTP(wrapped, r)

		route := chi.RouteContext(r.Context()).RoutePattern()
		if route == "" {
			route = "unmatched"
		}
		labels := []string{r.Method, route, strconv.Itoa(wrapped.statusCode)}
		httpRequestsTotal.WithLabelValues(labels...).Inc()
		httpRequestDuration.WithLabelValues(labels...).Observe(time.Since(started).Seconds())
	})
}

type profileRepository interface {
	List(context.Context) ([]domain.Perfil, error)
	Get(context.Context, string) (domain.Perfil, error)
	Update(context.Context, string, domain.UpdatePerfil) (domain.Perfil, error)
	Ready(context.Context) error
}

func NewRouter(repo profileRepository, logger *slog.Logger, brokerChecks ...func() bool) http.Handler {
	brokerReady := func() bool { return true }
	if len(brokerChecks) > 0 && brokerChecks[0] != nil {
		brokerReady = brokerChecks[0]
	}
	r := chi.NewRouter()
	r.Use(metricsMiddleware)
	r.Handle("/metrics", promhttp.HandlerFor(metricsRegistry, promhttp.HandlerOpts{}))
	health := func(w http.ResponseWriter, req *http.Request) {
		database := "UP"
		if err := repo.Ready(req.Context()); err != nil {
			database = "DOWN"
		}
		messageBroker := "UP"
		if !brokerReady() {
			messageBroker = "DOWN"
		}
		serviceStatus := "UP"
		statusCode := http.StatusOK
		if database == "DOWN" {
			serviceStatus = "DOWN"
			statusCode = http.StatusServiceUnavailable
		}
		writeJSON(w, statusCode, map[string]any{
			"status":  serviceStatus,
			"service": "perfiles-service",
			"checks":  map[string]string{"database": database, "messageBroker": messageBroker},
		})
	}
	r.Get("/health", health)
	r.Get("/health/live", health)
	r.Get("/health/ready", health)
	r.Get("/openapi.json", func(w http.ResponseWriter, req *http.Request) {
		http.ServeContent(w, req, "openapi.json", time.Time{}, bytes.NewReader(profiledocs.OpenAPIV3))
	})
	r.Get("/swagger/*", httpSwagger.Handler(httpSwagger.URL("/openapi.json")))
	r.Get("/perfiles", list(repo))
	r.Get("/perfiles/{empleadoId}", get(repo))
	r.Put("/perfiles/{empleadoId}", update(repo))
	return r
}
func list(repo profileRepository) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		p, err := repo.List(r.Context())
		if err != nil {
			writeError(w, 500, "no se pudieron consultar los perfiles")
			return
		}
		writeJSON(w, 200, p)
	}
}
func get(repo profileRepository) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		p, err := repo.Get(r.Context(), chi.URLParam(r, "empleadoId"))
		if errors.Is(err, repository.ErrNotFound) {
			writeError(w, 404, "no existe un perfil para el empleado solicitado")
			return
		}
		if err != nil {
			writeError(w, 500, "no se pudo consultar el perfil")
			return
		}
		writeJSON(w, 200, p)
	}
}
func update(repo profileRepository) http.HandlerFunc {
	type updateRequest struct {
		Telefono  *string `json:"telefono"`
		Direccion *string `json:"direccion"`
		Ciudad    *string `json:"ciudad"`
		Biografia *string `json:"biografia"`
	}

	return func(w http.ResponseWriter, r *http.Request) {
		var request updateRequest
		dec := json.NewDecoder(r.Body)
		dec.DisallowUnknownFields()
		if err := dec.Decode(&request); err != nil {
			writeError(w, http.StatusBadRequest, "body inválido")
			return
		}
		if request.Telefono == nil || request.Direccion == nil ||
			request.Ciudad == nil || request.Biografia == nil {
			writeError(w, http.StatusBadRequest, "se requieren telefono, direccion, ciudad y biografia")
			return
		}

		u := domain.UpdatePerfil{
			Telefono:  *request.Telefono,
			Direccion: *request.Direccion,
			Ciudad:    *request.Ciudad,
			Biografia: *request.Biografia,
		}
		p, err := repo.Update(r.Context(), chi.URLParam(r, "empleadoId"), u)
		if errors.Is(err, repository.ErrNotFound) {
			writeError(w, http.StatusNotFound, "no existe un perfil para el empleado solicitado")
			return
		}
		if err != nil {
			writeError(w, http.StatusInternalServerError, "no se pudo actualizar el perfil")
			return
		}
		writeJSON(w, http.StatusOK, p)
	}
}
func writeJSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(v)
}
func writeError(w http.ResponseWriter, status int, msg string) {
	writeJSON(w, status, map[string]string{"error": msg})
}
