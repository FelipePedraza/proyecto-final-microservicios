package config

import "os"

type Config struct {
	Port, DBHost, DBPort, DBName, DBUser, DBPassword string
	RabbitHost, RabbitPort, RabbitUser, RabbitPassword, RabbitExchange, RabbitQueue string
}

func Load() Config {
	return Config{
		Port: env("PORT", "8083"), DBHost: env("DB_HOST", "perfiles-db"), DBPort: env("DB_PORT", "5432"),
		DBName: env("DB_NAME", "perfiles_db"), DBUser: env("DB_USER", "perfiles_user"), DBPassword: env("DB_PASSWORD", ""),
		RabbitHost: env("RABBITMQ_HOST", "message-broker"), RabbitPort: env("RABBITMQ_PORT", "5672"),
		RabbitUser: env("RABBITMQ_USER", "admin"), RabbitPassword: env("RABBITMQ_PASSWORD", ""),
		RabbitExchange: env("RABBITMQ_EXCHANGE", "empleados_exchange"), RabbitQueue: env("RABBITMQ_QUEUE", "perfiles.empleados"),
	}
}

func (c Config) DatabaseURL() string {
	return "postgres://" + c.DBUser + ":" + c.DBPassword + "@" + c.DBHost + ":" + c.DBPort + "/" + c.DBName
}
func env(key, fallback string) string { if value := os.Getenv(key); value != "" { return value }; return fallback }
