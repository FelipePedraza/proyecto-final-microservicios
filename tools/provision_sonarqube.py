#!/usr/bin/env python3
"""Configura de forma idempotente el gate de SonarQube y el webhook de Jenkins."""

import base64
import getpass
import json
import os
import sys
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen


SONAR_URL = os.environ.get("SONAR_URL", "http://localhost:9000").rstrip("/")
QUALITY_GATE_NAME = "QualityGate70"
QUALITY_GATE_THRESHOLD = "70"
WEBHOOK_NAME = "Jenkins"
WEBHOOK_URL = "http://jenkins:8080/sonarqube-webhook/"


class SonarQubeError(RuntimeError):
    pass


def request(api_path, params=None, method="GET", token=""):
    url = f"{SONAR_URL}{api_path}"
    data = None
    if method == "GET":
        if params:
            url = f"{url}?{urlencode(params)}"
    else:
        data = urlencode(params or {}).encode("utf-8")

    credentials = base64.b64encode(f"{token}:".encode("utf-8")).decode("ascii")
    req = Request(
        url,
        data=data,
        headers={
            "Authorization": f"Basic {credentials}",
            "Content-Type": "application/x-www-form-urlencoded",
        },
        method=method,
    )
    try:
        with urlopen(req, timeout=15) as response:
            body = response.read()
    except HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        raise SonarQubeError(
            f"SonarQube devolvió HTTP {exc.code} en {api_path}: {detail}"
        ) from exc
    except URLError as exc:
        raise SonarQubeError(
            f"No se pudo conectar con SonarQube en {SONAR_URL}: {exc.reason}"
        ) from exc

    if not body:
        return {}
    try:
        return json.loads(body)
    except json.JSONDecodeError as exc:
        raise SonarQubeError(
            f"Respuesta JSON inválida de SonarQube en {api_path}"
        ) from exc


def configure_quality_gate(token):
    gates = request("/api/qualitygates/list", token=token).get(
        "qualitygates", []
    )
    gate = next(
        (item for item in gates if item.get("name") == QUALITY_GATE_NAME), None
    )
    if gate is None:
        gate = request(
            "/api/qualitygates/create",
            {"name": QUALITY_GATE_NAME},
            method="POST",
            token=token,
        )
        print(f"Creado el Quality Gate {QUALITY_GATE_NAME}.")

    gate_id = str(gate["id"])
    details = request(
        "/api/qualitygates/show", {"name": QUALITY_GATE_NAME}, token=token
    )
    coverage_conditions = [
        item
        for item in details.get("conditions", [])
        if item.get("metric") == "coverage"
    ]

    if coverage_conditions:
        condition = coverage_conditions[0]
        if (
            condition.get("op") != "LT"
            or str(condition.get("error")) != QUALITY_GATE_THRESHOLD
        ):
            request(
                "/api/qualitygates/update_condition",
                {
                    "id": condition["id"],
                    "op": "LT",
                    "error": QUALITY_GATE_THRESHOLD,
                },
                method="POST",
                token=token,
            )
        for duplicate in coverage_conditions[1:]:
            request(
                "/api/qualitygates/delete_condition",
                {"id": duplicate["id"]},
                method="POST",
                token=token,
            )
    else:
        request(
            "/api/qualitygates/create_condition",
            {
                "gateId": gate_id,
                "metric": "coverage",
                "op": "LT",
                "error": QUALITY_GATE_THRESHOLD,
            },
            method="POST",
            token=token,
        )

    request(
        "/api/qualitygates/set_as_default",
        {"id": gate_id},
        method="POST",
        token=token,
    )
    saved = request(
        "/api/qualitygates/show", {"name": QUALITY_GATE_NAME}, token=token
    )
    conditions = [
        item
        for item in saved.get("conditions", [])
        if item.get("metric") == "coverage"
    ]
    defaults = request("/api/qualitygates/list", token=token).get(
        "qualitygates", []
    )
    if (
        len(conditions) != 1
        or conditions[0].get("op") != "LT"
        or str(conditions[0].get("error")) != QUALITY_GATE_THRESHOLD
    ):
        raise SonarQubeError(
            "SonarQube no confirmó la condición coverage LT 70."
        )
    if not any(
        item.get("id") == gate_id and item.get("isDefault") is True
        for item in defaults
    ):
        raise SonarQubeError(
            f"SonarQube no confirmó {QUALITY_GATE_NAME} como gate predeterminado."
        )
    existing_projects = request(
        "/api/projects/search",
        {"projects": "auth-service,empleado-service"},
        token=token,
    ).get("components", [])
    for project in existing_projects:
        project_key = project.get("key")
        if project_key in {"auth-service", "empleado-service"}:
            request(
                "/api/qualitygates/select",
                {"gateId": gate_id, "projectKey": project_key},
                method="POST",
                token=token,
            )
    print(
        f"{QUALITY_GATE_NAME} está asignado por defecto: "
        f"la cobertura inferior a {QUALITY_GATE_THRESHOLD}% falla."
    )
    if existing_projects:
        print("El gate también se asignó a los proyectos existentes de los pipelines.")


def configure_jenkins_webhook(token):
    webhooks = request("/api/webhooks/list", token=token).get("webhooks", [])
    webhook = next(
        (
            item
            for item in webhooks
            if item.get("name") == WEBHOOK_NAME
            and item.get("url") == WEBHOOK_URL
        ),
        None,
    )
    if webhook is None:
        request(
            "/api/webhooks/create",
            {"name": WEBHOOK_NAME, "url": WEBHOOK_URL},
            method="POST",
            token=token,
        )
        print(f"Webhook global creado hacia {WEBHOOK_URL}.")
        webhooks = request("/api/webhooks/list", token=token).get("webhooks", [])
        if not any(
            item.get("name") == WEBHOOK_NAME and item.get("url") == WEBHOOK_URL
            for item in webhooks
        ):
            raise SonarQubeError(
                f"SonarQube no confirmó el webhook hacia {WEBHOOK_URL}."
            )
    print(f"Webhook global hacia {WEBHOOK_URL} configurado.")


def main():
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
