# Evidencia de implementación de HashiCorp Vault

Fecha: 2026-11-18 (America/Bogota)

## Arquitectura implementada

- `vault` ejecuta Vault 2.2.2 en modo de desarrollo dentro del perfil Compose
  opcional `vault`.
- `vault-bootstrap` habilita AppRole, instala una política de mínimo privilegio,
  carga cuatro valores en KV v2 y entrega RoleID/SecretID mediante un volumen local.
- `auth-service` se autentica por AppRole y consulta los secretos durante el
  arranque mediante la API REST de Vault.
- El token raíz solo está montado en Vault y bootstrap. No se monta ni se pasa a
  `auth-service`.
- Cuando `VAULT_ADDR` está vacío, Auth conserva el flujo obligatorio de Docker
  Compose Secrets.

## Política

La política `vault/policies/auth-service.hcl` concede únicamente `read` sobre:

```hcl
path "secret/data/auth-service/*" {
  capabilities = ["read"]
}
```

El AppRole entrega tokens batch con TTL de cinco minutos. El SecretID del entorno
didáctico expira en 24 horas y admite hasta 100 autenticaciones; en producción debe
entregarse mediante response wrapping y con límites ajustados al despliegue real.

## Validaciones sin Docker

1. `scripts/tests/vault-bootstrap-test.sh` simula la CLI de Vault y verificó:
   habilitación de AppRole, política, cuatro escrituras KV, creación de RoleID y
   SecretID, permisos restrictivos y ausencia de valores sensibles en la salida.
2. `VaultSecretLoaderTest` levantó un servidor HTTP local simulado y verificó:
   login AppRole, cuatro lecturas KV v2, propagación a la configuración de Auth,
   rechazo de marcadores y no exposición del cuerpo de errores de Vault.
3. `docker-compose.yml` se analizó como YAML y contiene los servicios, perfil,
   secreto raíz y volumen AppRole esperados.
4. Los scripts POSIX pasaron validación de sintaxis.

No se descargó la imagen ni se iniciaron contenedores debido a la restricción de
almacenamiento del equipo. La evidencia de ejecución real del perfil Vault deberá
obtenerse en CI o en un equipo con capacidad suficiente.

## Seguridad y limitaciones

El modo `dev` es exclusivamente académico: no tiene sellado operativo ni
persistencia. Un despliegue real requiere TLS, almacenamiento persistente,
inicialización y unseal controlados, auditoría y distribución segura del SecretID.
El root token no debe entregarse a aplicaciones ni utilizarse para sus consultas.
