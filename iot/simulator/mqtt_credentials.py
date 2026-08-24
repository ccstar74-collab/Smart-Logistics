"""Small helper for loading MQTT connection settings from a local env file."""

from pathlib import Path


def load_mqtt_credentials(path):
    credential_path = Path(path).expanduser()
    values = {}
    for raw_line in credential_path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip()

    required = ("MQTT_HOST", "MQTT_EXTERNAL_PORT", "MQTT_USERNAME", "MQTT_PASSWORD")
    missing = [key for key in required if not values.get(key)]
    if missing:
        raise ValueError(
            f"MQTT credentials file {credential_path} is missing: {', '.join(missing)}"
        )
    return values
