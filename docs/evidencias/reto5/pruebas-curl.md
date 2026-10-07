# Reto 5 – Pruebas manuales con curl

Guía para validar el Reto 5 (JWT, RBAC y ciclo de vida de cuentas por eventos) contra el stack levantado con
`docker compose up -d --build`. Equivale a la [colección Postman](./reto5-integracion.postman_collection.json),
pero por terminal y verificando también los eventos (logs de auth y notificaciones, y RabbitMQ).

> **Requisitos**
> - Comandos para **Git Bash** (usan `$(...)`, `sed`, `awk` y `date -d`). En PowerShell no funcionan tal cual.
> - Ejecútalos **en orden y en la misma terminal**: comparten variables.
> - `.env` con `VACACIONES_MANUAL_ENABLED=true` (secciones 7 y 8) y los contenedores `healthy`
>   (`docker compose ps`). Si acabas de recrear un contenedor, espera ~1 minuto o ejecuta la sección 0.
> - Usan IDs `E310`, `E311`, `E312` y el departamento `D04`. Si repites la guía, cámbialos
>   (un empleado repetido responde **409**) o reinicia las bases con `docker compose down -v`.
> - `GET /empleados` solo acepta `?estado=RETIRADO`; sin ese filtro empleado-service responde 400
>   (no es un error de seguridad).

## 0. Preparación y calentamiento

El primer request a un servicio recién (re)creado puede devolver el 503 del *fallback* del gateway
(`"error":"service_unavailable"`). La función `warm` repite hasta obtener otra respuesta.

```bash
G=http://localhost:8088
J='Content-Type: application/json'
TODAY=$(date -u +%F); TOM=$(date -u -d tomorrow +%F); AFTER=$(date -u -d "+2 days" +%F)
NOTI=reto-2-microservicios-notificaciones-service-1
RMQ_PW=$(grep '^RABBITMQ_PASSWORD=' .env | cut -d= -f2-)

login(){ curl -s -X POST $G/auth/login -H "$J" -d "{\"email\":\"$1\",\"password\":\"$2\"}" | sed 's/.*"token":"\([^"]*\)".*/\1/'; }
b64(){ echo "$1" | tr '_-' '/+' | awk '{while(length($0)%4)$0=$0"=";print}' | base64 -d 2>/dev/null; echo; }
tok(){ docker logs $NOTI 2>&1 | grep "Para: $1" | grep -o 'token: [A-Za-z0-9._-]*' | tail -1 | cut -d' ' -f2; }
rmq(){ curl -s -u "admin:$RMQ_PW" "http://localhost:15672/api/exchanges/%2F/$1" | grep -o '"publish_in":[0-9]*' | head -1 | cut -d: -f2; }
warm(){ for i in 1 2 3 4 5 6 7 8 9 10; do c=$(curl -s -o /dev/null -w "%{http_code}" -H "Authorization: Bearer $1" "$G$2"); [ "$c" != "503" ] && break; sleep 4; done; echo "$2 -> $c"; }

W=$(login admin@empresa.com admin123)
warm $W "/empleados?estado=RETIRADO"; warm $W "/departamentos"; warm $W "/vacaciones"
```

`tok` extrae el último token que notificaciones registró para un correo; `rmq` lee del API de RabbitMQ
cuántos mensajes han entrado a un exchange.

## 1. JWT: estructura, 401 y token alterado

```bash
ADMIN=$(login admin@empresa.com admin123)
echo "header : $(b64 $(echo $ADMIN | cut -d. -f1))"
echo "payload: $(b64 $(echo $ADMIN | cut -d. -f2))"
```

Esperado:

```
header : {"typ":"JWT","alg":"HS256"}
payload: {"role":"ADMIN","jti":"<uuid>","sub":"ADMIN-001","iat":...,"exp":...}
```

- `typ` y `alg` en el header; `sub`, `role`, `iat`, `exp` y `jti` (UUID único) en el contenido.
- `exp - iat = 3600` (access token de 1 hora).

```bash
# Cada login genera un jti distinto
ADMIN2=$(login admin@empresa.com admin123)
echo "jti distinto: $( [ "$(echo $ADMIN | cut -d. -f2)" != "$(echo $ADMIN2 | cut -d. -f2)" ] && echo SI || echo NO )"

# Sin token -> 401
curl -s -w " [%{http_code}]\n" "$G/empleados?estado=RETIRADO"
# Token alterado -> 401
curl -s -w " [%{http_code}]\n" -H "Authorization: Bearer ${ADMIN}x" "$G/empleados?estado=RETIRADO"
# Credenciales incorrectas -> 401
curl -s -w " [%{http_code}]\n" -X POST $G/auth/login -H "$J" -d '{"email":"admin@empresa.com","password":"mala"}'
# ADMIN lee -> 200
curl -s -o /dev/null -w "ADMIN lee [%{http_code}]\n" -H "Authorization: Bearer $ADMIN" "$G/empleados?estado=RETIRADO"
```

## 2. Onboarding: crear empleados y verificar los eventos

Flujo: `POST /empleados` → empleado-service publica `empleado.creado` en `empleados_exchange` → auth-service crea la
cuenta `PENDIENTE_ACTIVACION` y publica `usuario.creado` en `auth_exchange` → notificaciones registra el correo
de bienvenida con el token de activación.

```bash
echo "publish_in antes: empleados=$(rmq empleados_exchange) auth=$(rmq auth_exchange)"

curl -s -X POST $G/departamentos -H "Authorization: Bearer $ADMIN" -H "$J" -d '{"id":"D04","name":"Tecnologia"}'; echo

for N in 310 311 312; do
curl -s -X POST $G/empleados -H "Authorization: Bearer $ADMIN" -H "$J" -d "{\"id\":\"E$N\",\"nombre\":\"User$N\",\"apellido\":\"Prueba\",\"email\":\"e$N@empresa.com\",\"numeroEmpleado\":\"N$N\",\"cargo\":\"Dev\",\"area\":\"TI\",\"departamentoId\":\"D04\",\"fechaIngreso\":\"$TODAY\"}"; echo
done

# Empleado repetido -> 409
curl -s -w " [%{http_code}]\n" -X POST $G/empleados -H "Authorization: Bearer $ADMIN" -H "$J" -d "{\"id\":\"E310\",\"nombre\":\"User310\",\"apellido\":\"Prueba\",\"email\":\"e310@empresa.com\",\"numeroEmpleado\":\"N310\",\"cargo\":\"Dev\",\"area\":\"TI\",\"departamentoId\":\"D04\",\"fechaIngreso\":\"$TODAY\"}"

sleep 8
```

Verificación de los eventos:

```bash
# 1) RabbitMQ: los contadores suben (empleados +3, auth +3)
echo "publish_in despues: empleados=$(rmq empleados_exchange) auth=$(rmq auth_exchange)"

# 2) auth-service creó las cuentas
docker logs auth-service 2>&1 | grep "Cuenta creada" | tail -3 | cut -c60-230

# 3) notificaciones registró el correo de bienvenida (Tipo: SEGURIDAD)
docker logs $NOTI 2>&1 | grep "Para: e31" | cut -c1-120

# 4) La cuenta existe pero aún no tiene contraseña -> 401
curl -s -w " [%{http_code}]\n" -X POST $G/auth/login -H "$J" -d '{"email":"e310@empresa.com","password":"Clave12345"}'
```

## 3. Activación de la cuenta (primer `reset-password`)

```bash
# Contraseña débil -> 400 (política: 8-72 caracteres, al menos una letra y un número)
curl -s -w " [%{http_code}]\n" -X POST $G/auth/reset-password -H "$J" -d "{\"resetToken\":\"$(tok e310@empresa.com)\",\"newPassword\":\"corta\"}"

# Contraseña válida -> 200
for N in 310 311 312; do
curl -s -w " [%{http_code}] E$N\n" -X POST $G/auth/reset-password -H "$J" -d "{\"resetToken\":\"$(tok e$N@empresa.com)\",\"newPassword\":\"Clave12345\"}"
done
```

Inspección del reset token y del token de usuario:

```bash
R=$(tok e310@empresa.com)
echo "reset header : $(b64 $(echo $R | cut -d. -f1))"
echo "reset payload: $(b64 $(echo $R | cut -d. -f2))"
# Un reset token usado como Bearer -> 401
curl -s -w " [%{http_code}] reset como Bearer\n" -H "Authorization: Bearer $R" "$G/empleados?estado=RETIRADO"

U310=$(login e310@empresa.com Clave12345)
echo "user payload: $(b64 $(echo $U310 | cut -d. -f2))"
```

Esperado: el reset token lleva `"type":"RESET_PASSWORD"` (y `typ`/`jti`); el token de usuario lleva
`"role":"USER"` y `"sub":"E310"` (el id del empleado), sin `type`.

La activación publica `cuenta.activada` (motivo `ACTIVACION_INICIAL`); notificaciones muestra
"Tu cuenta fue activada correctamente" (Tipo: CUENTA).

## 4. RBAC y propiedad del recurso (USER E310)

| Prueba | Esperado |
|---|---|
| USER lee empleados | 200 |
| USER crea departamento | 403 |
| USER retira a otro empleado | 403 |
| USER edita su propio perfil | 200 |
| USER edita el perfil de otro | 403 |
| ADMIN edita el perfil de otro | 200 |

```bash
A="Authorization: Bearer $U310"
P='{"telefono":"3001234567","direccion":"Calle 1","ciudad":"Bogota","biografia":"ok"}'
ADMIN=$(login admin@empresa.com admin123)

curl -s -o /dev/null -w "USER lee            (200): %{http_code}\n" -H "$A" "$G/empleados?estado=RETIRADO"
curl -s -o /dev/null -w "USER crea depto     (403): %{http_code}\n" -X POST $G/departamentos -H "$A" -H "$J" -d '{"id":"X","name":"X"}'
curl -s -o /dev/null -w "USER retira         (403): %{http_code}\n" -X DELETE -H "$A" $G/empleados/E311
curl -s -o /dev/null -w "USER perfil propio  (200): %{http_code}\n" -X PUT $G/perfiles/E310 -H "$A" -H "$J" -d "$P"
curl -s -o /dev/null -w "USER perfil ajeno   (403): %{http_code}\n" -X PUT $G/perfiles/E311 -H "$A" -H "$J" -d "$P"
curl -s -o /dev/null -w "ADMIN perfil ajeno  (200): %{http_code}\n" -X PUT $G/perfiles/E311 -H "Authorization: Bearer $ADMIN" -H "$J" -d "$P"
```

## 5. Cambio y recuperación de contraseña

```bash
# Cambio con clave débil -> 400
curl -s -o /dev/null -w "change débil          (400): %{http_code}\n" -X POST $G/auth/change-password -H "$A" -H "$J" -d '{"oldPassword":"Clave12345","newPassword":"123"}'
# Contraseña actual incorrecta -> 400
curl -s -o /dev/null -w "change vieja mala     (400): %{http_code}\n" -X POST $G/auth/change-password -H "$A" -H "$J" -d '{"oldPassword":"Equivocada1","newPassword":"Nueva12345"}'
# Cambio válido -> 200; la anterior deja de servir -> 401
curl -s -o /dev/null -w "change ok             (200): %{http_code}\n" -X POST $G/auth/change-password -H "$A" -H "$J" -d '{"oldPassword":"Clave12345","newPassword":"Nueva12345"}'
curl -s -o /dev/null -w "login clave vieja     (401): %{http_code}\n" -X POST $G/auth/login -H "$J" -d '{"email":"e310@empresa.com","password":"Clave12345"}'

# Recuperación: siempre 200 (no revela si el correo existe)
curl -s -o /dev/null -w "recover inexistente   (200): %{http_code}\n" -X POST $G/auth/recover-password -H "$J" -d '{"email":"nadie@empresa.com"}'
curl -s -o /dev/null -w "recover existente     (200): %{http_code}\n" -X POST $G/auth/recover-password -H "$J" -d '{"email":"e310@empresa.com"}'
sleep 6
# Se publicó usuario.recuperacion; notificaciones lo registra (Tipo: SEGURIDAD)
docker logs $NOTI 2>&1 | grep "Para: e310" | grep "recuperaci" | tail -1 | cut -c1-110

curl -s -o /dev/null -w "reset recuperación    (200): %{http_code}\n" -X POST $G/auth/reset-password -H "$J" -d "{\"resetToken\":\"$(tok e310@empresa.com)\",\"newPassword\":\"Recup12345\"}"
curl -s -o /dev/null -w "login clave recuperada(200): %{http_code}\n" -X POST $G/auth/login -H "$J" -d '{"email":"e310@empresa.com","password":"Recup12345"}'
```

Recuperar la contraseña de una cuenta ya activa **no** publica `cuenta.activada`.

## 6. Vacaciones: reglas de negocio

```bash
# Vacación que empieza mañana (el scheduler no la toca todavía)
VID=$(curl -s -X POST $G/vacaciones -H "Authorization: Bearer $ADMIN" -H "$J" -d "{\"empleadoId\":\"E310\",\"fechaInicio\":\"$TOM\",\"fechaFin\":\"$AFTER\"}" | sed 's/.*"id":"\([^"]*\)".*/\1/'); echo $VID

# Período solapado del mismo empleado -> 400 ("solapamiento")
curl -s -w " [%{http_code}]\n" -X POST $G/vacaciones -H "Authorization: Bearer $ADMIN" -H "$J" -d "{\"empleadoId\":\"E310\",\"fechaInicio\":\"$TOM\",\"fechaFin\":\"$TOM\"}"

# Fecha en el pasado -> 400
curl -s -w " [%{http_code}]\n" -X POST $G/vacaciones -H "Authorization: Bearer $ADMIN" -H "$J" -d '{"empleadoId":"E310","fechaInicio":"2020-01-01","fechaFin":"2020-01-05"}'

# USER no puede programar vacaciones -> 403
curl -s -o /dev/null -w "USER programa (403): %{http_code}\n" -X POST $G/vacaciones -H "$A" -H "$J" -d "{\"empleadoId\":\"E310\",\"fechaInicio\":\"$TOM\",\"fechaFin\":\"$AFTER\"}"
```

## 7. Ciclo de vida con `forzar-inicio` / `forzar-fin` (modo manual)

Requiere `VACACIONES_MANUAL_ENABLED=true` en `.env` (luego `docker compose up -d vacaciones-service`).
Solo ADMIN (el gateway exige rol ADMIN para toda escritura de vacaciones).

```bash
# USER no puede forzar -> 403
curl -s -o /dev/null -w "USER forzar (403): %{http_code}\n" -X POST -H "$A" $G/vacaciones/$VID/forzar-inicio

# vacaciones.iniciadas -> cuenta SUSPENDIDA_TEMPORAL
curl -s -o /dev/null -w "forzar-inicio (200): %{http_code}\n" -X POST -H "Authorization: Bearer $ADMIN" $G/vacaciones/$VID/forzar-inicio
sleep 6
curl -s -w " [%{http_code}] suspendida\n" -X POST $G/auth/login -H "$J" -d '{"email":"e310@empresa.com","password":"Recup12345"}'

# Recuperar la contraseña durante la suspensión NO reactiva la cuenta
curl -s -o /dev/null -X POST $G/auth/recover-password -H "$J" -d '{"email":"e310@empresa.com"}'; sleep 5
curl -s -o /dev/null -w "reset durante suspensión (200): %{http_code}\n" -X POST $G/auth/reset-password -H "$J" -d "{\"resetToken\":\"$(tok e310@empresa.com)\",\"newPassword\":\"Otra12345\"}"
curl -s -w " [%{http_code}] sigue suspendida\n" -X POST $G/auth/login -H "$J" -d '{"email":"e310@empresa.com","password":"Otra12345"}'

# vacaciones.finalizadas -> cuenta ACTIVA
curl -s -o /dev/null -w "forzar-fin (200): %{http_code}\n" -X POST -H "Authorization: Bearer $ADMIN" $G/vacaciones/$VID/forzar-fin
sleep 6
curl -s -o /dev/null -w "login tras vacaciones (200): %{http_code}\n" -X POST $G/auth/login -H "$J" -d '{"email":"e310@empresa.com","password":"Otra12345"}'
```

Esperado en las notificaciones: "suspendida temporalmente…" (Tipo: CUENTA) y luego
"Tus vacaciones terminaron: tu cuenta fue reactivada…".

## 8. Caso borde: retiro durante vacaciones

Si el empleado se retira estando de vacaciones, `vacaciones.finalizadas` **no** debe reactivar la cuenta.

```bash
VID2=$(curl -s -X POST $G/vacaciones -H "Authorization: Bearer $ADMIN" -H "$J" -d "{\"empleadoId\":\"E311\",\"fechaInicio\":\"$TOM\",\"fechaFin\":\"$AFTER\"}" | sed 's/.*"id":"\([^"]*\)".*/\1/')
curl -s -o /dev/null -X POST -H "Authorization: Bearer $ADMIN" $G/vacaciones/$VID2/forzar-inicio; sleep 5
curl -s -o /dev/null -w "retiro (204): %{http_code}\n" -X DELETE -H "Authorization: Bearer $ADMIN" $G/empleados/E311; sleep 5
curl -s -o /dev/null -w "forzar-fin del retirado (200): %{http_code}\n" -X POST -H "Authorization: Bearer $ADMIN" $G/vacaciones/$VID2/forzar-fin; sleep 5

curl -s -w " [%{http_code}] sigue bloqueado\n" -X POST $G/auth/login -H "$J" -d '{"email":"e311@empresa.com","password":"Clave12345"}'
docker logs auth-service 2>&1 | grep "E311" | tail -4 | cut -c60-250
# Aparece en la auditoría de retirados
curl -s -H "Authorization: Bearer $ADMIN" "$G/empleados?estado=RETIRADO" | grep -o '"id":"E311"' | head -1
```

Esperado en el log de auth-service, en este orden: `pasó a SUSPENDIDA_TEMPORAL por vacaciones`,
`pasó a DESACTIVADA_PERMANENTE por retiro` y `vacaciones.finalizadas ignorado: … DESACTIVADA_PERMANENTE`.
El login responde 403 con `Estado actual: DESACTIVADA_PERMANENTE`.

## 9. Scheduler automático (sin forzar)

El scheduler corre según `VACACIONES_CRON` (por defecto cada minuto). Una vacación que empieza **hoy** pasa a
`EN_CURSO` sola y suspende la cuenta.

```bash
VID3=$(curl -s -X POST $G/vacaciones -H "Authorization: Bearer $ADMIN" -H "$J" -d "{\"empleadoId\":\"E312\",\"fechaInicio\":\"$TODAY\",\"fechaFin\":\"$TOM\"}" | sed 's/.*"id":"\([^"]*\)".*/\1/'); echo $VID3

# Espera hasta ~1 minuto a que el cron la inicie; el login de E312 pasa a 403
for i in $(seq 1 14); do c=$(curl -s -o /dev/null -w "%{http_code}" -X POST $G/auth/login -H "$J" -d '{"email":"e312@empresa.com","password":"Clave12345"}'); [ "$c" = "403" ] && break; sleep 6; done
echo "login E312 tras el cron (403): $c"
curl -s -H "Authorization: Bearer $ADMIN" "$G/vacaciones?empleadoId=E312" | grep -o '"estado":"[A-Z_]*"'    # EN_CURSO
```

La transición a `FINALIZADA` ocurre el día siguiente a `fechaFin` (`fechaFin < hoy`), por eso se prueba con
`forzar-fin` en la sección 7.

## 10. Resumen de notificaciones y colas

```bash
# Formato: [NOTIFICACIÓN] Tipo: SEGURIDAD|CUENTA|VACACIONES|DESVINCULACION | Para: ... | Mensaje: ...
docker logs $NOTI 2>&1 | grep "NOTIF" | grep -E "e31[012]" | cut -c1-150 | tail -16

# Colas de auth y notificaciones vacías y DLQ en 0 (ningún evento fallido)
for Q in auth.empleados notificaciones.auth auth.empleados.dlq notificaciones.auth.dlq; do
echo "$Q: $(curl -s -u admin:$RMQ_PW http://localhost:15672/api/queues/%2F/$Q | grep -o '"messages":[0-9]*' | head -1)"
done
```

Esperado: todas las colas en `"messages":0`. Un número mayor en una `.dlq` indica eventos mal formados.

## Al terminar

```bash
sed -i 's/^VACACIONES_MANUAL_ENABLED=.*/VACACIONES_MANUAL_ENABLED=false/' .env && docker compose up -d vacaciones-service
```

## Resultados esperados (resumen)

| Caso | Código |
|---|---|
| Sin token / token alterado / reset token como Bearer | 401 |
| Credenciales incorrectas, contraseña anterior o cuenta sin activar | 401 |
| USER escribiendo (crear, retirar, programar o forzar vacaciones) | 403 |
| USER edita el perfil de otro | 403 |
| Login con cuenta suspendida o desactivada | 403 |
| Contraseña que no cumple la política / contraseña actual incorrecta | 400 |
| Vacaciones solapadas o en el pasado | 400 |
| Empleado con id repetido | 409 |
| Retiro durante vacaciones y luego fin de vacaciones | sigue 403 |
| Recuperar contraseña de un correo inexistente | 200 (no revela nada) |

## Si algo falla

| Síntoma | Causa probable |
|---|---|
| `"error":"service_unavailable"` (503) | Servicio recién recreado o arrancando; ejecuta la sección 0 o espera 1 minuto |
| `tok` devuelve vacío | Notificaciones aún no procesó el evento; espera unos segundos y repite |
| `login` 401 tras activar | El consumidor de auth no procesó `empleado.creado`; revisa `docker logs auth-service` |
| `forzar-*` responde 404 | `VACACIONES_MANUAL_ENABLED` está en `false` |
| `password authentication failed` en una BD | Volumen de una ejecución anterior con otra contraseña; borra ese volumen y levanta de nuevo |
