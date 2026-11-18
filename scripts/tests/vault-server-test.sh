#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
TEST_ROOT="$(mktemp -d)"

cleanup() {
  rm -rf "${TEST_ROOT}"
}
trap cleanup EXIT

mkdir -p "${TEST_ROOT}/bin"
cp "${PROJECT_ROOT}/scripts/tests/fixtures/fake-vault.sh" "${TEST_ROOT}/bin/vault"
chmod +x "${TEST_ROOT}/bin/vault"

root_token='root-token-de-prueba-con-mas-de-treinta-y-dos-caracteres'
printf '%s\n' "${root_token}" > "${TEST_ROOT}/vault_root_token"
output="$(
  PATH="${TEST_ROOT}/bin:${PATH}" \
  VAULT_FAKE_LOG="${TEST_ROOT}/vault.log" \
  VAULT_ROOT_TOKEN_FILE="${TEST_ROOT}/vault_root_token" \
  sh "${PROJECT_ROOT}/vault/server-dev.sh"
)"

rg -q '^server -dev -dev-listen-address=0.0.0.0:8200$' "${TEST_ROOT}/vault.log"
[[ "${output}" != *"${root_token}"* ]]

printf 'REEMPLAZAR_CON_TOKEN_REAL\n' > "${TEST_ROOT}/vault_root_token"
if PATH="${TEST_ROOT}/bin:${PATH}" \
  VAULT_FAKE_LOG="${TEST_ROOT}/vault.log" \
  VAULT_ROOT_TOKEN_FILE="${TEST_ROOT}/vault_root_token" \
  sh "${PROJECT_ROOT}/vault/server-dev.sh" >/dev/null 2>&1; then
  printf 'ERROR: Vault aceptó un root token de plantilla.\n' >&2
  exit 1
fi

printf 'vault-server-test: ok\n'
