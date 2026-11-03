"""Run only on a dedicated emulator after connectedDebugAndroidTest installs both APKs."""
import re
import os
import subprocess

PACKAGE = "org.alquds.library"
RUNNER = f"{PACKAGE}.test/androidx.test.runner.AndroidJUnitRunner"
ADB = ["adb", "-s", os.environ.get("ANDROID_SERIAL", "emulator-5554")]


def run_probe(method):
    result = subprocess.run(
        ADB + ["shell", "am", "instrument", "-w", "-r", "-e", "class",
         f"{PACKAGE}.ProcessRestartProbe#{method}", RUNNER],
        capture_output=True, text=True, timeout=120, check=False,
    )
    output = result.stdout + result.stderr
    print(output, flush=True)
    result.check_returncode()
    # am instrument may return exit code zero even when JUnit fails.
    if not re.search(r"OK\s*\(1 test\)", output) or "FAILURES!!!" in output:
        raise RuntimeError(f"Process restart probe {method} did not pass exactly one test")


if __name__ == "__main__":
    # The connected test runner may remove its APKs during teardown.
    for apk in ("app/build/outputs/apk/debug/app-debug.apk",
                "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"):
        subprocess.run(ADB + ["install", "-r", "-t", apk], check=True, timeout=120)
    run_probe("prepare")
    subprocess.run(ADB + ["shell", "am", "force-stop", PACKAGE], check=True, timeout=30)
    subprocess.run(ADB + ["shell", "am", "force-stop", PACKAGE + ".test"], check=True, timeout=30)
    run_probe("verify")
    print("Process restart: 2 probes passed in separate Android processes.", flush=True)
