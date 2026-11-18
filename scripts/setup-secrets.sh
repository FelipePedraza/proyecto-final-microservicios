#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
TEMPLATE_FILE="${PROJECT_ROOT}/.env.example"
ENV_FILE="${PROJECT_ROOT}/.env"
SECRETS_DIR="${PROJECT_ROOT}/secrets"

fail() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

command -v openssl >/dev/null 2>&1 || fail "openssl no está instalado."
command -v awk >/dev/null 2>&1 || fail "awk no está instalado."
[[ -f "${TEMPLATE_FILE}" ]] || fail "No se encontró ${TEMPLATE_FILE}."

umask 077
if [[ ! -e "${ENV_FILE}" ]]; then
  cp "${TEMPLATE_FILE}" "${ENV_FILE}"
fi
chmod 600 "${ENV_FILE}"
mkdir -p "${SECRETS_DIR}"
chmod 700 "${SECRETS_DIR}"

cleanup() {
  if [[ -n "${TEMP_FILE:-}" && -f "${TEMP_FILE}" ]]; then
    rm -f "${TEMP_FILE}"
  fi
}
trap cleanup EXIT

env_value() {
  local key="$1"
  awk -v key="${key}" 'index($0, key "=") == 1 { print substr($0, length(key) + 2); exit }' "${ENV_FILE}"
}

set_env_value() {
  local key="$1"
  local value="$2"
  TEMP_FILE="$(mktemp "${PROJECT_ROOT}/.env.tmp.XXXXXX")"
  if ! awk -v key="${key}" -v value="${value}" '
    BEGIN { found = 0 }
    index($0, key "=") == 1 {
      print key "=" value
      found = 1
      next
    }
    { print }
    END { if (!found) print key "=" value }
  ' "${ENV_FILE}" > "${TEMP_FILE}"; then
    fail "No se pudo actualizar la variable ${key}."
  fi
  mv "${TEMP_FILE}" "${ENV_FILE}"
  chmod 600 "${ENV_FILE}"
  TEMP_FILE=""
}

value_or_generated() {
  local key="$1"
  local generated="$2"
  local current
  current="$(env_value "${key}")"
  if [[ -n "${current}" && "${current}" != REEMPLAZAR* && "${current}" != GESTIONADO_EN_SECRETS* ]]; then
    printf '%s' "${current}"
  else
    printf '%s' "${generated}"
  fi
}

ensure_secret() {
  local filename="$1"
  local value="$2"
  local path="${SECRETS_DIR}/${filename}"
  if [[ -f "${path}" ]]; then
    local current
    current="$(<"${path}")"
    if [[ -n "${current}" && "${current}" != REEMPLAZAR* ]]; then
      chmod 600 "${path}"
      return
    fi
  fi
  [[ -n "${value}" ]] || fail "No se generó un valor para ${filename}."
  printf '%s\n' "${value}" > "${path}"
  chmod 600 "${path}"
}

random_hex() {
  openssl rand -hex 20
}

ensure_secret db_empleado_password.txt "$(value_or_generated EMPLEADO_DB_PASSWORD "$(random_hex)")"
ensure_secret db_departamento_password.txt "$(value_or_generated DEPARTAMENTO_DB_PASSWORD "$(random_hex)")"
ensure_secret db_notificaciones_password.txt "$(value_or_generated NOTIFICACIONES_DB_PASSWORD "$(random_hex)")"
ensure_secret db_perfiles_password.txt "$(value_or_generated PERFILES_DB_PASSWORD "$(random_hex)")"
ensure_secret db_vacaciones_password.txt "$(value_or_generated VACACIONES_DB_PASSWORD "$(random_hex)")"
ensure_secret db_auth_password.txt "$(value_or_generated AUTH_DB_PASSWORD "$(random_hex)")"
ensure_secret broker_password.txt "$(value_or_generated RABBITMQ_PASSWORD "$(random_hex)")"
ensure_secret rabbitmq_erlang_cookie.txt "$(value_or_generated RABBITMQ_ERLANG_COOKIE "$(openssl rand -hex 32)")"
ensure_secret jwt_secret.txt "$(value_or_generated JWT_SECRET_KEY "$(openssl rand -base64 32 | tr -d '\n')")"
ensure_secret auth_admin_password.txt "$(value_or_generated ADMIN_PASSWORD "$(random_hex)")"
ensure_secret jenkins_admin_password.txt "$(value_or_generated JENKINS_ADMIN_PASSWORD "$(random_hex)")"
ensure_secret sonar_db_password.txt "$(value_or_generated SONAR_DB_PASSWORD "$(random_hex)")"
ensure_secret sonar_token.txt "$(value_or_generated SONAR_TOKEN "PENDIENTE_CONFIGURAR_EN_SONARQUBE_$(openssl rand -hex 8)")"
ensure_secret grafana_admin_password.txt "$(value_or_generated GRAFANA_ADMIN_PASSWORD "$(random_hex)")"
ensure_secret discord_webhook_url.txt "$(value_or_generated DISCORD_WEBHOOK_URL 'https://discord.com/api/webhooks/0/placeholder')"
ensure_secret vault_root_token.txt "$(openssl rand -hex 32)"

set_env_value EMPLEADO_DB_PASSWORD GESTIONADO_EN_SECRETS_DB_EMPLEADO
set_env_value DEPARTAMENTO_DB_PASSWORD GESTIONADO_EN_SECRETS_DB_DEPARTAMENTO
set_env_value NOTIFICACIONES_DB_PASSWORD GESTIONADO_EN_SECRETS_DB_NOTIFICACIONES
set_env_value PERFILES_DB_PASSWORD GESTIONADO_EN_SECRETS_DB_PERFILES
set_env_value VACACIONES_DB_PASSWORD GESTIONADO_EN_SECRETS_DB_VACACIONES
set_env_value AUTH_DB_PASSWORD GESTIONADO_EN_SECRETS_DB_AUTH
set_env_value RABBITMQ_PASSWORD GESTIONADO_EN_SECRETS_BROKER
set_env_value RABBITMQ_ERLANG_COOKIE GESTIONADO_EN_SECRETS_RABBITMQ_COOKIE
set_env_value JWT_SECRET_KEY GESTIONADO_EN_SECRETS_JWT
set_env_value ADMIN_PASSWORD GESTIONADO_EN_SECRETS_AUTH_ADMIN
set_env_value JENKINS_ADMIN_PASSWORD GESTIONADO_EN_SECRETS_JENKINS_ADMIN
set_env_value SONAR_DB_PASSWORD GESTIONADO_EN_SECRETS_SONAR_DB
set_env_value SONAR_TOKEN GESTIONADO_EN_SECRETS_SONAR_TOKEN
set_env_value E2E_ADMIN_PASSWORD GESTIONADO_EN_SECRETS_AUTH_ADMIN
set_env_value E2E_JWT_SECRET GESTIONADO_EN_SECRETS_JWT
set_env_value GRAFANA_ADMIN_PASSWORD GESTIONADO_EN_SECRETS_GRAFANA_ADMIN
set_env_value DISCORD_WEBHOOK_URL GESTIONADO_EN_SECRETS_DISCORD_WEBHOOK

for secret_file in "${SECRETS_DIR}"/*.txt; do
  [[ -s "${secret_file}" ]] || fail "El archivo ${secret_file} está vacío."
  if grep -q '^REEMPLAZAR' "${secret_file}"; then
    fail "El archivo ${secret_file} conserva un marcador de plantilla."
  fi
  chmod 600 "${secret_file}"
done

printf 'Configuración local preparada.\n'
printf '  .env: variables no sensibles y referencias, permisos 600.\n'
printf '  secrets/: 16 archivos locales, permisos 600.\n'
printf 'Los valores no se mostraron en la terminal.\n'
printf 'Reemplaza secrets/sonar_token.txt con un token real antes de ejecutar análisis de Jenkins.\n'
