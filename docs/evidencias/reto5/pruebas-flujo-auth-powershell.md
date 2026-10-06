# Reto 5: flujo de empleado con autenticacion (PowerShell)

Guia para ejecutar desde PowerShell las solicitudes del flujo de departamento,
empleado, perfil, notificaciones y vacaciones, incluyendo la autenticacion JWT
del Reto 5.

> **Antes de empezar**
> - Ejecuta los comandos desde la raiz del repositorio.
> - Usa `curl.exe`, no `curl`: en algunas versiones de Windows PowerShell, `curl`
>   es un alias de `Invoke-WebRequest`.
> - Levanta los servicios con `docker compose up -d --build` y confirma que
>   esten disponibles con `docker compose ps`.
> - El administrador inicial usa `admin@empresa.com` y la contrasena definida
>   por `ADMIN_PASSWORD` en `.env` (por defecto, `admin123`).
> - Las fechas de vacaciones se calculan a futuro porque el servicio rechaza
>   fechas de inicio pasadas.
> - Ejecuta el flujo una sola vez con los IDs de ejemplo. Si ya existen `IT` o
>   `E001`, cambia esos IDs y sus referencias por otros que no esten usados.

## 1. Iniciar sesion y guardar el JWT

El login es publico. El resto de los endpoints del flujo requiere el token en
`Authorization: Bearer ...`.

```powershell
$G = "http://localhost:8088"
$JsonHeader = "Content-Type: application/json"

# Sin token, una ruta protegida debe responder 401.
curl.exe -i "$G/perfiles/E001"

$LoginBody = '{"email":"admin@empresa.com","password":"admin123"}'
$Login = curl.exe -sS -X POST "$G/auth/login" -H $JsonHeader --data-raw $LoginBody | ConvertFrom-Json
$Token = $Login.token
if ([string]::IsNullOrWhiteSpace($Token)) { throw "No se obtuvo el JWT. Revisa ADMIN_PASSWORD y que auth-service este disponible." }
$AuthHeader = "Authorization: Bearer $Token"
```

Si cambiaste `ADMIN_PASSWORD` en `.env`, reemplaza `admin123` por esa
contrasena. La respuesta del login contiene el token en `token`.

## 2. Crear departamento y empleado

```powershell
curl.exe -i -X POST "$G/departamentos" -H $AuthHeader -H $JsonHeader --data-raw '{"id":"IT","name":"Tecnologia","description":"Departamento de tecnologia"}'

curl.exe -i -X POST "$G/empleados" -H $AuthHeader -H $JsonHeader --data-raw '{"id":"E001","nombre":"Juan","apellido":"Perez","email":"juan.perez@empresa.com","numeroEmpleado":"EMP-2026-001","cargo":"Desarrollador Senior","area":"Tecnologia","departamentoId":"IT","fechaIngreso":"2026-03-01"}'
```

La creacion del empleado publica eventos que procesan `perfiles-service`,
`notificaciones-service` y `auth-service` de forma asincrona. Espera a que el
perfil aparezca antes de continuar:

```powershell
$ProfileStatus = ""
for ($Attempt = 0; $Attempt -lt 15; $Attempt++) {
    $ProfileStatus = curl.exe -sS -o NUL -w "%{http_code}" -H $AuthHeader "$G/perfiles/E001"
    if ($ProfileStatus -eq "200") { break }
    Start-Sleep -Seconds 2
}
if ($ProfileStatus -ne "200") { throw "El perfil no aparecio despues de esperar los eventos. Revisa docker compose ps y los logs de perfiles-service." }
```

## 3. Consultar y actualizar el perfil; revisar notificaciones

```powershell
curl.exe -i -H $AuthHeader "$G/perfiles/E001"
curl.exe -i -H $AuthHeader "$G/notificaciones/E001"

$ProfileBody = '{"telefono":"3001234567","ciudad":"Armenia","biografia":"Ingeniero de sistemas"}'
curl.exe -i -X PUT "$G/perfiles/E001" -H $AuthHeader -H $JsonHeader --data-raw $ProfileBody
```

La lista de notificaciones puede tardar unos segundos en reflejar los eventos
de bienvenida. Si aun no aparece, vuelve a consultar:

```powershell
Start-Sleep -Seconds 3
curl.exe -i -H $AuthHeader "$G/notificaciones/E001"
```

## 4. Programar vacaciones y probar validaciones

Se usan fechas futuras calculadas al ejecutar los comandos. La respuesta de
creacion incluye el ID asignado; se guarda para cancelar exactamente ese
periodo mas adelante.

```powershell
$StartDate = (Get-Date).Date.AddDays(14).ToString("yyyy-MM-dd")
$EndDate = (Get-Date).Date.AddDays(24).ToString("yyyy-MM-dd")
$VacationBody = @{
    empleadoId = "E001"
    fechaInicio = $StartDate
    fechaFin = $EndDate
} | ConvertTo-Json -Compress

$Vacation = curl.exe -sS -X POST "$G/vacaciones" -H $AuthHeader -H $JsonHeader --data-raw $VacationBody | ConvertFrom-Json
$Vacation | ConvertTo-Json
$VacationId = $Vacation.id
if ([string]::IsNullOrWhiteSpace($VacationId)) { throw "No se creo la vacacion. Revisa la respuesta y que el empleado ya exista en vacaciones-service." }
```

Verifica que se haya publicado la notificacion de tipo `VACACIONES`:

```powershell
Start-Sleep -Seconds 3
curl.exe -i -H $AuthHeader "$G/notificaciones/E001"
```

Las tres peticiones siguientes deben fallar con `400 Bad Request`: rango de
fechas incoherente, periodo solapado y empleado inexistente. Para la fecha
incoherente se mantiene el formato ISO (`yyyy-MM-dd`) y se envia una fecha de
fin anterior a la de inicio.

```powershell
$InvalidRangeBody = @{
    empleadoId = "E001"
    fechaInicio = $StartDate
    fechaFin = (Get-Date).Date.AddDays(13).ToString("yyyy-MM-dd")
} | ConvertTo-Json -Compress
curl.exe -i -X POST "$G/vacaciones" -H $AuthHeader -H $JsonHeader --data-raw $InvalidRangeBody

# El mismo periodo ya programado debe producir el error de solapamiento.
curl.exe -i -X POST "$G/vacaciones" -H $AuthHeader -H $JsonHeader --data-raw $VacationBody

$UnknownEmployeeBody = @{
    empleadoId = "NO_EXISTE"
    fechaInicio = $StartDate
    fechaFin = $EndDate
} | ConvertTo-Json -Compress
curl.exe -i -X POST "$G/vacaciones" -H $AuthHeader -H $JsonHeader --data-raw $UnknownEmployeeBody
```

## 5. Cancelar el periodo y retirar al empleado

`DELETE /vacaciones/{id}` cancela el periodo programado; no lo borra de la base
de datos. Hazlo antes de retirar al empleado, mientras su estado aun es
`PROGRAMADA`.

```powershell
curl.exe -i -X DELETE "$G/vacaciones/$VacationId" -H $AuthHeader

# El retiro responde 204 y genera eventos asincronos de desvinculacion.
curl.exe -i -X DELETE "$G/empleados/E001" -H $AuthHeader

Start-Sleep -Seconds 5
curl.exe -i -H $AuthHeader "$G/empleados?estado=RETIRADO"
curl.exe -i -H $AuthHeader "$G/notificaciones/E001"
curl.exe -i -H $AuthHeader "$G/perfiles/E001"
```

En la ultima consulta el perfil debe seguir disponible pero archivado. La
notificacion debe incluir el evento de desvinculacion.

## Notas

- `E001`, el departamento `IT`, el correo y el numero de empleado deben ser
  unicos en la base de datos. Una segunda creacion puede responder `409`.
- Se usa el JWT del administrador para este flujo porque crear departamentos,
  empleados, programar/cancelar vacaciones y retirar empleados son operaciones
  administrativas. El login inicial comprueba la autenticacion; el `401` sin
  token confirma que el gateway protege las rutas.
- La guia completa de JWT, activacion de cuentas y pruebas de roles en Git Bash
  esta en [`pruebas-curl.md`](./pruebas-curl.md).
