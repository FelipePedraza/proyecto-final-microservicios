# language: es
@seguridad
Característica: Quién puede entrar y qué puede hacer
  Sin token no se entra, un USER solo mira
  y un ADMIN puede hacer de todo

  Antecedentes:
    Dado que el sistema está arriba y responde por el gateway

  Escenario: Sin token no deja pasar
    Cuando pido la lista de empleados sin token
    Entonces me devuelve un 401

  Escenario: Con un token inventado tampoco
    Dado que mando un token inventado
    Cuando pido la lista de empleados
    Entonces me devuelve un 401

  Escenario: Un USER puede ver la lista de empleados
    Dado que entro como "USER"
    Cuando pido la lista de empleados
    Entonces me devuelve un 200

  Escenario: Un USER no puede crear empleados
    Dado que entro como "USER"
    Cuando intento crear un empleado
    Entonces me devuelve un 403

  Escenario: Un USER no puede borrar empleados
    Dado que hay un empleado de prueba ya creado
    Y que entro como "USER"
    Cuando intento borrar ese empleado
    Entonces me devuelve un 403

  Escenario: Un ADMIN sí puede crear y borrar empleados
    Dado que entro como "ADMIN"
    Cuando creo un empleado
    Entonces me devuelve un 201
    Cuando borro el empleado que creé
    Entonces me devuelve un 204
