"""Restore the permanent signing identity from one GitHub Actions secret."""
import base64
import json
import os
from pathlib import Path
import subprocess

raw = os.environ.get("ANDROID_SIGNING_JSON", "")
if not raw:
    raise SystemExit("ANDROID_SIGNING_JSON repository secret is required for stable signing.")
data = json.loads(raw)
password = data["password"]
alias = data["alias"]
if any(c in password + alias for c in "\r\n"):
    raise SystemExit("Invalid signing configuration")
print("::add-mask::" + password)
path = Path(os.environ["RUNNER_TEMP"]) / "trx-release.p12"
path.write_bytes(base64.b64decode(data["keystore_base64"], validate=True))
path.chmod(0o600)
environment = dict(os.environ, TRX_CERT_PASSWORD=password)
result = subprocess.run(["keytool", "-list", "-v", "-keystore", str(path), "-alias", alias,
                         "-storepass:env", "TRX_CERT_PASSWORD"], env=environment,
                        capture_output=True, text=True, check=True)
expected = "CD:F9:7D:63:CC:B9:9F:C8:7E:56:46:B3:82:6E:B2:D0:14:E3:CF:C6"
if expected not in result.stdout:
    raise SystemExit("Signing certificate does not match the configured Google SHA-1.")
with open(os.environ["GITHUB_ENV"], "a") as output:
    output.write(f"ANDROID_KEYSTORE_FILE={path}\nANDROID_KEYSTORE_PASSWORD={password}\nANDROID_KEY_ALIAS={alias}\n")
print("Signing SHA-1: " + expected)
