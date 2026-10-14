# language: es
@onboarding
Característica: Llegada de un empleado nuevo
  Se crea el empleado, le aparece su cuenta y su notificación de bienvenida,
  y con eso ya puede poner su contraseña y entrar

  Antecedentes:
    Dado que el sistema está arriba y responde por el gateway
    Y que entro como "ADMIN"

  Escenario: Al crear el empleado también se le crea la cuenta
    Cuando creo un empleado nuevo con datos bien puestos
    Entonces me devuelve un 201
    Y al rato auth ya le tiene creada su cuenta

  Escenario: Al crear el empleado le llega la bienvenida
    Cuando creo un empleado nuevo con datos bien puestos
    Entonces me devuelve un 201
    Y al rato le llega su notificación de bienvenida

  Escenario: El empleado nuevo pone su contraseña y entra
    Cuando creo un empleado nuevo con datos bien puestos
    Entonces me devuelve un 201
    Y al rato consigo su token de activación
    Cuando el empleado nuevo define su contraseña
    Entonces me devuelve un 200
    Cuando el empleado nuevo hace login con esa contraseña
    Entonces me devuelve un 200
    Y en la respuesta viene un token

  Escenario: No se puede crear un empleado en un departamento que no existe
    Cuando intento crear un empleado en un departamento que no existe
    Entonces me devuelve un 400

  Escenario: No se puede crear un empleado sin nombre ni email
    Cuando intento crear un empleado sin nombre ni email
    Entonces me devuelve un 400
