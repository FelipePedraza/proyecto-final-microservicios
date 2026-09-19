# Reto 3 — Circuit Breaker y Fallback en RegistroService

Este documento describe **qué se hizo**, **en qué archivos** y **por qué se tomó cada decisión** para
proteger la llamada síncrona de `RegistroService` (empleados) hacia `DepartamentosService`.

## 1. Requisito y resultado

| Requisito | Resultado |
|-----------|-----------|
| Librería de resiliencia en el servicio de empleados | **Polly 8.8.0** (`Polly`), en `RegistroService.csproj` |
| 3 a 5 fallos consecutivos abren el circuito | 3 por defecto, configurable de 3 a 5 |
| Timeout de llamada de 5 s | 5 s por intento HTTP |
| Circuito abierto de 30 a 60 s | 30 s por defecto, configurable de 30 a 60 s |
| Estados CLOSED, OPEN, HALF_OPEN | Implementados, con log de cada transición y endpoint para consultarlos |
| Respuesta de fallback (`PENDIENTE VALIDACION`) | El empleado se registra con estado `PENDIENTE_VALIDACION` y responde `201` |

## 2. Cómo funciona

```
POST /empleados
   │
   ▼
EmpleadoService.RegistrarAsync
   │  ¿existe el departamento?
   ▼
DepartamentoClient.ExisteAsync ── reintentos (hasta 3, backoff 1 s, 2 s)
   │                                  │
   │                                  ▼ cada intento pasa por
   │                         DepartamentoCircuitBreaker (Polly)
   │                                  │
   │                                  ▼ timeout 5 s por intento
   │                            GET departamentos/{id}
   │
   ├─ existe .............................. se registra ACTIVO
   ├─ 404 (no existe) ..................... 400 "El departamento ... no existe"
   └─ no disponible / circuito abierto .... FALLBACK: se registra PENDIENTE_VALIDACION (201)
```

Máquina de estados del circuito:

```
            3 intentos fallidos consecutivos                 pasan 30 s
  CLOSED ─────────────────────────────────► OPEN ─────────────────────────► HALF_OPEN
     ▲                                        ▲                                  │
     │        la llamada de prueba funciona   │   la llamada de prueba falla     │
     └────────────────────────────────────────┼──────────────────────────────────┤
                                              └──────────────────────────────────┘
```

- **CLOSED**: las llamadas pasan. Un éxito reinicia la cuenta de fallos.
- **OPEN**: las llamadas se rechazan al instante, sin tocar la red.
- **HALF_OPEN**: se deja pasar **una** llamada de prueba; las demás se rechazan mientras tanto.

## 3. Archivos

### Nuevos

| Archivo | Qué contiene | Por qué |
|---------|--------------|---------|
| `Infrastructure/Departamentos/DepartamentoCircuitBreaker.cs` | Singleton que envuelve la política de Polly, registra las transiciones y expone `Estado` (`CLOSED`/`OPEN`/`HALF_OPEN`). | Es único y compartido: el estado del circuito debe ser el mismo para todas las peticiones. Aísla a Polly del resto del código. |
| `Infrastructure/Departamentos/DepartamentosResilienceOptions.cs` | Parámetros (fallos, timeout, tiempo abierto, espera de reintento) y `EsValida()`. | Centraliza la configuración y permite validar los rangos que exige el reto. |
| `Infrastructure/Departamentos/DepartamentosCircuitoAbiertoException.cs` | Excepción que indica "rechazado por circuito abierto". Hereda de `DepartamentosNoDisponibleException`. | Distingue el motivo (útil en logs y pruebas) sin romper el código que ya capturaba la excepción base. |
| `RegistroService.Tests/DepartamentosResilienceOptionsTests.cs` | Pruebas de los rangos y de que una configuración inválida impide arrancar. | Los rangos son parte del requisito. |

### Modificados

| Archivo | Cambio | Por qué |
|---------|--------|---------|
| `RegistroService.csproj` | Se agrega `Polly` 8.8.0. | Librería de resiliencia pedida por el reto. |
| `Infrastructure/Departamentos/DepartamentoClient.cs` | Reintentos por fuera, breaker por dentro (cada intento pasa por el breaker). Se lee el timeout y la espera desde las opciones. Si el circuito se abre, se deja de reintentar. | Ver decisión 4.2. |
| `Infrastructure/Departamentos/DepartamentosNoDisponibleException.cs` | Deja de ser `sealed`. | Para que `DepartamentosCircuitoAbiertoException` herede de ella. |
| `Domain/Services/EmpleadoService.cs` | `VerificarDepartamentoAsync` aplica el fallback cuando Departamentos no está disponible. | Es la regla de negocio "si no puedo validar, registro como pendiente". Ver decisión 4.3. |
| `Domain/Enums/EstadoEmpleado.cs` | Nuevo valor `PendienteValidacion`. | Estado del fallback. |
| `Domain/Entities/Empleado.cs` | Método `MarcarPendienteValidacion()`. | El estado tiene setter privado; el cambio se hace con un método de dominio. |
| `API/Extensions/MappingExtensions.cs` | Mapea `PendienteValidacion` a `"PENDIENTE_VALIDACION"`. | Es el valor canónico que ve el cliente. |
| `API/DTOs/EmpleadoResponse.cs` | Documenta el nuevo estado. | Contrato de la API. |
| `Program.cs` | Registra opciones (con validación al inicio) y el breaker singleton; el timeout del `HttpClient` sale de las opciones; nuevo endpoint `GET /health/circuit-breaker`; documenta el `201` de fallback. | Cableado y observabilidad. |
| `appsettings.json` | Sección `Departamentos:Resilience`. | Valores por defecto. |
| `docker-compose.yml`, `.env.example` | Variables `CB_FALLOS_CONSECUTIVOS`, `CB_TIMEOUT_LLAMADA`, `CB_DURACION_CIRCUITO_ABIERTO`. | Permite ajustar el breaker sin reconstruir la imagen. |
| `RegistroService.Tests/DepartamentoClientTests.cs` | Se adaptan los 3 tests existentes y se agregan 6 sobre la máquina de estados. | Ver sección 6. |
| `RegistroService.Tests/EmpleadoServiceTests.cs` | 4 tests del fallback. | |
| `RegistroService.Tests/EmpleadosEndpointsTests.cs` | 2 tests: fallback por HTTP y `/health/circuit-breaker`. El fake de Departamentos simula caída con el id `DEP-CAIDO`. | |
| `README.md`, `EVIDENCIAS.md` | Sección del Reto 3 y estado en la tabla. | |

## 4. Decisiones y por qué

### 4.1 Polly con la API clásica (`CircuitBreakerAsync`)
El reto pide "N fallos **consecutivos**". `Policy.Handle<...>().CircuitBreakerAsync(N, duración)`
implementa exactamente eso y expone `CircuitState` y los callbacks `onBreak`, `onReset` y `onHalfOpen`
para registrar las transiciones. La API nueva (`ResiliencePipeline`) modela el umbral como un ratio de
fallos dentro de una ventana de tiempo, que no equivale a "N seguidos". La API clásica sigue incluida en
el paquete `Polly` 8.x.

### 4.2 Los fallos se cuentan por intento HTTP, no por petición del usuario
**Este cambio salió de una prueba real, no de la teoría.** La primera versión colocaba el breaker por
fuera del bucle de reintentos, de modo que contaba peticiones fallidas. Con Departamentos detenido, una
petición fallida tardaba ~8.8 s (3 intentos, esperas de 1 s y 2 s, y resolución DNS lenta). El gateway
del proyecto corta a los 5 s, así que la petición se abortaba antes de registrar el fallo: **el circuito
nunca se abría y el empleado no se guardaba**. Se reprodujo con un cliente que corta a los 5 s.

La corrección fue el orden estándar (el mismo de `AddStandardResilienceHandler` de Microsoft): reintentos
por fuera, breaker por dentro. Cada intento fallido cuenta, por lo que el circuito se abre aunque el
cliente cancele. Si el circuito se abre a mitad de los reintentos, se deja de reintentar.

Consecuencia aceptada: con el umbral en 3 y 3 reintentos, una sola petición fallida puede abrir el
circuito. Con umbral 5 hacen falta dos peticiones.

### 4.3 El fallback vive en el servicio de dominio, y se activa también antes de abrir el circuito
`EmpleadoService` captura `DepartamentosNoDisponibleException`, que cubre tanto "intentos agotados"
como "circuito abierto". Así el usuario recibe el mismo comportamiento durante toda la caída y no solo
después de que el circuito se abra. Las validaciones propias (email o número duplicado) se siguen
aplicando en el repositorio.

### 4.4 Qué cuenta como fallo
Cuentan: timeout, conexión rechazada o DNS, y respuestas 408, 429 y 5xx. **No cuenta el 404**: es una
respuesta de negocio válida ("el departamento no existe") y sigue devolviendo `400`. Un 4xx inesperado
(por ejemplo 401) cuenta como fallo pero no se reintenta, porque reintentar no lo arregla.

### 4.5 Respuesta `201 Created` con estado `PENDIENTE_VALIDACION`
El empleado sí queda persistido, así que `201` es correcto y el cliente lo ve consultable con
`GET /empleados/{id}`. Un `503` haría fallar un registro que se puede aceptar. La respuesta lleva el
estado, de modo que el cliente sabe que falta validar el departamento.

### 4.6 Sin cambio de esquema en la base de datos
El estado se guarda como texto (`PendienteValidacion`, 19 caracteres) en una columna `VARCHAR(30)` sin
restricción `CHECK`, por lo que `database/registro/001-schema.sql` no cambia.

### 4.7 Configuración validada al arrancar
Los rangos del reto (3 a 5 fallos, 30 a 60 s) se validan con `ValidateOnStart`. Con un valor fuera de
rango el servicio **no arranca**, en lugar de funcionar con un breaker mal calibrado en silencio.

### 4.8 `/health/circuit-breaker` separado de `/health/ready`
Un circuito abierto no debe sacar a RegistroService de servicio: el fallback sigue permitiendo
registrar. Si el estado del circuito formara parte de `/health/ready`, Compose marcaría el servicio como
no saludable justo cuando está haciendo su trabajo de degradación. Por eso es un endpoint aparte,
pensado para observar y para la demo.

### 4.9 `EsperaBaseReintento` configurable
El backoff (1 s, 2 s) estaba fijo en el código. Al hacerlo configurable, las pruebas usan 1 ms y la
suite completa corre en ~2 s en vez de decenas de segundos, sin cambiar el comportamiento por defecto.

## 5. Configuración

| Clave (`Departamentos:Resilience:`) | Variable de Compose | Defecto | Rango válido |
|-------------------------------------|---------------------|---------|--------------|
| `FallosConsecutivos` | `CB_FALLOS_CONSECUTIVOS` | `3` | 3 a 5 |
| `TimeoutLlamada` | `CB_TIMEOUT_LLAMADA` | `00:00:05` | mayor que 0 |
| `DuracionCircuitoAbierto` | `CB_DURACION_CIRCUITO_ABIERTO` | `00:00:30` | 30 a 60 s |
| `EsperaBaseReintento` | — | `00:00:01` | mayor o igual que 0 |

## 6. Pruebas

Se pasó de 21 a **41 pruebas** (20 nuevas), todas en verde. Se ejecutan con:

```powershell
cd Reto-1/RegistroService
dotnet test RegistroService.sln
```

Las pruebas del cliente usan el circuit breaker **real** de Polly y solo simulan las respuestas HTTP:

| Prueba | Qué demuestra |
|--------|---------------|
| `Circuito_Se_Abre_Tras_Fallos_Consecutivos_Y_Rechaza_Sin_Llamar_Al_Servicio` | CLOSED a OPEN; con OPEN no hay tráfico de red |
| `Circuito_Se_Abre_A_Mitad_De_Los_Reintentos_Y_Deja_De_Reintentar` | Cuenta por intento y acumula entre peticiones (umbral 5) |
| `Un_Exito_Reinicia_El_Contador_De_Fallos_Consecutivos` | Los fallos deben ser consecutivos |
| `Departamento_Inexistente_404_No_Cuenta_Como_Fallo_Del_Circuito` | El 404 es negocio, no fallo |
| `HalfOpen_Cierra_El_Circuito_Si_La_Llamada_De_Prueba_Tiene_Exito` | HALF_OPEN a CLOSED |
| `HalfOpen_Vuelve_A_Abrir_El_Circuito_Si_La_Llamada_De_Prueba_Falla` | HALF_OPEN a OPEN con una sola llamada de prueba |
| 4 en `EmpleadoServiceTests` | Fallback con servicio caído y con circuito abierto; el 404 no activa el fallback; los duplicados se siguen validando |
| 2 en `EmpleadosEndpointsTests` | `201` con `PENDIENTE_VALIDACION` por HTTP; `/health/circuit-breaker` |
| 3 en `DepartamentosResilienceOptionsTests` (8 casos) | Rangos válidos e inválidos; configuración inválida impide arrancar |

## 7. Verificación con Docker Compose

Se levantó un stack aislado (proyecto `cbtest`, puertos 18080/18081) y se ejecutó este escenario:

1. Con Departamentos arriba: registro `ACTIVO`, circuito `CLOSED`.
2. Se detuvo `departamentos-service`. Las peticiones respondieron `201 PENDIENTE_VALIDACION` y el circuito pasó a `OPEN` (log: `CLOSED -> OPEN tras 3 fallos consecutivos`).
3. Con el circuito abierto las respuestas tardaron ~0.02 s (frente a ~8.8 s antes).
4. Con un cliente que corta a los 5 s (simula el gateway): la 1.ª petición se abortó pero dejó fallos registrados, la 2.ª abrió el circuito y respondió `201` en 3.9 s; las siguientes, en ~0.03 s.
5. Se levantó Departamentos de nuevo; a los 30 s el circuito pasó a `HALF_OPEN`, la llamada de prueba funcionó (`ACTIVO`) y volvió a `CLOSED`.
6. La base de datos mostró los empleados del periodo de caída como `PendienteValidacion` y el resto como `Activo`.

Log de transiciones observado: `CLOSED -> OPEN`, `OPEN -> HALF_OPEN`, `HALF_OPEN -> CLOSED`.

## 8. Limitaciones y trabajo pendiente

- **Gateway no probado de extremo a extremo.** No se pudo construir su imagen en esa sesión (fallo de red de Docker), y no se modificó. El comportamiento frente a su corte de 5 s se comprobó con un cliente que corta a los 5 s.
- **Los empleados `PENDIENTE_VALIDACION` no se revalidan automáticamente.** Quedan así hasta que alguien los corrija. Un proceso que los revise cuando Departamentos vuelva sería el siguiente paso natural; queda fuera de este punto.
- **El estado del circuito vive en memoria de cada instancia.** Con varias réplicas de RegistroService, cada una tendría su propio circuito.
- **Una petición cuyo cliente corta antes de tiempo no se registra** (como ocurre con cualquier petición abortada); lo que sí queda es la cuenta de fallos, que es lo que permite abrir el circuito.
