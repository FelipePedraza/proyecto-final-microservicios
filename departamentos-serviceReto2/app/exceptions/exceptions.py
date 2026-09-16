class DepartamentoError(Exception):
    """Base de los errores de negocio del servicio de departamentos."""


class DepartamentoNoEncontradoError(DepartamentoError):
    def __init__(self, id: str):
        super().__init__(f"El departamento con id {id} no existe")


class DepartamentoYaExisteError(DepartamentoError):
    def __init__(self, id: str):
        super().__init__(f"El departamento con id {id} ya existe")