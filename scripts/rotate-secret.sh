#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
SECRETS_DIR="${SECRETS_DIR:-${PROJECT_ROOT}/secrets}"
ROTATION_BACKUP_DIR="${ROTATION_BACKUP_DIR:-${SECRETS_DIR}/backups}"

fail() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

usage() {
  printf 'Uso: %s jwt\n' "$0"
  printf 'Rota jwt_secret.txt de forma atómica y conserva una copia local para rollback.\n'
}

[[ "${1:-}" == "jwt" && "$#" -eq 1 ]] || {
  usage >&2
  exit 2
}

command -v openssl >/dev/null 2>&1 || fail "openssl no está instalado."
[[ -d "${SECRETS_DIR}" ]] || fail "No existe ${SECRETS_DIR}; ejecuta scripts/setup-secrets.sh."

secret_file="${SECRETS_DIR}/jwt_secret.txt"
[[ -f "${secret_file}" ]] || fail "No existe ${secret_file}."
[[ -s "${secret_file}" ]] || fail "El JWT Secret actual está vacío; no se puede crear rollback."
if grep -qE '^(REEMPLAZAR|GESTIONADO_EN_SECRETS)' "${secret_file}"; then
  fail "El JWT Secret actual es un marcador de plantilla."
fi
current_length="$(tr -d '\r\n' < "${secret_file}" | wc -c | tr -d ' ')"
[[ "${current_length}" -ge 32 ]] || fail "El JWT Secret actual no alcanza 32 bytes."

umask 077
mkdir -p "${ROTATION_BACKUP_DIR}"
chmod 700 "${ROTATION_BACKUP_DIR}"

timestamp="$(date -u '+%Y%m%dT%H%M%SZ')"
backup_file="$(mktemp "${ROTATION_BACKUP_DIR}/jwt_secret.${timestamp}.bak.XXXXXX")"
temporary_file="$(mktemp "${SECRETS_DIR}/.jwt_secret.rotate.XXXXXX")"

cleanup() {
  if [[ -n "${temporary_file:-}" && -f "${temporary_file}" ]]; then
    rm -f "${temporary_file}"
  fi
}
trap cleanup EXIT

cp "${secret_file}" "${backup_file}"
chmod 600 "${backup_file}"

openssl rand -base64 48 | tr -d '\n' > "${temporary_file}"
printf '\n' >> "${temporary_file}"
chmod 600 "${temporary_file}"

new_length="$(tr -d '\r\n' < "${temporary_file}" | wc -c | tr -d ' ')"
[[ "${new_length}" -ge 32 ]] || fail "El nuevo JWT Secret no alcanza 32 bytes."
if cmp -s "${secret_file}" "${temporary_file}"; then
  fail "El valor generado coincide con el anterior; no se aplicó la rotación."
fi

mv "${temporary_file}" "${secret_file}"
temporary_file=""
chmod 600 "${secret_file}"

printf 'Rotación JWT completada sin mostrar valores sensibles.\n'
printf 'Backup local para rollback: %s\n' "${backup_file}"
printf 'Servicios afectados: auth-service y gateway-service.\n'
printf 'Impacto: todos los JWT emitidos antes de esta rotación quedan invalidados.\n'
