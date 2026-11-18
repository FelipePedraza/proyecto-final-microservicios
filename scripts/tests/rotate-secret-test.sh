#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROTATE_SCRIPT="$(cd "${SCRIPT_DIR}/.." && pwd)/rotate-secret.sh"
TEST_ROOT="$(mktemp -d)"

cleanup() {
  rm -rf "${TEST_ROOT}"
}
trap cleanup EXIT

permissions() {
  if stat -f '%Sp' "$1" >/dev/null 2>&1; then
    stat -f '%Sp' "$1"
  else
    stat -c '%A' "$1"
  fi
}

secrets_dir="${TEST_ROOT}/secrets"
backups_dir="${TEST_ROOT}/backups"
mkdir -p "${secrets_dir}"
printf 'jwt-anterior-de-prueba-con-mas-de-treinta-y-dos-bytes\n' > "${secrets_dir}/jwt_secret.txt"
chmod 600 "${secrets_dir}/jwt_secret.txt"
old_value="$(<"${secrets_dir}/jwt_secret.txt")"

output="$(SECRETS_DIR="${secrets_dir}" ROTATION_BACKUP_DIR="${backups_dir}" "${ROTATE_SCRIPT}" jwt)"
new_value="$(<"${secrets_dir}/jwt_secret.txt")"

[[ "${old_value}" != "${new_value}" ]]
[[ "${#new_value}" -ge 32 ]]
[[ "$(permissions "${secrets_dir}/jwt_secret.txt")" == '-rw-------' ]]
[[ "$(permissions "${backups_dir}")" == 'drwx------' ]]

backup_count="$(find "${backups_dir}" -type f -name 'jwt_secret.*.bak.*' | wc -l | tr -d ' ')"
[[ "${backup_count}" -eq 1 ]]
backup_file="$(find "${backups_dir}" -type f -name 'jwt_secret.*.bak.*' -print -quit)"
[[ "$(<"${backup_file}")" == "${old_value}" ]]
[[ "$(permissions "${backup_file}")" == '-rw-------' ]]
[[ "${output}" != *"${old_value}"* ]]
[[ "${output}" != *"${new_value}"* ]]

if SECRETS_DIR="${secrets_dir}" "${ROTATE_SCRIPT}" desconocido >/dev/null 2>&1; then
  printf 'ERROR: se aceptó un tipo de rotación desconocido.\n' >&2
  exit 1
fi

printf 'REEMPLAZAR_CON_VALOR_REAL\n' > "${secrets_dir}/jwt_secret.txt"
if SECRETS_DIR="${secrets_dir}" ROTATION_BACKUP_DIR="${backups_dir}" "${ROTATE_SCRIPT}" jwt >/dev/null 2>&1; then
  printf 'ERROR: se rotó un marcador de plantilla.\n' >&2
  exit 1
fi

printf 'rotate-secret-test: ok\n'
