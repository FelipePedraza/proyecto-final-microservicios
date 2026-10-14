# language: es
Característica: Salida de un empleado
  Cuando alguien se retira pierde el acceso para siempre
  y queda registrado para auditoría

  Antecedentes:
    Dado que entro como "ADMIN"
    Y que ya hay un empleado activo con su contraseña puesta

  Escenario: Al dar de baja a un empleado pierde el acceso
    Cuando doy de baja al empleado
    Entonces me devuelve un 204
    Y al rato el empleado ya no puede hacer login
    Y tampoco puede recuperar su contraseña

  Escenario: El empleado dado de baja queda en la auditoría
    Cuando doy de baja al empleado
    Y listo los empleados en estado "RETIRADO"
    Entonces el empleado dado de baja sale en la lista
