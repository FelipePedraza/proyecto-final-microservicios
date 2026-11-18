#!/bin/sh

set -eu

root_token_file="${VAULT_ROOT_TOKEN_FILE:-/run/secrets/vault_root_token}"
if [ ! -r "${root_token_file}" ]; then
  printf 'ERROR: Vault no puede leer %s.\n' "${root_token_file}" >&2
  exit 1
fi

VAULT_DEV_ROOT_TOKEN_ID="$(tr -d '\r\n' < "${root_token_file}")"
if [ -z "${VAULT_DEV_ROOT_TOKEN_ID}" ]; then
  printf 'ERROR: vault_root_token está vacío.\n' >&2
  exit 1
fi
case "${VAULT_DEV_ROOT_TOKEN_ID}" in
  REEMPLAZAR*|GESTIONADO_EN_SECRETS*)
    printf 'ERROR: vault_root_token contiene un marcador inseguro.\n' >&2
    exit 1
    ;;
esac
if [ "${#VAULT_DEV_ROOT_TOKEN_ID}" -lt 32 ]; then
  printf 'ERROR: vault_root_token debe tener al menos 32 caracteres.\n' >&2
  exit 1
fi
export VAULT_DEV_ROOT_TOKEN_ID

exec vault server -dev -dev-listen-address=0.0.0.0:8200
