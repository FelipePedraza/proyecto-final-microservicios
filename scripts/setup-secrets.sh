#!/bin/bash
set -eu

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

mkdir -p secrets
chmod 700 secrets

generate_if_missing() {
  local name="$1"
  local format="$2"
  local file="secrets/$name.txt"

  if [[ -f "$file" ]]; then
    echo "Conservando secreto existente: $file"
    return
  fi

  if [[ "$format" == "base64" ]]; then
    openssl rand -base64 32 > "$file"
  else
    openssl rand -hex 20 > "$file"
  fi
  chmod 600 "$file"
  echo "Generado: $file"
}

generate_if_missing jwt_secret base64
generate_if_missing broker_password hex
generate_if_missing rabbitmq_erlang_cookie hex
generate_if_missing db_empleado_password hex
generate_if_missing db_departamentos_password hex
generate_if_missing db_notificaciones_password hex
generate_if_missing db_perfiles_password hex
generate_if_missing db_vacaciones_password hex
generate_if_missing db_auth_password hex
generate_if_missing db_sonar_password hex
generate_if_missing auth_admin_password hex
generate_if_missing grafana_admin_password hex
generate_if_missing jenkins_admin_password hex
generate_if_missing sonar_token hex
generate_if_missing discord_webhook_url hex

if [[ ! -f .env ]]; then
  cp .env.example .env
  echo "Creado .env desde .env.example"
fi

chmod 600 .env
echo "Secretos locales preparados en secrets/. Configure tambien las variables no sensibles de .env."
