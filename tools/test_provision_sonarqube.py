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
    def test_reports_missing_gate_id_instead_of_key_error(self, mock_request):
        mock_request.side_effect = [
            {"qualitygates": []},
            {},
            {"qualitygates": []},
        ]

        with self.assertRaisesRegex(
            provision_sonarqube.SonarQubeError,
            "no devolvió el ID",
        ):
            provision_sonarqube.configure_quality_gate("test-token")


if __name__ == "__main__":
    unittest.main()
