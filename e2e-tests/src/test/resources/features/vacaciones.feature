# language: es
@vacaciones
Característica: Vacaciones y suspensión de cuentas
  Cuando alguien se va de vacaciones su cuenta se suspende,
  y cuando vuelve se reactiva. Además no se pueden cruzar dos períodos

  Antecedentes:
    Dado que entro como "ADMIN"
    Y que ya hay un empleado activo con su contraseña puesta

  Escenario: Se pueden programar unas vacaciones normales
    Cuando pido vacaciones desde dentro de 7 días hasta dentro de 14 días
    Entonces me devuelve un 201

  Escenario: La cuenta se suspende en las vacaciones y se reactiva al volver
    Cuando pido vacaciones desde dentro de 7 días hasta dentro de 14 días
    Entonces me devuelve un 201
    Cuando arranco las vacaciones a la fuerza
    Entonces me devuelve un 200
    Y al rato el empleado ya no puede hacer login
    Cuando termino las vacaciones a la fuerza
    Entonces me devuelve un 200
    Y al rato el empleado vuelve a poder hacer login

  Escenario: No deja pedir vacaciones que terminan antes de empezar
    Cuando pido vacaciones desde dentro de 7 días hasta dentro de 2 días
    Entonces me devuelve un 400

  Escenario: No deja pedir vacaciones que se cruzan con otras
    Cuando pido vacaciones desde dentro de 7 días hasta dentro de 14 días
    Y pido vacaciones desde dentro de 10 días hasta dentro de 20 días
    Entonces me devuelve un 400
