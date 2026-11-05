#!/usr/bin/env python3
"""Provisiona el Quality Gate y el webhook global de Jenkins en SonarQube."""

import getpass
import json
import os
import sys
from typing import Any, Dict, Iterable, Optional
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen


SONAR_URL = os.environ.get("SONAR_URL", "http://localhost:9000").rstrip("/")
QUALITY_GATE_NAME = "QualityGate70"
QUALITY_GATE_THRESHOLD = "70"
WEBHOOK_NAME = "Jenkins"
WEBHOOK_URL = "http://jenkins:8080/sonarqube-webhook/"
PROJECT_KEYS = ("auth-service", "empleado-service")


class SonarQubeError(RuntimeError):
    """Error legible al comunicarse con SonarQube o configurar sus recursos."""


def _decode_json(body: bytes, api_path: str) -> Dict[str, Any]:
    if not body:
        return {}
    try:
        payload = json.loads(body)
    except json.JSONDecodeError as exc:
        raise SonarQubeError(
            f"Respuesta JSON inválida de SonarQube en {api_path}."
        ) from exc
    if not isinstance(payload, dict):
        raise SonarQubeError(
            f"Respuesta inesperada de SonarQube en {api_path}: se esperaba un objeto JSON."
        )
    return payload


def request(
    api_path: str,
    params: Optional[Dict[str, str]] = None,
    method: str = "GET",
    token: str = "",
) -> Dict[str, Any]:
    """Ejecuta una llamada autenticada a la API Web de SonarQube."""
    if not token:
        raise SonarQubeError("Se requiere un token de SonarQube.")

    method = method.upper()
    url = f"{SONAR_URL}{api_path}"
    data = None
    if method == "GET":
        if params:
            url = f"{url}?{urlencode(params)}"
    else:
        data = urlencode(params or {}).encode("utf-8")

    headers = {"Authorization": f"Bearer {token}"}
    if data is not None:
        headers["Content-Type"] = "application/x-www-form-urlencoded"
    req = Request(url, data=data, headers=headers, method=method)

    try:
        with urlopen(req, timeout=15) as response:
            return _decode_json(response.read(), api_path)
    except HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace").strip()
        if exc.code in (401, 403):
            reason = (
                "el token fue rechazado"
                if exc.code == 401
                else "el token no tiene permiso para esta operación"
            )
            raise SonarQubeError(
                f"SonarQube respondió HTTP {exc.code} en {api_path}: {reason}. "
                f"{detail}"
            ) from exc
        raise SonarQubeError(
            f"SonarQube respondió HTTP {exc.code} en {api_path}: {detail}"
        ) from exc
    except URLError as exc:
        raise SonarQubeError(
            f"No se pudo conectar con SonarQube en {SONAR_URL}: {exc.reason}"
        ) from exc
    except (ConnectionError, TimeoutError) as exc:
        raise SonarQubeError(
            f"SonarQube cerró o no respondió la conexión a {SONAR_URL}{api_path}: "
            f"{exc}. Comprueba que SonarQube esté iniciado y que SONAR_URL use "
            "el protocolo y puerto correctos (por defecto, http://localhost:9000). "
            "Puedes validar la disponibilidad abriendo "
            f"{SONAR_URL}/api/system/status en el navegador."
        ) from exc


def _quality_gates(payload: Dict[str, Any]) -> Iterable[Dict[str, Any]]:
    gates = payload.get("qualitygates", [])
    if not isinstance(gates, list):
        raise SonarQubeError(
            "Respuesta inesperada de qualitygates/list: 'qualitygates' no es una lista."
        )
    return (gate for gate in gates if isinstance(gate, dict))


def _gate_details(token: str) -> Dict[str, Any]:
    return request(
        "/api/qualitygates/show",
        {"name": QUALITY_GATE_NAME},
        token=token,
    )


def _coverage_conditions(details: Dict[str, Any]) -> list:
    conditions = details.get("conditions")
    if conditions is None:
        conditions = []
    if not isinstance(conditions, list):
        raise SonarQubeError(
            f"Respuesta inesperada de qualitygates/show para {QUALITY_GATE_NAME}: "
            "'conditions' no es una lista."
        )
    return [
        item
        for item in conditions
        if isinstance(item, dict) and item.get("metric") == "coverage"
    ]


def _condition_id(condition: Dict[str, Any]) -> str:
    condition_id = condition.get("id")
    if not condition_id:
        raise SonarQubeError(
            f"SonarQube devolvió una condición coverage sin ID para {QUALITY_GATE_NAME}."
        )
    return str(condition_id)


def configure_quality_gate(token: str) -> None:
    gates = list(
        _quality_gates(request("/api/qualitygates/list", token=token))
    )
    gate = next(
        (item for item in gates if item.get("name") == QUALITY_GATE_NAME),
        None,
    )
    created = gate is None

    if created:
        request(
            "/api/qualitygates/create",
            {"name": QUALITY_GATE_NAME},
            method="POST",
            token=token,
        )
        gates = list(
            _quality_gates(request("/api/qualitygates/list", token=token))
        )
        gate = next(
            (item for item in gates if item.get("name") == QUALITY_GATE_NAME),
            None,
        )
        if gate is None:
            raise SonarQubeError(
                f"SonarQube no encontró {QUALITY_GATE_NAME} después de crearlo."
            )

    details = _gate_details(token)
    if created:
        print(f"Creado el Quality Gate {QUALITY_GATE_NAME}.")

    conditions = _coverage_conditions(details)
    if conditions:
        current = conditions[0]
        if (
            current.get("op") != "LT"
            or str(current.get("error")) != QUALITY_GATE_THRESHOLD
        ):
            request(
                "/api/qualitygates/update_condition",
                {
                    "id": _condition_id(current),
                    "op": "LT",
                    "error": QUALITY_GATE_THRESHOLD,
                },
                method="POST",
                token=token,
            )
        for duplicate in conditions[1:]:
            request(
                "/api/qualitygates/delete_condition",
                {"id": _condition_id(duplicate)},
                method="POST",
                token=token,
            )
    else:
        request(
            "/api/qualitygates/create_condition",
            {
                "gateName": QUALITY_GATE_NAME,
                "metric": "coverage",
                "op": "LT",
                "error": QUALITY_GATE_THRESHOLD,
            },
            method="POST",
            token=token,
        )

    if details.get("isDefault") is not True:
        request(
            "/api/qualitygates/set_as_default",
            {"name": QUALITY_GATE_NAME},
            method="POST",
            token=token,
        )

    saved = _gate_details(token)
    conditions = _coverage_conditions(saved)
    if (
        len(conditions) != 1
        or conditions[0].get("op") != "LT"
        or str(conditions[0].get("error")) != QUALITY_GATE_THRESHOLD
    ):
        raise SonarQubeError(
            "SonarQube no confirmó la condición coverage LT 70."
        )

    if saved.get("isDefault") is not True:
        raise SonarQubeError(
            f"SonarQube no confirmó {QUALITY_GATE_NAME} como gate predeterminado."
        )

    existing_projects = request(
        "/api/projects/search",
        {"projects": ",".join(PROJECT_KEYS)},
        token=token,
    ).get("components", [])
    for project in existing_projects:
        project_key = project.get("key")
        if project_key in PROJECT_KEYS:
            request(
                "/api/qualitygates/select",
                {"gateName": QUALITY_GATE_NAME, "projectKey": project_key},
                method="POST",
                token=token,
            )

    print(
        f"{QUALITY_GATE_NAME} está asignado por defecto: "
        f"la cobertura inferior a {QUALITY_GATE_THRESHOLD}% falla."
    )
    if existing_projects:
        print("El gate también se asignó a los proyectos existentes de los pipelines.")


def configure_jenkins_webhook(token: str) -> None:
    webhooks = request("/api/webhooks/list", token=token).get("webhooks", [])
    exists = any(
        item.get("name") == WEBHOOK_NAME and item.get("url") == WEBHOOK_URL
        for item in webhooks
    )
    if not exists:
        request(
            "/api/webhooks/create",
            {"name": WEBHOOK_NAME, "url": WEBHOOK_URL},
            method="POST",
            token=token,
        )
        webhooks = request("/api/webhooks/list", token=token).get("webhooks", [])
        if not any(
            item.get("name") == WEBHOOK_NAME and item.get("url") == WEBHOOK_URL
            for item in webhooks
        ):
            raise SonarQubeError(
                f"SonarQube no confirmó el webhook hacia {WEBHOOK_URL}."
            )
        print(f"Webhook global creado hacia {WEBHOOK_URL}.")
    print(f"Webhook global hacia {WEBHOOK_URL} configurado.")


def main() -> None:
    token = os.environ.get("SONAR_ADMIN_TOKEN", "").strip()
    if not token:
        token = getpass.getpass(
            "Token de usuario administrador de SonarQube (no se mostrará): "
        ).strip()
    if not token:
        raise SonarQubeError("Se requiere un token de administrador de SonarQube.")

    configure_quality_gate(token)
    configure_jenkins_webhook(token)
    print("Configuración de SonarQube completada.")


if __name__ == "__main__":
    try:
        main()
    except SonarQubeError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        sys.exit(1)
