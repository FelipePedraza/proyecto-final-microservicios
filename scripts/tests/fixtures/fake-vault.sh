#!/bin/sh

set -eu

command_name="$1"
shift
printf '%s %s\n' "${command_name}" "$*" >> "${VAULT_FAKE_LOG}"

case "${command_name}" in
  status|auth|policy)
    exit 0
    ;;
  server)
    [ -n "${VAULT_DEV_ROOT_TOKEN_ID:-}" ] || exit 1
    exit 0
    ;;
  kv)
    cat >/dev/null
    exit 0
    ;;
  read)
    printf 'role-id-generado\n'
    ;;
  write)
    case "$*" in
      *auth/approle/role/auth-service/secret-id*) printf 'secret-id-generado\n' ;;
      *) exit 0 ;;
    esac
    ;;
  *)
    printf 'Comando Vault inesperado: %s\n' "${command_name}" >&2
    exit 1
    ;;
esac
