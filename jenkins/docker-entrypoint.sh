#!/bin/bash
set -euo pipefail

read_secret() {
  local secret_name="$1"
  local env_name="$2"
  local secret_path="/run/secrets/$secret_name"

  if [[ -f "$secret_path" ]]; then
    export "$env_name=$(tr -d '\r\n' < "$secret_path")"
  elif [[ -z "${!env_name:-}" ]]; then
    echo "ERROR: Secreto '$secret_name' no configurado. Montar $secret_path o definir $env_name." >&2
    exit 1
  fi
}

read_secret jenkins_admin_password JENKINS_ADMIN_PASSWORD
read_secret sonar_token SONAR_TOKEN

exec /usr/bin/tini -- /usr/local/bin/jenkins.sh
