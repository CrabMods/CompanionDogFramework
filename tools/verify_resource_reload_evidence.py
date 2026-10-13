"""Read back the bounded reload acceptance and the still-failed geometry audit."""

import hashlib
import json
import struct
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RELOAD = ROOT / "build/visual-tests/20261013T063524Z"
FULL = ROOT / "build/visual-tests/20261013T063848Z"
EXPECTED = ["reload.directory", "reload.zip", "reload.priority", "reload.blank", "reload.invalid_json",
            "reload.missing_texture", "reload.invalid_png", "reload.invalid_resolution", "reload.restore_default"]


def read(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def bound(path):
    return {"path": str(path.relative_to(ROOT)), "sha256": digest(path)}


def verify_frames(receipt):
    for row in receipt["scenarios"]:
        if not row["passed"] or not row["cleanupPassed"]:
            raise ValueError("Frame/cleanup failure: " + row["id"])
        for item in row["artifacts"]:
            path = Path(item["path"])
            if digest(path) != item["sha256"]:
                raise ValueError("Frame hash mismatch: " + str(path))
            data = path.read_bytes()
            if data[:8] != b"\x89PNG\r\n\x1a\n" or struct.unpack(">II", data[16:24]) != (1280, 720):
                raise ValueError("Frame size/signature mismatch")


def main():
    reload_receipt, full = read(RELOAD / "client-receipt.json"), read(FULL / "client-receipt.json")
    if (not reload_receipt["allScenariosPassed"] or reload_receipt["scenarioCount"] != 10
            or reload_receipt["observedScenarios"] != ["resource.baseline", *EXPECTED]):
        raise ValueError("Scoped reload acceptance is not complete")
    if (full["allScenariosPassed"] or full["scenarioCount"] != 98
            or full["animationGeometryAudit"] != "failed"
            or "Ear/tail ground penetration" not in full["failure"]):
        raise ValueError("Full geometry failure must remain visible")
    for receipt in (reload_receipt, full):
        if (receipt["processStartCount"] != 1 or receipt["worldLoadCount"] != 1
                or receipt["requestedScenarios"] != receipt["observedScenarios"]
                or receipt["cleanupFailureCount"] != 0):
            raise ValueError("Session coverage/cleanup mismatch")
        isolation = receipt["desktopIsolation"]
        if (isolation["violationCount"] or isolation["verifiedTicks"] < 1
                or not all(isolation[key] for key in ("hidden", "unfocused", "cursorFree", "windowed", "clipboardIsolated", "silentDevice"))):
            raise ValueError("Isolation was not verified")
        verify_frames(receipt)
    for folder in (RELOAD, FULL):
        if not all(row["restored"] for row in read(folder / "restoration.json")):
            raise ValueError("Test configuration was not restored")
        observations = read(folder / "reload-observations.json")
        if [o["id"] for o in observations] != EXPECTED or not all(o["rendererRebuilt"] for o in observations):
            raise ValueError("Renderer reload coverage mismatch")
    for item in read(FULL / "source-manifest.json"):
        if digest(ROOT / item["path"]) != item["sha256"]:
            raise ValueError("Current client-source input drift: " + item["path"])
    production = [item for item in read(ROOT / "build/visual-tests/20261013T063133Z/source-manifest.json")
                  if item["path"].replace("\\", "/").startswith("src/main/")]
    if any(digest(ROOT / item["path"]) != item["sha256"] for item in production):
        raise ValueError("Production code or assets were changed by this testing task")
    server_path = ROOT / "build/minecraft-mod-testing/gametest-9cb2b1bd-c4ef-492a-a3bf-d98cacc863bf.json"
    server = read(server_path)
    if not server["allScenariosPassed"] or server["requiredGameTestCount"] != 52:
        raise ValueError("Server behavior suite did not pass")
    for item in read(Path(server["sourceManifest"])):
        if item["path"].replace("\\", "/").startswith("src/main/") and digest(ROOT / item["path"]) != item["sha256"]:
            raise ValueError("Server behavior evidence is stale")
    tests = [ET.parse(path).getroot() for path in (ROOT / "build/test-results/test").glob("TEST-*.xml")]
    if sum(int(t.get("tests", "0")) for t in tests) != 15 or any(int(t.get(k, "0")) for t in tests for k in ("failures", "errors")):
        raise ValueError("JVM parser/default-asset suite is not green")
    build_log = ROOT / "build/resource-reload-build-20261013.log"
    if "minecraft_mod_build=passed command=build" not in build_log.read_text(encoding="utf-8-sig"):
        raise ValueError("Canonical Build did not pass")
    result = {
        "schemaVersion": 1, "scope": "resource-location/pack reload tests; not full visual acceptance",
        "resourceReloadPassed": True, "resourceScenarioCount": 10, "serverGameTestsPassed": 52,
        "jvmTestsPassed": 15, "staticAssetTestsPassed": 10, "canonicalBuildPassed": True,
        "fullVisualAccepted": False, "geometry": read(FULL / "geometry-audit.json"),
        "visualFramesCollected": full["scenarioCount"], "productionFilesChanged": 0,
        "runtimeIsolationVerified": True, "testConfigurationRestored": True,
        "noMultiplayerAcceptanceClaim": True,
        "evidence": [bound(folder / name) for folder in (RELOAD, FULL)
                     for name in ("client-receipt.json", "source-manifest.json", "restoration.json", "reload-observations.json")]
                    + [bound(server_path), bound(build_log), bound(FULL / "geometry-audit.json")],
    }
    output = ROOT / "build/resource-reload-verification-20261013.json"
    output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print("resource_reload=PASS; full_visual=FAIL (retained geometry defect); server=52/52; preserved_production=true")


if __name__ == "__main__":
    main()
