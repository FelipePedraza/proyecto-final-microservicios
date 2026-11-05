import unittest
from http.client import RemoteDisconnected
from io import BytesIO
from urllib.error import HTTPError
from unittest.mock import call, patch

from tools import provision_sonarqube


class SonarQubeRequestTests(unittest.TestCase):
    @patch("tools.provision_sonarqube.urlopen")
    def test_authenticates_with_bearer_token(self, mock_urlopen):
        response = mock_urlopen.return_value.__enter__.return_value
        response.read.return_value = b'{"ok": true}'

        payload = provision_sonarqube.request(
            "/api/system/status",
            token="test-token",
        )

        self.assertEqual(payload, {"ok": True})
        request = mock_urlopen.call_args.args[0]
        self.assertEqual(
            request.get_header("Authorization"),
            "Bearer test-token",
        )

    @patch("tools.provision_sonarqube.urlopen")
    def test_reports_connection_closed_without_traceback(self, mock_urlopen):
        mock_urlopen.side_effect = RemoteDisconnected(
            "Remote end closed connection without response"
        )

        with self.assertRaisesRegex(
            provision_sonarqube.SonarQubeError,
            "Comprueba que SonarQube esté iniciado",
        ) as error:
            provision_sonarqube.request(
                "/api/qualitygates/list",
                token="test-token",
            )

        self.assertIn(
            f"{provision_sonarqube.SONAR_URL}/api/qualitygates/list",
            str(error.exception),
        )

    @patch("tools.provision_sonarqube.urlopen")
    def test_reports_actual_forbidden_api_response(self, mock_urlopen):
        mock_urlopen.side_effect = HTTPError(
            f"{provision_sonarqube.SONAR_URL}/api/qualitygates/select",
            403,
            "Forbidden",
            {},
            BytesIO(b'{"errors":[{"msg":"Insufficient privileges"}]}'),
        )

        with self.assertRaisesRegex(
            provision_sonarqube.SonarQubeError,
            "HTTP 403",
        ) as error:
            provision_sonarqube.request(
                "/api/qualitygates/select",
                {"gateName": provision_sonarqube.QUALITY_GATE_NAME},
                method="POST",
                token="test-token",
            )

        self.assertIn("Insufficient privileges", str(error.exception))


class ConfigureQualityGateTests(unittest.TestCase):
    @patch("tools.provision_sonarqube.request")
    def test_creates_gate_and_uses_name_for_default_and_condition(self, mock_request):
        condition = {
            "id": "condition-1",
            "metric": "coverage",
            "op": "LT",
            "error": "70",
        }
        mock_request.side_effect = [
            {"qualitygates": []},
            {"name": provision_sonarqube.QUALITY_GATE_NAME},
            {"qualitygates": [{"name": provision_sonarqube.QUALITY_GATE_NAME}]},
            {"conditions": [], "isDefault": False},
            {"id": "condition-1"},
            {},
            {"conditions": [condition], "isDefault": True},
            {"components": []},
        ]

        with patch("builtins.print"):
            provision_sonarqube.configure_quality_gate("test-token")

        self.assertIn(
            call(
                "/api/qualitygates/create_condition",
                {
                    "gateName": provision_sonarqube.QUALITY_GATE_NAME,
                    "metric": "coverage",
                    "op": "LT",
                    "error": "70",
                },
                method="POST",
                token="test-token",
            ),
            mock_request.call_args_list,
        )
        self.assertIn(
            call(
                "/api/qualitygates/set_as_default",
                {"name": provision_sonarqube.QUALITY_GATE_NAME},
                method="POST",
                token="test-token",
            ),
            mock_request.call_args_list,
        )

    @patch("tools.provision_sonarqube.request")
    def test_false_actions_for_default_gate_do_not_mean_missing_admin(self, mock_request):
        condition = {
            "id": "condition-1",
            "metric": "coverage",
            "op": "LT",
            "error": "70",
        }
        mock_request.side_effect = [
            {
                "qualitygates": [
                    {
                        "name": provision_sonarqube.QUALITY_GATE_NAME,
                        "isDefault": True,
                    }
                ]
            },
            {
                "name": provision_sonarqube.QUALITY_GATE_NAME,
                "conditions": [condition],
                "isDefault": True,
                "actions": {
                    "manageConditions": False,
                    "setAsDefault": False,
                    "associateProjects": False,
                },
                "caycStatus": "NON_COMPLIANT",
                "hasMQRConditions": False,
                "hasStandardConditions": True,
                "isAiCodeSupported": False,
                "isBuiltIn": False,
            },
            {"conditions": [condition], "isDefault": True},
            {"components": []},
        ]

        with patch("builtins.print"):
            provision_sonarqube.configure_quality_gate("test-token")

        self.assertNotIn(
            call(
                "/api/qualitygates/set_as_default",
                {"name": provision_sonarqube.QUALITY_GATE_NAME},
                method="POST",
                token="test-token",
            ),
            mock_request.call_args_list,
        )

    @patch("tools.provision_sonarqube.request")
    def test_existing_gate_without_condition_creates_condition_by_name(
        self, mock_request
    ):
        mock_request.side_effect = [
            {"qualitygates": [{"name": provision_sonarqube.QUALITY_GATE_NAME}]},
            {"name": provision_sonarqube.QUALITY_GATE_NAME, "conditions": []},
            {},
            {},
            {
                "name": provision_sonarqube.QUALITY_GATE_NAME,
                "conditions": [
                    {"id": "condition-1", "metric": "coverage", "op": "LT", "error": "70"}
                ],
                "isDefault": True,
            },
            {"components": []},
        ]

        with patch("builtins.print"):
            provision_sonarqube.configure_quality_gate("test-token")

        self.assertIn(
            call(
                "/api/qualitygates/create_condition",
                {
                    "gateName": provision_sonarqube.QUALITY_GATE_NAME,
                    "metric": "coverage",
                    "op": "LT",
                    "error": "70",
                },
                method="POST",
                token="test-token",
            ),
            mock_request.call_args_list,
        )

    @patch("tools.provision_sonarqube.request")
    def test_selects_existing_project_by_gate_name(self, mock_request):
        condition = {
            "id": "condition-1",
            "metric": "coverage",
            "op": "LT",
            "error": "70",
        }
        mock_request.side_effect = [
            {"qualitygates": [{"name": provision_sonarqube.QUALITY_GATE_NAME}]},
            {"conditions": [condition], "isDefault": True},
            {"conditions": [condition], "isDefault": True},
            {"components": [{"key": "auth-service"}]},
            {},
        ]

        with patch("builtins.print"):
            provision_sonarqube.configure_quality_gate("test-token")

        self.assertIn(
            call(
                "/api/qualitygates/select",
                {
                    "gateName": provision_sonarqube.QUALITY_GATE_NAME,
                    "projectKey": "auth-service",
                },
                method="POST",
                token="test-token",
            ),
            mock_request.call_args_list,
        )


if __name__ == "__main__":
    unittest.main()
