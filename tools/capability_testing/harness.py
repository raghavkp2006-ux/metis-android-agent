"""APK-verified synthetic-device harness. No production-package mutation API exists here."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

from emulator_ready import wait_ready

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
POLICY = json.loads((HERE / "policy.json").read_text(encoding="utf-8"))
ANDROID = "{http://schemas.android.com/apk/res/android}"
HASH_COMMAND = "'find databases shared_prefs files -type f -exec sha256sum {} \\; 2>/dev/null'"
PHASE3_SCREENSHOTS = (
    "phase3-heatmap-empty.png",
    "phase3-heatmap-populated.png",
    "phase3-heatmap-dark.png",
    "phase3-heatmap-font-200.png",
)


def digest(path):
    with Path(path).open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def source_digest():
    paths = [ROOT / "app/build.gradle.kts"]
    for folder in (ROOT / "app/src", HERE):
        paths += [p for p in folder.rglob("*") if p.is_file() and "__pycache__" not in p.parts]
    combined = hashlib.sha256()
    for path in sorted(set(paths)):
        combined.update(path.relative_to(ROOT).as_posix().encode())
        combined.update(bytes.fromhex(digest(path)))
    return combined.hexdigest()


def run(command, *, timeout=180, check=True):
    result = subprocess.run([str(part) for part in command], capture_output=True, timeout=timeout)
    output = result.stdout.decode("utf-8", errors="replace")
    if check and result.returncode:
        raise RuntimeError(f"Command failed ({result.returncode}): {command[0]}\n"
                           + result.stderr.decode("utf-8", errors="replace")[-2000:] + output[-2000:])
    return output


def assert_target(package):
    if package in POLICY["production_packages"] or package not in (POLICY["target_package"], POLICY["test_package"]):
        raise ValueError(f"REFUSED: package is not an allowlisted synthetic target: {package}")


def validate_manifests(app_xml, test_xml):
    app, test = ET.fromstring(app_xml), ET.fromstring(test_xml)
    app_id, test_id = app.get("package"), test.get("package")
    assert_target(app_id)
    assert_target(test_id)
    if app_id != POLICY["target_package"] or test_id != POLICY["test_package"]:
        raise ValueError("REFUSED: APK roles do not match the exact test application and instrumentation packages")
    if app.get(ANDROID + "sharedUserId") or test.get(ANDROID + "sharedUserId"):
        raise ValueError("REFUSED: shared Android UID")
    instruments = test.findall("instrumentation")
    if len(instruments) != 1:
        raise ValueError("REFUSED: expected exactly one instrumentation declaration")
    instrument = instruments[0]
    target = instrument.get(ANDROID + "targetPackage")
    assert_target(target)
    if target != app_id or instrument.get(ANDROID + "name") != POLICY["runner"]:
        raise ValueError("REFUSED: instrumentation target/runner differs from verified policy")
    application = app.find("application")
    if application is None or application.get(ANDROID + "debuggable") != "true":
        raise ValueError("REFUSED: capability-test application must be debuggable")
    if application.get(ANDROID + "allowBackup") != "false":
        raise ValueError("REFUSED: synthetic application backup must be disabled")
    return dict(application_id=app_id, instrumentation_package=test_id,
                instrumentation_target=target, runner=POLICY["runner"])


def verify(sdk, app, test):
    tool = Path(sdk) / "cmdline-tools/latest/bin" / ("apkanalyzer.bat" if os.name == "nt" else "apkanalyzer")
    if not tool.is_file():
        raise ValueError("APK manifest analyzer is required; no metadata-only fallback is permitted")
    app_xml = run([tool, "manifest", "print", app])
    test_xml = run([tool, "manifest", "print", test])
    result = validate_manifests(app_xml, test_xml)
    result.update(app_sha256=digest(app), test_sha256=digest(test),
                  variant=POLICY["variant"], git_commit=run(["git", "rev-parse", "HEAD"]).strip(),
                  source_sha256=source_digest(), synthetic_data_only=True, status="PASS")
    return result


class Device:
    def __init__(self, sdk, serial):
        self.sdk = Path(sdk)
        self.adb = Path(sdk) / "platform-tools" / ("adb.exe" if os.name == "nt" else "adb")
        self.serial = serial

    def read(self, *args, timeout=180, check=True):
        packages = {*POLICY["production_packages"], POLICY["target_package"], POLICY["test_package"]}
        allowed = args == ("shell", "pm", "list", "instrumentation")
        if len(args) == 3 and args[:2] == ("shell", "getprop"):
            allowed = args[2] in ("ro.kernel.qemu", "ro.product.model", "sys.boot_completed", "ro.build.version.release")
        if len(args) == 4 and args[:3] in (("shell", "pm", "path"), ("shell", "dumpsys", "package")):
            allowed = args[3] in packages
        if args[:2] == ("shell", "run-as"):
            allowed = len(args) == 6 and args[2] in POLICY["production_packages"] and args[3:] == (
                "sh", "-c", HASH_COMMAND)
            if args[2] == POLICY["target_package"] and len(args) == 5 and args[3] == "ls":
                allowed = re.fullmatch(r"[A-Za-z0-9_./-]+", args[4]) is not None
        if not allowed:
            raise ValueError("REFUSED: command is not an approved read-only device query")
        return self._command(*args, timeout=timeout, check=check)

    def _command(self, *args, timeout=180, check=True):
        if args[0] == "uninstall":
            assert_target(args[1])
        if args[:3] in (("shell", "pm", "clear"), ("shell", "am", "force-stop")):
            assert_target(args[3])
        if args[:3] == ("shell", "am", "start"):
            assert_target(args[-1].split("/")[0])
        if args[0] == "reboot" and self.read("shell", "getprop", "ro.kernel.qemu").strip() != "1":
            raise ValueError("REFUSED: reboot is restricted to the synthetic emulator")
        return run([self.adb, "-s", self.serial, *args], timeout=timeout, check=check)

    def production_snapshot(self):
        states = {}
        for package in POLICY["production_packages"]:
            apk_paths = self.read("shell", "pm", "path", package, check=False).strip()
            installed = apk_paths.startswith("package:")
            states[package] = dict(installed=installed, apk_paths=apk_paths)
            if installed:
                states[package]["metadata"] = self.read("shell", "dumpsys", "package", package)
                # Read only. Hashes are evidence, not a recoverable backup.
                states[package]["file_hashes"] = self.read("shell", "run-as", package, "sh", "-c",
                    HASH_COMMAND, check=False)
        return states

    def install(self, path, expected_id, expected_hash):
        assert_target(expected_id)
        if digest(path) != expected_hash:
            raise ValueError("REFUSED: APK changed after manifest verification")
        analyzer = self.sdk / "cmdline-tools/latest/bin" / ("apkanalyzer.bat" if os.name == "nt" else "apkanalyzer")
        manifest = ET.fromstring(run([analyzer, "manifest", "print", path]))
        actual_id = manifest.get("package")
        assert_target(actual_id)
        if actual_id != expected_id:
            raise ValueError("REFUSED: actual APK identity differs from the requested install role")
        for instrument in manifest.findall("instrumentation"):
            assert_target(instrument.get(ANDROID + "targetPackage"))
            if instrument.get(ANDROID + "targetPackage") != POLICY["target_package"]:
                raise ValueError("REFUSED: actual instrumentation target is not the isolated application")
        output = self._command("install", "-r", "-t", str(path), timeout=180)
        if "Success" not in output:
            raise RuntimeError("Synthetic APK installation did not report success")

    def uninstall(self, package):
        assert_target(package)
        output = self._command("uninstall", package, check=False)
        if "Success" not in output and self.read("shell", "pm", "path", package, check=False).startswith("package:"):
            raise RuntimeError("Synthetic package cleanup failed")

    def installed_target(self, app_hash, test_hash, output_dir):
        package = POLICY["target_package"]
        assert_target(package)
        for installed_id, expected_hash, name in ((package, app_hash, "installed-target.apk"),
                                                (POLICY["test_package"], test_hash, "installed-test.apk")):
            paths = self.read("shell", "pm", "path", installed_id).strip().splitlines()
            if len(paths) != 1 or not paths[0].startswith("package:"):
                raise ValueError("REFUSED: expected one installed base APK for the exact synthetic package")
            run([self.adb, "-s", self.serial, "pull", paths[0][8:], output_dir / name], timeout=60)
            if digest(output_dir / name) != expected_hash:
                raise ValueError("REFUSED: installed APK is not the verified APK")
        declaration = f"instrumentation:{POLICY['test_package']}/{POLICY['runner']} (target={package})"
        if declaration not in self.read("shell", "pm", "list", "instrumentation").splitlines():
            raise ValueError("REFUSED: installed instrumentation target differs from policy")

    def instrument(self, output_dir, test_class=None, log_name="instrumentation.log"):
        assert_target(POLICY["target_package"])
        arguments = ["shell", "am", "instrument", "-w", "-r"]
        if test_class:
            if not re.fullmatch(r"dev\.metis\.agent\.[A-Za-z0-9_.]+(?:#[A-Za-z0-9_]+)?", test_class):
                raise ValueError("REFUSED: unexpected test class")
            arguments += ["-e", "class", test_class]
        arguments += [f"{POLICY['test_package']}/{POLICY['runner']}"]
        log_path = output_dir / log_name
        with log_path.open("wb") as stream:
            process = subprocess.Popen([str(self.adb), "-s", self.serial, *arguments],
                                       stdout=stream, stderr=subprocess.STDOUT)
            try:
                code = process.wait(timeout=1800)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()
                raise RuntimeError("Synthetic instrumentation timed out") from None
        log = log_path.read_text(encoding="utf-8", errors="replace")
        if code:
            raise RuntimeError("Synthetic instrumentation client failed")
        if not re.search(r"OK \(\d+ tests?\)", log) or "FAILURES!!!" in log or "INSTRUMENTATION_FAILED" in log:
            raise RuntimeError("Instrumentation did not complete successfully; see instrumentation.log")
        return dict(tests=int(re.search(r"OK \((\d+) tests?\)", log)[1]),
                    skipped_statuses=len(re.findall(r"INSTRUMENTATION_STATUS_CODE: -3", log)), status="PASS")

    def pull_phase3_screenshots(self, output_dir):
        destination = output_dir / "screenshots"
        destination.mkdir(exist_ok=True)
        results = {}
        for name in PHASE3_SCREENSHOTS:
            command = [str(self.adb), "-s", self.serial, "exec-out", "run-as",
                       POLICY["target_package"], "cat", "files/" + name]
            pulled = subprocess.run(command, capture_output=True, timeout=60, check=False)
            if pulled.returncode or not pulled.stdout.startswith(b"\x89PNG\r\n\x1a\n"):
                raise RuntimeError(f"Could not retrieve the synthetic Phase 3 screenshot: {name}")
            path = destination / name
            path.write_bytes(pulled.stdout)
            results[name] = dict(sha256=digest(path), bytes=len(pulled.stdout))
        return results


def check_emulator_attestation(path, verified):
    if not path:
        raise ValueError("REFUSED: physical testing requires a passing full emulator attestation")
    attestation = json.loads(Path(path).read_text(encoding="utf-8"))
    if attestation.get("status") != "PASS" or attestation.get("mode") != "suite" or not attestation.get("emulator"):
        raise ValueError("REFUSED: emulator attestation did not pass the full suite")
    if not attestation.get("isolation_proof_passed"):
        raise ValueError("REFUSED: emulator isolation proof is missing")
    if not attestation.get("emulator_reboot_verified"):
        raise ValueError("REFUSED: emulator reboot/recovery proof is missing")
    for field in ("app_sha256", "test_sha256", "source_sha256"):
        if attestation.get("apk_verification", {}).get(field) != verified[field]:
            raise ValueError("REFUSED: emulator evidence does not bind the current APKs and source")


def device_run(args, verified):
    destination = Path(args.output)
    destination.mkdir(parents=True, exist_ok=False)
    device = Device(args.sdk, args.serial)
    emulator = device.read("shell", "getprop", "ro.kernel.qemu").strip() == "1"
    if not emulator:
        check_emulator_attestation(args.emulator_attestation, verified)
    if args.mode == "suite":
        if not args.proof:
            raise ValueError("REFUSED: full suite requires a successful same-device isolation proof")
        proof = json.loads(Path(args.proof).read_text(encoding="utf-8"))
        if proof.get("status") != "PASS" or proof.get("mode") != "proof" or proof.get("device_serial") != args.serial:
            raise ValueError("REFUSED: missing successful isolation proof for this exact device")
        if proof["apk_verification"] != verified:
            raise ValueError("REFUSED: proof does not bind these APKs/source")
    before = device.production_snapshot()
    for package in (POLICY["target_package"], POLICY["test_package"]):
        if device.read("shell", "pm", "path", package, check=False).strip().startswith("package:"):
            raise ValueError("REFUSED: existing synthetic installation needs a separately reviewed cleanup")
    result = dict(mode=args.mode, emulator=emulator, device_serial=args.serial,
                  apk_verification=verified, production_before=before, status="FAIL",
                  model=device.read("shell", "getprop", "ro.product.model").strip(),
                  android=device.read("shell", "getprop", "ro.build.version.release").strip())
    try:
        # Both final manifests are verified before the first device mutation.
        device.install(args.app, POLICY["target_package"], verified["app_sha256"])
        device.install(args.test, POLICY["test_package"], verified["test_sha256"])
        device.installed_target(verified["app_sha256"], verified["test_sha256"], destination)
        if device.production_snapshot() != before:
            raise RuntimeError("Production snapshot changed during synthetic installation")
        if args.mode == "proof":
            result["instrumentation"] = device.instrument(destination,
                "dev.metis.agent.testing.IsolationProofTest#seedSyntheticIsolationMarker")
            database = POLICY["target_package"] + ".metis-personal.db"
            result["synthetic_database"] = device.read("shell", "run-as", POLICY["target_package"],
                                                       "ls", "databases/" + database).strip()
            # Explicit process restart; the seeded encrypted DB must survive without replacement.
            assert_target(POLICY["target_package"])
            device._command("shell", "am", "force-stop", POLICY["target_package"])
            device._command("shell", "am", "start", "-W", "-n",
                        f"{POLICY['target_package']}/dev.metis.agent.MainActivity")
            result["restart_database_present"] = database in device.read("shell", "run-as",
                                                                         POLICY["target_package"], "ls", "databases")
            if not result["restart_database_present"]:
                raise RuntimeError("Synthetic database did not survive process restart")
            result["restart_instrumentation"] = device.instrument(destination,
                "dev.metis.agent.testing.IsolationProofTest#verifySyntheticIsolationMarkerSurvivesRestart",
                "restart-instrumentation.log")
            if emulator:
                device._command("reboot")
                wait_ready(device.sdk, device.serial)
                result["reboot_instrumentation"] = device.instrument(destination,
                    "dev.metis.agent.testing.IsolationProofTest#verifySyntheticIsolationMarkerSurvivesRestart",
                    "reboot-instrumentation.log")
                result["emulator_reboot_verified"] = True
        else:
            result["instrumentation"] = device.instrument(destination)
            if emulator:
                result["screenshots"] = device.pull_phase3_screenshots(destination)
            result["emulator_reboot_verified"] = proof.get("emulator_reboot_verified", False)
        result["isolation_proof_passed"] = True
        result["status"] = "PASS"
    except Exception as error:
        result["error"] = str(error)
        raise
    finally:
        # No AGP device runner is used, so only these exact allowlisted packages are cleaned up.
        try:
            device.uninstall(POLICY["test_package"])
            device.uninstall(POLICY["target_package"])
            result["cleanup_passed"] = not device.read("shell", "pm", "path", POLICY["target_package"],
                                                      check=False).strip().startswith("package:")
            result["production_after"] = device.production_snapshot()
            result["production_unchanged"] = before == result["production_after"]
            if not result["production_unchanged"] or not result["cleanup_passed"]:
                result["status"] = "FAIL"
        except Exception as cleanup_error:
            result["status"] = "FAIL"
            result["cleanup_passed"] = False
            result["cleanup_error"] = str(cleanup_error)
            raise
        finally:
            (destination / "result.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    if result["status"] != "PASS":
        raise RuntimeError("Device isolation/cleanup checks failed")
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=("verify", "proof", "suite"))
    parser.add_argument("--sdk", required=True)
    parser.add_argument("--app", required=True, type=Path)
    parser.add_argument("--test", required=True, type=Path)
    parser.add_argument("--output", required=True)
    parser.add_argument("--serial")
    parser.add_argument("--proof")
    parser.add_argument("--emulator-attestation")
    args = parser.parse_args()
    verified = verify(args.sdk, args.app, args.test)
    if args.mode == "verify":
        destination = Path(args.output)
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_text(json.dumps(verified, indent=2) + "\n", encoding="utf-8")
    else:
        if not args.serial:
            parser.error("--serial is required; implicit device selection is forbidden")
        device_run(args, verified)
    print(json.dumps(dict(status="PASS", mode=args.mode, application_id=verified["application_id"])))


if __name__ == "__main__":
    main()
