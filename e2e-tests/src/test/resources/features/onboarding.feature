# language: es
@onboarding
Característica: Onboarding de empleados
  Como administrador de recursos humanos
  Quiero registrar nuevos empleados y habilitar sus credenciales
  Para que puedan incorporarse y acceder al sistema de forma segura

  Antecedentes:
    Dado que el sistema está desplegado y accesible a través del gateway
    Y que estoy autenticado como "ADMIN"

  Escenario: El registro de un empleado crea su cuenta de acceso
    Cuando registro un nuevo empleado con datos válidos y únicos
    Entonces la respuesta debe tener código 201
    Y eventualmente el servicio de autenticación debe haber creado una cuenta para el nuevo empleado

  Escenario: El registro de un empleado genera una notificación de bienvenida
    Cuando registro un nuevo empleado con datos válidos y únicos
    Entonces la respuesta debe tener código 201
    Y eventualmente debe existir una notificación de bienvenida para el nuevo empleado

  Escenario: El nuevo empleado establece su contraseña e inicia sesión
    Cuando registro un nuevo empleado con datos válidos y únicos
    Entonces la respuesta debe tener código 201
    Y eventualmente obtengo el token de activación del nuevo empleado
    Cuando el nuevo empleado establece una contraseña válida
    Entonces la respuesta debe tener código 200
    Cuando el nuevo empleado inicia sesión con su contraseña
    Entonces la respuesta debe tener código 200
    Y la respuesta debe contener un token de autenticación

  Escenario: El registro falla cuando el departamento no existe
    Cuando intento registrar un nuevo empleado en un departamento inexistente
    Entonces la respuesta debe tener código 400

  Escenario: El registro falla cuando faltan campos obligatorios
    Cuando intento registrar un nuevo empleado sin nombre ni correo electrónico
    Entonces la respuesta debe tener código 400
