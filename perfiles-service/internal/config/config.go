package config

import "os"
import "strings"
import "path/filepath"

type Config struct {
	Port, DBHost, DBPort, DBName, DBUser, DBPassword                                string
	RabbitHost, RabbitPort, RabbitUser, RabbitPassword, RabbitExchange, RabbitQueue string
	OtelServiceName, OtelZipkinEndpoint                                             string
}

func Load() Config {
	dbPassword := readSecret("db_perfiles_password", "DB_PASSWORD")
	rabbitPassword := readSecret("broker_password", "RABBITMQ_PASSWORD")
	return Config{
		Port: env("PORT", "8083"), DBHost: env("DB_HOST", "perfiles-db"), DBPort: env("DB_PORT", "5432"),
		DBName: env("DB_NAME", "perfiles_db"), DBUser: env("DB_USER", "perfiles_user"), DBPassword: dbPassword,
		RabbitHost: env("RABBITMQ_HOST", "message-broker"), RabbitPort: env("RABBITMQ_PORT", "5672"),
		RabbitUser: env("RABBITMQ_USER", "admin"), RabbitPassword: rabbitPassword,
		RabbitExchange: env("RABBITMQ_EXCHANGE", "empleados_exchange"), RabbitQueue: env("RABBITMQ_QUEUE", "perfiles.empleados"),
		OtelServiceName:    env("OTEL_SERVICE_NAME", "perfiles-service"),
		OtelZipkinEndpoint: env("OTEL_EXPORTER_ZIPKIN_ENDPOINT", "http://zipkin:9411/api/v2/spans"),
	}
}

func (c Config) DatabaseURL() string {
	return "postgres://" + c.DBUser + ":" + c.DBPassword + "@" + c.DBHost + ":" + c.DBPort + "/" + c.DBName
}
func env(key, fallback string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}

	func readSecret(secretName, envName string) string {
		secretPath := filepath.Join("/run/secrets", secretName)
		if data, err := os.ReadFile(secretPath); err == nil {
			if value := strings.TrimSpace(string(data)); value != "" {
				return value
			}
		}
		if value := os.Getenv(envName); value != "" {
			return value
		}
		panic("Secreto '" + secretName + "' no configurado. Montar " + secretPath + " o definir " + envName + ".")
	}
	return fallback
}
