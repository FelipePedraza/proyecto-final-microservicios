# Reto 5 – Pruebas manuales con curl

Guía para validar el Reto 5 (JWT, RBAC y ciclo de vida de cuentas) contra el stack levantado con
`docker compose up -d --build`. Equivale a la [colección Postman](./reto5-integracion.postman_collection.json),
pero por terminal.

> Los comandos están escritos para **Git Bash** (usan `$(...)`, `sed` y `date -d`). En PowerShell no
> funcionan tal cual. Ejecútalos **en orden y en la misma terminal**, porque comparten variables.
>
> `GET /empleados` solo acepta `?estado=RETIRADO` (sin ese filtro registro-service responde 400, que no es un error de seguridad).
>
> Usan IDs nuevos (`E200`, `E201`, departamento `D02`). Si los repites, los `POST` responderán 409:
> cambia los IDs o reinicia las bases (`docker compose down -v`).

## 0. Preparación

```bash
G=http://localhost:8088
J='Content-Type: application/json'
TODAY=$(date -u +%F); TOM=$(date -u -d tomorrow +%F)

# Token de ADMIN (semilla: admin@empresa.com / ADMIN_PASSWORD del .env)
ADMIN=$(curl -s -X POST $G/auth/login -H "$J" -d '{"email":"admin@empresa.com","password":"admin123"}' | sed 's/.*"token":"\([^"]*\)".*/\1/')
echo $ADMIN | cut -c1-30
```

## 1. Autenticación y 401

| Prueba | Esperado |
|---|---|
| Sin token | 401 |
| Token alterado | 401 |
| Credenciales incorrectas | 401 |
| ADMIN lee empleados | 200 |

```bash
curl -s -w " [%{http_code}]\n" $G/empleados
curl -s -w " [%{http_code}]\n" -H "Authorization: Bearer ${ADMIN}x" $G/empleados
curl -s -w " [%{http_code}]\n" -X POST $G/auth/login -H "$J" -d '{"email":"admin@empresa.com","password":"mala"}'
curl -s -o /dev/null -w "[%{http_code}]\n" -H "Authorization: Bearer $ADMIN" "$G/empleados?estado=RETIRADO"
```

## 2. Onboarding (la cuenta se crea por evento)

`empleado.creado` → auth-service crea la cuenta en `PENDIENTE_ACTIVACION` → publica `usuario.creado`
→ notificaciones imprime el token de activación.

```bash
curl -s -X POST $G/departamentos -H "Authorization: Bearer $ADMIN" -H "$J" -d '{"id":"D02","name":"Tecnologia"}'; echo
for N in 200 201; do
curl -s -X POST $G/empleados -H "Authorization: Bearer $ADMIN" -H "$J" -d "{\"id\":\"E$N\",\"nombre\":\"User$N\",\"apellido\":\"Prueba\",\"email\":\"e$N@empresa.com\",\"numeroEmpleado\":\"N$N\",\"cargo\":\"Dev\",\"area\":\"TI\",\"departamentoId\":\"D02\",\"fechaIngreso\":\"$TODAY\"}"; echo
done
sleep 6

# Login antes de activar -> 401 (todavía no tiene contraseña)
curl -s -w " [%{http_code}]\n" -X POST $G/auth/login -H "$J" -d '{"email":"e200@empresa.com","password":"Clave12345"}'
```

## 3. Activación de la cuenta

El token viaja en el evento y se imprime en el log de notificaciones.

```bash
tok(){ docker logs reto-2-microservicios-notificaciones-service-1 2>&1 | grep "Para: $1" | grep -o 'token: [A-Za-z0-9._-]*' | tail -1 | cut -d' ' -f2; }

# Contraseña débil -> 400 (política: 8-72 caracteres, al menos una letra y un número)
curl -s -w " [%{http_code}]\n" -X POST $G/auth/reset-password -H "$J" -d "{\"resetToken\":\"$(tok e200@empresa.com)\",\"newPassword\":\"corta\"}"

# Contraseña válida -> 200
for N in 200 201; do
curl -s -w " [%{http_code}]\n" -X POST $G/auth/reset-password -H "$J" -d "{\"resetToken\":\"$(tok e$N@empresa.com)\",\"newPassword\":\"Clave12345\"}"
done

# Un reset token usado como Bearer -> 401
curl -s -w " [%{http_code}]\n" -H "Authorization: Bearer $(tok e200@empresa.com)" $G/empleados

U200=$(curl -s -X POST $G/auth/login -H "$J" -d '{"email":"e200@empresa.com","password":"Clave12345"}' | sed 's/.*"token":"\([^"]*\)".*/\1/')
```

## 4. RBAC y propiedad del recurso (USER E200)

| Prueba | Esperado |
|---|---|
| USER lee empleados | 200 |
| USER crea departamento | 403 |
| USER retira a otro empleado | 403 |
| USER edita su propio perfil | 200 |
| USER edita el perfil de otro | 403 |
| ADMIN edita el perfil de otro | 200 |

```bash
A="Authorization: Bearer $U200"
curl -s -o /dev/null -w "USER lee empleados   (200): %{http_code}\n" -H "$A" "$G/empleados?estado=RETIRADO"
curl -s -o /dev/null -w "USER crea depto      (403): %{http_code}\n" -X POST $G/departamentos -H "$A" -H "$J" -d '{"id":"X","name":"X"}'
curl -s -o /dev/null -w "USER retira empleado (403): %{http_code}\n" -X DELETE -H "$A" $G/empleados/E201
curl -s -o /dev/null -w "USER perfil propio   (200): %{http_code}\n" -X PUT $G/perfiles/E200 -H "$A" -H "$J" -d '{"telefono":"3001234567","direccion":"Calle 1","ciudad":"Bogota","biografia":"ok"}'
curl -s -o /dev/null -w "USER perfil ajeno    (403): %{http_code}\n" -X PUT $G/perfiles/E201 -H "$A" -H "$J" -d '{"telefono":"3001234567","direccion":"Calle 1","ciudad":"Bogota","biografia":"x"}'
curl -s -o /dev/null -w "ADMIN perfil ajeno   (200): %{http_code}\n" -X PUT $G/perfiles/E201 -H "Authorization: Bearer $ADMIN" -H "$J" -d '{"telefono":"3001234567","direccion":"Calle 1","ciudad":"Bogota","biografia":"x"}'
```

## 5. Cambio y recuperación de contraseña

```bash
# Cambio con clave débil -> 400
curl -s -w " [%{http_code}]\n" -X POST $G/auth/change-password -H "$A" -H "$J" -d '{"oldPassword":"Clave12345","newPassword":"123"}'

# Cambio válido -> 200, y la clave anterior deja de servir -> 401
curl -s -w " [%{http_code}]\n" -X POST $G/auth/change-password -H "$A" -H "$J" -d '{"oldPassword":"Clave12345","newPassword":"Nueva12345"}'
curl -s -o /dev/null -w "login clave vieja (401): %{http_code}\n" -X POST $G/auth/login -H "$J" -d '{"email":"e200@empresa.com","password":"Clave12345"}'

# Recuperación: publica usuario.recuperacion; el token llega por notificaciones
curl -s -X POST $G/auth/recover-password -H "$J" -d '{"email":"e200@empresa.com"}'; echo
sleep 5
curl -s -w " [%{http_code}]\n" -X POST $G/auth/reset-password -H "$J" -d "{\"resetToken\":\"$(tok e200@empresa.com)\",\"newPassword\":\"Recup12345\"}"
curl -s -o /dev/null -w "login clave recuperada (200): %{http_code}\n" -X POST $G/auth/login -H "$J" -d '{"email":"e200@empresa.com","password":"Recup12345"}'
```

## 6. Vacaciones: suspensión y reactivación

Requiere `VACACIONES_MANUAL_ENABLED=true` (endpoints `forzar-inicio` / `forzar-fin`, solo ADMIN).
Sin eso, el scheduler (`VACACIONES_CRON`) hace las transiciones por fecha.

```bash
# Una sola vez: activar el modo manual y recrear el contenedor
sed -i 's/^VACACIONES_MANUAL_ENABLED=.*/VACACIONES_MANUAL_ENABLED=true/' .env && docker compose up -d vacaciones-service && sleep 40

VID=$(curl -s -X POST $G/vacaciones -H "Authorization: Bearer $ADMIN" -H "$J" -d "{\"empleadoId\":\"E200\",\"fechaInicio\":\"$TODAY\",\"fechaFin\":\"$TOM\"}" | sed 's/.*"id":"\([^"]*\)".*/\1/'); echo $VID

# USER no puede forzar -> 403
curl -s -o /dev/null -w "USER forzar (403): %{http_code}\n" -X POST -H "$A" $G/vacaciones/$VID/forzar-inicio

# Inicio de vacaciones -> cuenta SUSPENDIDA_TEMPORAL
curl -s -X POST -H "Authorization: Bearer $ADMIN" $G/vacaciones/$VID/forzar-inicio; echo
sleep 6
curl -s -w " [%{http_code}] (esperado 403: suspendida)\n" -X POST $G/auth/login -H "$J" -d '{"email":"e200@empresa.com","password":"Recup12345"}'

# Recuperar la contraseña durante la suspensión NO debe reactivar la cuenta
curl -s -X POST $G/auth/recover-password -H "$J" -d '{"email":"e200@empresa.com"}' >/dev/null; sleep 5
curl -s -o /dev/null -X POST $G/auth/reset-password -H "$J" -d "{\"resetToken\":\"$(tok e200@empresa.com)\",\"newPassword\":\"Otra12345\"}"
curl -s -w " [%{http_code}] (sigue 403)\n" -X POST $G/auth/login -H "$J" -d '{"email":"e200@empresa.com","password":"Otra12345"}'

# Fin de vacaciones -> cuenta ACTIVA
curl -s -X POST -H "Authorization: Bearer $ADMIN" $G/vacaciones/$VID/forzar-fin; echo
sleep 6
curl -s -o /dev/null -w "login tras vacaciones (200): %{http_code}\n" -X POST $G/auth/login -H "$J" -d '{"email":"e200@empresa.com","password":"Otra12345"}'
```

## 7. Caso borde: retiro durante vacaciones

Si el empleado se retira estando de vacaciones, `vacaciones.finalizadas` **no** debe reactivar la cuenta.

```bash
VID2=$(curl -s -X POST $G/vacaciones -H "Authorization: Bearer $ADMIN" -H "$J" -d "{\"empleadoId\":\"E201\",\"fechaInicio\":\"$TODAY\",\"fechaFin\":\"$TOM\"}" | sed 's/.*"id":"\([^"]*\)".*/\1/')
curl -s -o /dev/null -X POST -H "Authorization: Bearer $ADMIN" $G/vacaciones/$VID2/forzar-inicio; sleep 5
curl -s -o /dev/null -w "retiro (204): %{http_code}\n" -X DELETE -H "Authorization: Bearer $ADMIN" $G/empleados/E201; sleep 5
curl -s -o /dev/null -X POST -H "Authorization: Bearer $ADMIN" $G/vacaciones/$VID2/forzar-fin; sleep 5
curl -s -w " [%{http_code}] (debe seguir 403: NO se reactiva)\n" -X POST $G/auth/login -H "$J" -d '{"email":"e201@empresa.com","password":"Clave12345"}'
docker logs auth-service 2>&1 | grep "E201" | tail -4 | cut -c60-260
```

En el log de auth-service debe aparecer `vacaciones.finalizadas ignorado: ... DESACTIVADA_PERMANENTE`.

## 8. Notificaciones generadas

Formato esperado: `[NOTIFICACIÓN] Tipo: SEGURIDAD|CUENTA|... | Para: ... | Mensaje: ...`

```bash
docker logs reto-2-microservicios-notificaciones-service-1 2>&1 | grep "NOTIF" | tail -12 | cut -c1-170
```

## Al terminar

```bash
sed -i 's/^VACACIONES_MANUAL_ENABLED=.*/VACACIONES_MANUAL_ENABLED=false/' .env && docker compose up -d vacaciones-service
```

## Resultados esperados (resumen)

| Caso | Código |
|---|---|
| Sin token / token alterado / reset token como Bearer | 401 |
| Credenciales incorrectas / contraseña anterior | 401 |
| USER escribiendo (crear, retirar, forzar vacaciones) | 403 |
| USER edita perfil ajeno | 403 |
| Contraseña que no cumple la política | 400 |
| Login con cuenta suspendida o desactivada | 403 |
| Retiro durante vacaciones y luego fin de vacaciones | sigue 403 |
