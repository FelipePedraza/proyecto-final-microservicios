#!/bin/sh

set -eu

export VAULT_ADDR="${VAULT_ADDR:-http://vault:8200}"
secrets_dir="${VAULT_SECRETS_DIR:-/run/secrets}"
policy_file="${VAULT_POLICY_FILE:-/vault/policies/auth-service.hcl}"
root_token_file="${secrets_dir}/vault_root_token"
approle_dir="${VAULT_APPROLE_DIR:-/vault/approle}"

fail() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

read_required_file() {
  file="$1"
  [ -r "${file}" ] || fail "No se puede leer ${file}."
  value="$(tr -d '\r\n' < "${file}")"
  [ -n "${value}" ] || fail "${file} está vacío."
  case "${value}" in
    REEMPLAZAR*|GESTIONADO_EN_SECRETS*) fail "${file} contiene un marcador inseguro." ;;
  esac
  printf '%s' "${value}"
}

VAULT_TOKEN="$(read_required_file "${root_token_file}")"
export VAULT_TOKEN

for application_secret in \
  "${secrets_dir}/db_auth_password" \
  "${secrets_dir}/broker_password" \
  "${secrets_dir}/jwt_secret" \
  "${secrets_dir}/auth_admin_password"; do
  read_required_file "${application_secret}" >/dev/null
done

until vault status >/dev/null 2>&1; do
  sleep 1
done

vault auth enable approle >/dev/null 2>&1 || true
vault policy write auth-service "${policy_file}" >/dev/null
vault write auth/approle/role/auth-service \
  token_policies=auth-service \
  token_type=batch \
  token_ttl=5m \
  token_max_ttl=10m \
  secret_id_ttl=24h \
  secret_id_num_uses=100 >/dev/null

vault kv put -mount=secret auth-service/db-password value=- \
  < "${secrets_dir}/db_auth_password" >/dev/null
vault kv put -mount=secret auth-service/broker-password value=- \
  < "${secrets_dir}/broker_password" >/dev/null
vault kv put -mount=secret auth-service/jwt-secret value=- \
  < "${secrets_dir}/jwt_secret" >/dev/null
vault kv put -mount=secret auth-service/admin-password value=- \
  < "${secrets_dir}/auth_admin_password" >/dev/null

mkdir -p "${approle_dir}"
chmod 700 "${approle_dir}"

role_tmp="$(mktemp "${approle_dir}/.role_id.XXXXXX")"
secret_tmp="$(mktemp "${approle_dir}/.secret_id.XXXXXX")"
cleanup() {
  rm -f "${role_tmp:-}" "${secret_tmp:-}"
}
trap cleanup EXIT

vault read -field=role_id auth/approle/role/auth-service/role-id > "${role_tmp}"
vault write -field=secret_id -f auth/approle/role/auth-service/secret-id > "${secret_tmp}"
chmod 600 "${role_tmp}" "${secret_tmp}"
mv "${role_tmp}" "${approle_dir}/role_id"
mv "${secret_tmp}" "${approle_dir}/secret_id"
role_tmp=""
secret_tmp=""

printf 'Vault inicializado: KV v2 y AppRole auth-service listos.\n'
printf 'No se mostraron tokens, RoleID, SecretID ni secretos de aplicación.\n'
