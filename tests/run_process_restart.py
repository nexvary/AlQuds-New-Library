"""Run only on a dedicated emulator after connectedDebugAndroidTest installs both APKs."""
import re
import subprocess

PACKAGE = "org.alquds.library"
RUNNER = f"{PACKAGE}.test/androidx.test.runner.AndroidJUnitRunner"


def run_probe(method):
    result = subprocess.run(
        ["adb", "shell", "am", "instrument", "-w", "-r", "-e", "class",
         f"{PACKAGE}.ProcessRestartProbe#{method}", RUNNER],
        capture_output=True, text=True, timeout=120, check=True,
    )
    output = result.stdout + result.stderr
    print(output, flush=True)
    # am instrument may return exit code zero even when JUnit fails.
    if not re.search(r"OK\s*\(1 test\)", output) or "FAILURES!!!" in output:
        raise RuntimeError(f"Process restart probe {method} did not pass exactly one test")


if __name__ == "__main__":
    run_probe("prepare")
    subprocess.run(["adb", "shell", "am", "force-stop", PACKAGE], check=True, timeout=30)
    subprocess.run(["adb", "shell", "am", "force-stop", PACKAGE + ".test"], check=True, timeout=30)
    run_probe("verify")
    print("Process restart: 2 probes passed in separate Android processes.", flush=True)
