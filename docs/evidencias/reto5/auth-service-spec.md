# DocumentaciÃ³n: Auth Service (GestiÃ³n de Credenciales y Seguridad)

Este documento define la arquitectura, el modelo de datos, la integraciÃ³n por eventos y los contratos de la API para el microservicio de autenticaciÃ³n (`auth-service`), basado estrictamente en las reglas del **Reto 5**. 

---

## 1. Arquitectura y Responsabilidades (Integrante 1)

*   **Proveedor de Identidad:** Ãšnico servicio responsable de verificar contraseÃ±as y emitir tokens JWT.
*   **Almacenamiento Seguro:** ContraseÃ±as hasheadas siempre con **BCrypt**. Cero almacenamiento en texto plano.
*   **ValidaciÃ³n Externa:** Este servicio *firma* el JWT. La *validaciÃ³n* (para bloquear o permitir peticiones) la harÃ¡ el **API Gateway** (Integrante 2).
*   **IntegraciÃ³n por Eventos:** El servicio no envÃ­a correos; emite eventos para que el `notificaciones-service` lo haga (Integrante 3).

---

## 2. Modelo de Datos (Base de Datos)

La entidad principal serÃ¡ `Account` (Cuenta). 

| Campo | Tipo | DescripciÃ³n |
| :--- | :--- | :--- |
| `id` | String (UUID) | Identificador Ãºnico de la cuenta (debe coincidir con el `id` del empleado). |
| `email` | String | Correo electrÃ³nico (Ãºnico). Se usa como `username` en el Login. |
| `password` | String | Hash BCrypt. Al crearse la cuenta por evento, este campo estarÃ¡ vacÃ­o o nulo hasta que se active. |
| `role` | String | Rol RBAC: `ADMIN` o `USER`. |
| `status` | Enum/String | **Obligatorio manejar estados completos (no booleanos):**<br>`PENDIENTE_ACTIVACION`, `ACTIVA`, `SUSPENDIDA_TEMPORAL`, `DESACTIVADA_PERMANENTE`. |

---

## 3. Estructuras de los Tokens (JWT)

El servicio manejarÃ¡ 2 tipos de tokens (ambos firmados con el mismo *Secret Key* inyectado por `.env`):

### 3.1 Token de Acceso (Access JWT)
Devuelto en `/auth/login`. Tiempo de expiraciÃ³n recomendado: **1 hora**.
```json
{
  "sub": "UUID-del-empleado", 
  "role": "USER",             
  "iat": 1712345678,          
  "exp": 1712349278           
}
```

### 3.2 Token de ActivaciÃ³n / RecuperaciÃ³n (Reset Token - Stateless)
Generado al crear un empleado o pedir recuperaciÃ³n. Tiempo de expiraciÃ³n: **15 a 60 minutos**.
```json
{
  "sub": "UUID-del-empleado",
  "type": "RESET_PASSWORD",   // <-- Claim especial obligatorio
  "iat": 1712345678,
  "exp": 1712346578
}
```

---

## 4. Contratos de API REST (Endpoints)

### 4.1 Login (`POST /auth/login`)
*   **DescripciÃ³n:** Recibe credenciales, verifica validez y retorna el Access JWT.
*   **Body:** `{"email": "juan@empresa.com", "password": "miPassword123"}`
*   **Response (200):** `{"token": "eyJhbG..."}`
*   **Errores:** `401 Unauthorized` (malas credenciales), `403 Forbidden` (cuenta suspendida/desactivada).

### 4.2 Solicitar RecuperaciÃ³n (`POST /auth/recover-password`)
*   **DescripciÃ³n:** Genera un Reset Token y **publica el evento** `usuario.recuperacion`.
*   **Body:** `{"email": "juan@empresa.com"}`
*   **Response (200):** `{"message": "Instrucciones enviadas"}`

### 4.3 Restablecer ContraseÃ±a (`POST /auth/reset-password`)
*   **DescripciÃ³n:** Recibe el Reset Token y la nueva contraseÃ±a. Hace el update en BD y **publica el evento** `cuenta.activada` (si es la primera vez).
*   **Body:** `{"resetToken": "eyJhb...", "newPassword": "nuevaPassword123"}`
*   **Response (200):** `{"message": "ContraseÃ±a actualizada"}`

### 4.4 Cambiar ContraseÃ±a (`POST /auth/change-password`)
*   **DescripciÃ³n:** Un usuario logueado cambia su propia clave. Requiere JWT vÃ¡lido (validado por Gateway).
*   **Body:** `{"oldPassword": "vieja", "newPassword": "nueva"}`
*   **Response (200):** `{"message": "ContraseÃ±a cambiada exitosamente"}`

---

## 5. Matriz de IntegraciÃ³n por Eventos (Para el Integrante 3)

El `auth-service` actuarÃ¡ como **Productor** y **Consumidor** en RabbitMQ.

### 5.1 Eventos que CONSUME (Acciones Internas):
| Evento | AcciÃ³n en la Base de Datos (`auth-service`) | Efecto Secundario |
| :--- | :--- | :--- |
| `empleado.creado` | Crea cuenta en estado `PENDIENTE_ACTIVACION`, sin password, rol `USER`. | Genera Reset Token y **publica** `usuario.creado`. |
| `empleado.retirado` | Cambia estado a `DESACTIVADA_PERMANENTE`. | **Publica** `cuenta.desactivada` (permanente: true). |
| `vacaciones.iniciadas` | Cambia estado a `SUSPENDIDA_TEMPORAL`. | **Publica** `cuenta.desactivada` (motivo: VACACIONES, permanente: false). |
| `vacaciones.finalizadas` | **(CASO BORDE)** Si la cuenta estÃ¡ `DESACTIVADA_PERMANENTE`, **IGNORAR**. Si estÃ¡ `SUSPENDIDA_TEMPORAL`, pasar a `ACTIVA`. | **Publica** `cuenta.activada`. |

### 5.2 Eventos que PUBLICA (Hacia `notificaciones-service`):
*   `usuario.creado`: Se emite tras procesar `empleado.creado`. Lleva el Reset Token en el payload.
*   `usuario.recuperacion`: Se emite al llamar a `/auth/recover-password`. Lleva el Reset Token en el payload.
*   `cuenta.activada`: Se emite tras establecer la contraseÃ±a o volver de vacaciones.
*   `cuenta.desactivada`: Se emite al iniciar vacaciones o al retirar al empleado.

## 6. Seed del Administrador
Para garantizar que el sistema no nazca bloqueado, el auth-service cuenta con un componente AdminSeeder. Al levantar la aplicacion, si no existe el admin, se crea:
* ID: ADMIN-001
* Email: admin@empresa.com
* Password: admin123 (Se guarda encriptada)

## 7. Manejo de Errores
Se integro un RestExceptionHandler global que formatea todos los errores al estandar ResponseDTO.
