import unittest
from unittest.mock import call, patch

from tools import provision_sonarqube


class ConfigureQualityGateTests(unittest.TestCase):
    @patch("tools.provision_sonarqube.request")
    def test_reads_gate_id_from_list_after_creation(self, mock_request):
        condition = {
            "id": "condition-1",
            "metric": "coverage",
            "op": "LT",
            "error": "70",
        }
        mock_request.side_effect = [
            {"qualitygates": []},
            {"name": provision_sonarqube.QUALITY_GATE_NAME},
            {
                "qualitygates": [
                    {
                        "id": "gate-1",
                        "name": provision_sonarqube.QUALITY_GATE_NAME,
                    }
                ]
            },
            {"conditions": [condition]},
            {},
            {"conditions": [condition]},
            {
                "qualitygates": [
                    {
                        "id": "gate-1",
                        "name": provision_sonarqube.QUALITY_GATE_NAME,
                        "isDefault": True,
                    }
                ]
            },
            {"components": []},
        ]

        with patch("builtins.print"):
            provision_sonarqube.configure_quality_gate("test-token")

        self.assertIn(
            call(
                "/api/qualitygates/set_as_default",
                {"id": "gate-1"},
                method="POST",
                token="test-token",
            ),
            mock_request.call_args_list,
        )

    @patch("tools.provision_sonarqube.request")
    def test_reads_gate_id_from_show_when_list_omits_it(self, mock_request):
        condition = {
            "id": "condition-1",
            "metric": "coverage",
            "op": "LT",
            "error": "70",
        }
        mock_request.side_effect = [
            {
                "qualitygates": [
                    {"name": provision_sonarqube.QUALITY_GATE_NAME}
                ]
            },
            {
                "qualityGate": {
                    "id": "gate-1",
                    "name": provision_sonarqube.QUALITY_GATE_NAME,
                },
                "conditions": [condition],
            },
            {},
            {"conditions": [condition]},
            {
                "qualitygates": [
                    {
                        "id": "gate-1",
                        "name": provision_sonarqube.QUALITY_GATE_NAME,
                        "isDefault": True,
                    }
                ]
            },
            {"components": []},
        ]

        with patch("builtins.print"):
            provision_sonarqube.configure_quality_gate("test-token")

        self.assertIn(
            call(
                "/api/qualitygates/set_as_default",
                {"id": "gate-1"},
                method="POST",
                token="test-token",
            ),
            mock_request.call_args_list,
        )

    @patch("tools.provision_sonarqube.request")
    def test_reports_missing_gate_id_instead_of_key_error(self, mock_request):
        mock_request.side_effect = [
            {"qualitygates": []},
            {},
            {
                "qualitygates": [
                    {"name": provision_sonarqube.QUALITY_GATE_NAME}
                ]
            },
            {"qualityGate": {}},
        ]

        with self.assertRaisesRegex(
            provision_sonarqube.SonarQubeError,
            "no devolvió el ID",
        ):
            provision_sonarqube.configure_quality_gate("test-token")

    @patch("tools.provision_sonarqube.request")
    def test_explains_missing_quality_gate_permissions(self, mock_request):
        mock_request.return_value = {
            "qualitygates": [
                {
                    "name": provision_sonarqube.QUALITY_GATE_NAME,
                    "actions": {
                        "manageConditions": False,
                        "setAsDefault": False,
                        "associateProjects": False,
                    },
                }
            ]
        }

        with self.assertRaisesRegex(
            provision_sonarqube.SonarQubeError,
            "Administer Quality Gates",
        ):
            provision_sonarqube.configure_quality_gate("test-token")

        self.assertEqual(mock_request.call_count, 2)
        self.assertIn(
            call(
                "/api/qualitygates/show",
                {"name": provision_sonarqube.QUALITY_GATE_NAME},
                token="test-token",
            ),
            mock_request.call_args_list,
        )

    @patch("tools.provision_sonarqube.request")
    def test_actionable_error_when_sonar_omits_gate_id_and_permissions(
        self, mock_request
    ):
        mock_request.side_effect = [
            {
                "qualitygates": [
                    {"name": provision_sonarqube.QUALITY_GATE_NAME}
                ]
            },
            {"name": provision_sonarqube.QUALITY_GATE_NAME},
        ]

        with self.assertRaisesRegex(
            provision_sonarqube.SonarQubeError,
            "ni en qualitygates/list ni en qualitygates/show",
        ):
            provision_sonarqube.configure_quality_gate("test-token")


if __name__ == "__main__":
    unittest.main()
