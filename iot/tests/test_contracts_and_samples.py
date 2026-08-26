import json
import pathlib
import unittest

import jsonschema


IOT_ROOT = pathlib.Path(__file__).resolve().parents[1]
CONTRACTS = IOT_ROOT / "contracts"
SAMPLES = IOT_ROOT / "samples"
SCHEMA_VALIDATOR = getattr(jsonschema, "Draft202012Validator", jsonschema.Draft7Validator)


def schema_kind(topic):
    if topic.endswith("/gps"):
        return "gps"
    if topic.endswith("/status"):
        return "status"
    if topic.endswith("/command/ack"):
        return "command_ack"
    if topic.endswith("/command"):
        return "command"
    if topic.endswith("/alert"):
        return "alert"
    return None


class ContractAndSampleTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.schemas = {
            name: json.loads((CONTRACTS / f"{name}.schema.json").read_text(encoding="utf-8"))
            for name in ("gps", "status", "alert", "command", "command_ack", "route_api")
        }

    def test_all_jsonl_events_match_contracts(self):
        validated = 0
        for path in sorted(SAMPLES.glob("*.jsonl")):
            for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
                if not line.strip():
                    continue
                event = json.loads(line)
                kind = schema_kind(event["topic"])
                self.assertIsNotNone(kind, f"{path.name}:{line_number} 未知Topic")
                SCHEMA_VALIDATOR(self.schemas[kind]).validate(event["payload"])
                validated += 1
        self.assertGreater(validated, 0)

    def test_command_examples_match_contracts(self):
        examples = {
            "task_route_ready_command.json": "command",
            "task_route_ready_ack.json": "command_ack",
            "task_route_api_ready.json": "route_api",
        }
        for filename, kind in examples.items():
            payload = json.loads((SAMPLES / filename).read_text(encoding="utf-8"))
            if kind == "route_api":
                payload = payload["data"]
            SCHEMA_VALIDATOR(self.schemas[kind]).validate(payload)

    def test_all_required_alarm_scenarios_are_present(self):
        observed = set()
        for path in sorted(SAMPLES.glob("abnormal_*.jsonl")):
            for line in path.read_text(encoding="utf-8").splitlines():
                event = json.loads(line)
                if event["topic"].endswith("/alert"):
                    observed.add(event["payload"]["alert_type"])
        self.assertEqual({"异常停留", "偏航", "异常开箱"}, observed)

    def test_secret_firmware_config_is_not_committed(self):
        self.assertFalse(
            (IOT_ROOT / "firmware" / "D14_smart_logistics_gps_mqtt" / "smart_logistics_config.h").exists()
        )


if __name__ == "__main__":
    unittest.main()
