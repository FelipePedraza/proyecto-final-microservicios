# Evidencia de rotación del JWT Secret

Fecha: 2026-11-18 (America/Bogota)

## Alcance

Se rotó el archivo local `secrets/jwt_secret.txt` con
`scripts/rotate-secret.sh jwt`. El procedimiento no imprimió ni registró el valor
anterior, el valor nuevo ni sus fingerprints. `secrets/` y su carpeta de backups
permanecen excluidos de Git.

## Comprobaciones realizadas

1. El valor anterior era válido y no era un marcador de plantilla.
2. El script creó un backup local restringido dentro de `secrets/backups/`.
3. El nuevo valor se generó con `openssl rand -base64 48` y tiene más de 32 bytes.
4. La comparación local confirmó que el valor cambió, sin mostrar ninguno de los
   dos valores.
5. El secreto nuevo, el backup y el directorio conservaron permisos `600`, `600` y
   `700`, respectivamente.
6. `scripts/tests/rotate-secret-test.sh` pasó y comprobó reemplazo atómico, backup,
   permisos, cambio efectivo y ausencia de valores sensibles en la salida.
7. La prueba `JWTUtilsTest.unTokenEmitidoAntesDeLaRotacionQuedaInvalidado` pasó:
   un token firmado con la clave anterior es rechazado al validarlo con la clave
   rotada.

## Impacto observado

La firma HMAC de un token existente deja de validar después de cambiar el JWT
Secret. En ejecución, esto obliga a los usuarios a iniciar sesión nuevamente y
también invalida tokens pendientes de activación o recuperación.

Los consumidores que deben reiniciarse con el mismo valor nuevo son
`auth-service`, que firma tokens, y `gateway-service`, que valida access tokens.
No se levantaron contenedores ni se ejecutaron reinicios porque la validación de
este equipo debe realizarse sin Docker por restricción de almacenamiento.

## Rollback

Durante una ventana de mantenimiento puede restaurarse el backup local generado por
el script sobre `secrets/jwt_secret.txt`, conservar permisos `600` y reiniciar Auth y
Gateway. El rollback vuelve a aceptar firmas de la clave anterior y deja de aceptar
las emitidas con la clave nueva. Tras verificar la rotación, el backup debe eliminarse
de forma segura para no mantener credenciales antiguas utilizables.
