import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import harness


APP = '<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="dev.metis.agent.capabilitytest"><application android:debuggable="true" android:allowBackup="false"/></manifest>'
TEST = '<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="dev.metis.agent.capabilitytest.test"><instrumentation android:name="dev.metis.agent.testing.IsolatedAndroidJUnitRunner" android:targetPackage="dev.metis.agent.capabilitytest"/></manifest>'


class HarnessSafetyTest(unittest.TestCase):
    def test_exact_app_and_instrumentation_identities(self):
        self.assertEqual(harness.validate_manifests(APP, TEST)["application_id"], harness.POLICY["target_package"])

    def test_production_app_and_target_are_refused(self):
        for app, test in ((APP.replace('package="dev.metis.agent.capabilitytest"', 'package="dev.metis.agent"'), TEST),
                          (APP, TEST.replace('targetPackage="dev.metis.agent.capabilitytest"', 'targetPackage="dev.metis.agent"'))):
            with self.assertRaises(ValueError):
                harness.validate_manifests(app, test)

    def test_swapped_roles_unknown_target_runner_and_shared_uid_are_refused(self):
        for app, test in ((TEST, APP), (APP, TEST.replace("IsolatedAndroidJUnitRunner", "AndroidJUnitRunner")),
                          (APP.replace('<application', '<application'), TEST.replace("capabilitytest\"", "other\"")),
                          (APP.replace('<application', '<application').replace('package=', 'android:sharedUserId="other" package=', 1), TEST)):
            with self.assertRaises(ValueError):
                harness.validate_manifests(app, test)

    def test_production_destructive_utilities_never_call_adb(self):
        device = harness.Device("unused", "fake")
        with patch.object(device, "_command") as command:
            for package in ("dev.metis.agent", "dev.metis.agent.test", "arbitrary.app"):
                with self.assertRaises(ValueError):
                    device.uninstall(package)
            for arguments in (("shell", "pm", "clear", "dev.metis.agent"),
                              ("shell", "am", "force-stop", "dev.metis.agent"),
                              ("uninstall", "dev.metis.agent"), ("shell", "reboot")):
                with self.assertRaises(ValueError):
                    device.read(*arguments)
            command.assert_not_called()

    def test_changed_apk_is_refused_before_install(self):
        with tempfile.TemporaryDirectory() as folder:
            apk = Path(folder) / "synthetic.apk"
            apk.write_bytes(b"changed bytes")
            device = harness.Device("unused", "fake")
            with patch.object(device, "_command") as command, self.assertRaises(ValueError):
                device.install(apk, harness.POLICY["target_package"], "incorrect")
            command.assert_not_called()

    def test_read_only_queries_reject_shell_injection_and_production_writes(self):
        device = harness.Device("unused", "fake")
        with patch.object(device, "_command") as command:
            for arguments in (("shell", "pm", "list", "packages; rm -rf /data"),
                              ("shell", "run-as", "dev.metis.agent", "sh", "-c", "rm -rf databases"),
                              ("shell", "pm", "path", "dev.metis.agent; reboot"),
                              ("shell", "run-as", harness.POLICY["target_package"], "ls", "databases; reboot")):
                with self.assertRaises(ValueError):
                    device.read(*arguments)
            command.assert_not_called()

    def test_physical_gate_requires_current_full_emulator_evidence(self):
        verified = {field: "current" for field in ("app_sha256", "test_sha256", "source_sha256")}
        with self.assertRaises(ValueError):
            harness.check_emulator_attestation(None, verified)
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "result.json"
            value = dict(status="PASS", mode="suite", emulator=True, isolation_proof_passed=True,
                         emulator_reboot_verified=True,
                         apk_verification=verified)
            path.write_text(json.dumps(value), encoding="utf-8")
            harness.check_emulator_attestation(path, verified)
            for field in verified:
                changed = dict(verified, **{field: "stale"})
                with self.assertRaises(ValueError):
                    harness.check_emulator_attestation(path, changed)

    def test_additional_configured_production_package_is_refused(self):
        with patch.dict(harness.POLICY, production_packages=["dev.metis.agent", "another.production"]):
            with self.assertRaises(ValueError):
                harness.assert_target("another.production")


if __name__ == "__main__":
    unittest.main()
