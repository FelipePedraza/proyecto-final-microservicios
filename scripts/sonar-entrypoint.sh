#!/bin/bash
set -euo pipefail

if [[ -f /run/secrets/db_sonar_password ]]; then
  export SONAR_JDBC_PASSWORD="$(tr -d '\r\n' < /run/secrets/db_sonar_password)"
elif [[ -z "${SONAR_JDBC_PASSWORD:-}" ]]; then
  echo "ERROR: Secreto 'db_sonar_password' no configurado." >&2
  exit 1
fi

exec /opt/sonarqube/docker/entrypoint.sh "$@"
