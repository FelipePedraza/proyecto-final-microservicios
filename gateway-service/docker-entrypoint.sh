#!/bin/sh
set -eu

if [ -f /run/secrets/jwt_secret ]; then
  JWT_SECRET="$(tr -d '\r\n' < /run/secrets/jwt_secret)"
elif [ -z "${JWT_SECRET:-}" ]; then
  echo "ERROR: JWT Secret no configurado. Montar /run/secrets/jwt_secret o definir JWT_SECRET." >&2
  exit 1
fi

if [ -z "${JWT_SECRET:-}" ]; then
  echo "ERROR: JWT Secret vacío." >&2
  exit 1
fi
export JWT_SECRET
exec java -jar app.jar
