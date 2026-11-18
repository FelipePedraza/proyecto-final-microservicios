#!/bin/sh
set -eu

read_secret() {
  secret_name="$1"
  env_name="$2"
  secret_path="/run/secrets/$secret_name"
  if [ -f "$secret_path" ]; then
    value="$(tr -d '\r\n' < "$secret_path")"
  else
    eval "value=\${$env_name:-}"
  fi
  if [ -z "$value" ]; then
    echo "ERROR: Secreto '$secret_name' no configurado. Montar $secret_path o definir $env_name." >&2
    exit 1
  fi
  export "$env_name=$value"
}

read_secret db_vacaciones_password DB_PASSWORD
read_secret broker_password RABBITMQ_PASSWORD

exec java -jar app.jar
