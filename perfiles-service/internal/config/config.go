package config

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"
)

type Config struct {
	Port, DBHost, DBPort, DBName, DBUser, DBPassword                                string
	RabbitHost, RabbitPort, RabbitUser, RabbitPassword, RabbitExchange, RabbitQueue string
	OtelServiceName, OtelZipkinEndpoint                                             string
}

func Load() Config {
	return Config{
		Port: env("PORT", "8083"), DBHost: env("DB_HOST", "perfiles-db"), DBPort: env("DB_PORT", "5432"),
		DBName: env("DB_NAME", "perfiles_db"), DBUser: env("DB_USER", "perfiles_user"), DBPassword: requiredSecret("db_perfiles_password", "DB_PASSWORD"),
		RabbitHost: env("RABBITMQ_HOST", "message-broker"), RabbitPort: env("RABBITMQ_PORT", "5672"),
		RabbitUser: env("RABBITMQ_USER", "admin"), RabbitPassword: requiredSecret("broker_password", "RABBITMQ_PASSWORD"),
		RabbitExchange: env("RABBITMQ_EXCHANGE", "empleados_exchange"), RabbitQueue: env("RABBITMQ_QUEUE", "perfiles.empleados"),
		OtelServiceName:    env("OTEL_SERVICE_NAME", "perfiles-service"),
		OtelZipkinEndpoint: env("OTEL_EXPORTER_ZIPKIN_ENDPOINT", "http://zipkin:9411/api/v2/spans"),
	}
}

func requiredSecret(secretName, envFallback string) string {
	secretsDir := env("SECRETS_DIR", "/run/secrets")
	secretPath := filepath.Join(secretsDir, secretName)
	value := ""
	configured := false
	if contents, err := os.ReadFile(secretPath); err == nil {
		value = strings.TrimSpace(string(contents))
		configured = true
	} else if !os.IsNotExist(err) {
		panic(fmt.Sprintf("ERROR: No se pudo leer el secreto %q desde %s: %v", secretName, secretPath, err))
	} else if environmentValue, exists := os.LookupEnv(envFallback); exists {
		value = strings.TrimSpace(environmentValue)
		configured = true
	}
	hint := fmt.Sprintf(" Montar /run/secrets/%s o definir %s.", secretName, envFallback)
	if !configured {
		panic(fmt.Sprintf("ERROR: Secreto %q no configurado.%s", secretName, hint))
	}
	if value == "" {
		panic(fmt.Sprintf("ERROR: Secreto %q está vacío.%s", secretName, hint))
	}
	if strings.HasPrefix(value, "REEMPLAZAR") || strings.HasPrefix(value, "GESTIONADO_EN_SECRETS") {
		panic(fmt.Sprintf("ERROR: Secreto %q contiene un marcador de plantilla inseguro.%s", secretName, hint))
	}
	return value
}

func (c Config) DatabaseURL() string {
	return "postgres://" + c.DBUser + ":" + c.DBPassword + "@" + c.DBHost + ":" + c.DBPort + "/" + c.DBName
}
func env(key, fallback string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return fallback
}
