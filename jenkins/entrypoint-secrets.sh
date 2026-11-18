#!/usr/bin/env bash

set -euo pipefail

read_secret() {
  local name="$1"
  local path="/run/secrets/${name}"
  [[ -r "${path}" ]] || { printf 'ERROR: secreto %s no disponible en %s\n' "${name}" "${path}" >&2; exit 1; }
  local value
  value="$(<"${path}")"
  [[ -n "${value}" && "${value}" != REEMPLAZAR* ]] || {
    printf 'ERROR: secreto %s vacío o con marcador de plantilla\n' "${name}" >&2
    exit 1
  }
  printf '%s' "${value}"
}

export JENKINS_ADMIN_PASSWORD="$(read_secret jenkins_admin_password)"
export SONAR_TOKEN="$(read_secret sonar_token)"

exec /usr/local/bin/jenkins.sh "$@"
