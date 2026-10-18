"""Bounded emulator-only readiness check; never retries installation or instrumentation."""
import argparse
from pathlib import Path
import subprocess
import time


def wait_ready(sdk, serial, timeout=180):
    if not serial.startswith("emulator-") or not serial[9:].isdigit():
        raise ValueError("Readiness setup is restricted to emulator serials")
    adb = Path(sdk) / "platform-tools" / ("adb.exe" if __import__("os").name == "nt" else "adb")

    def query(*args):
        return subprocess.run([str(adb), "-s", serial, *args], capture_output=True, text=True, timeout=10)

    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        boot = query("shell", "getprop", "sys.boot_completed")
        package_manager = query("shell", "pm", "path", "android")
        if boot.returncode == 0 and boot.stdout.strip() == "1" and package_manager.returncode == 0 and package_manager.stdout.startswith("package:"):
            break
        time.sleep(1)  # Poll infrastructure readiness, never test results.
    else:
        raise RuntimeError("Emulator boot/package-manager readiness timed out")
    if query("shell", "getprop", "ro.kernel.qemu").stdout.strip() != "1":
        raise ValueError("Refusing animation setup on a non-emulator")
    for scale in ("window_animation_scale", "transition_animation_scale", "animator_duration_scale"):
        result = query("shell", "settings", "put", "global", scale, "0")
        if result.returncode != 0 or query("shell", "settings", "get", "global", scale).stdout.strip() not in ("0", "0.0"):
            raise RuntimeError("Could not disable emulator animation: " + scale)
    print("Emulator boot complete; package manager ready; all three animation scales verified zero")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sdk", required=True)
    parser.add_argument("--serial", required=True)
    args = parser.parse_args()
    wait_ready(args.sdk, args.serial)
