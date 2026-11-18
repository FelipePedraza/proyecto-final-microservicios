#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
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

mkdir -p "${TEST_ROOT}/bin" "${TEST_ROOT}/secrets" "${TEST_ROOT}/approle"
for name in vault_root_token db_auth_password broker_password jwt_secret auth_admin_password; do
  printf '%s-valor-seguro-de-prueba-con-longitud-suficiente\n' "${name}" \
    > "${TEST_ROOT}/secrets/${name}"
done

cp "${PROJECT_ROOT}/scripts/tests/fixtures/fake-vault.sh" "${TEST_ROOT}/bin/vault"
chmod +x "${TEST_ROOT}/bin/vault"

output="$(
  PATH="${TEST_ROOT}/bin:${PATH}" \
  VAULT_FAKE_LOG="${TEST_ROOT}/vault.log" \
  VAULT_SECRETS_DIR="${TEST_ROOT}/secrets" \
  VAULT_POLICY_FILE="${PROJECT_ROOT}/vault/policies/auth-service.hcl" \
  VAULT_APPROLE_DIR="${TEST_ROOT}/approle" \
  sh "${PROJECT_ROOT}/vault/bootstrap.sh"
)"

[[ "$(<"${TEST_ROOT}/approle/role_id")" == 'role-id-generado' ]]
[[ "$(<"${TEST_ROOT}/approle/secret_id")" == 'secret-id-generado' ]]
[[ "$(permissions "${TEST_ROOT}/approle")" == 'drwx------' ]]
[[ "$(permissions "${TEST_ROOT}/approle/role_id")" == '-rw-------' ]]
[[ "$(permissions "${TEST_ROOT}/approle/secret_id")" == '-rw-------' ]]
[[ "$(rg -c '^kv put ' "${TEST_ROOT}/vault.log")" -eq 4 ]]
[[ "${output}" != *'valor-seguro-de-prueba'* ]]
[[ "${output}" != *'role-id-generado'* ]]
[[ "${output}" != *'secret-id-generado'* ]]

printf 'vault-bootstrap-test: ok\n'
