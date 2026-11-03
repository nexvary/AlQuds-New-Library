"""Small, explicit source policy. Not a substitute for a full secrets or dependency audit."""
from pathlib import Path
import re
import subprocess
import sys

SECRET_PATTERNS = (
    re.compile(r"-----BEGIN (?:RSA |DSA |EC |OPENSSH |ENCRYPTED )?PRIVATE KEY-----"),
    re.compile(r"\bAKIA[0-9A-Z]{16}\b"),
    re.compile(r"\bAIza[0-9A-Za-z_-]{35}\b"),
    re.compile(r"\bgh[pousr]_[0-9A-Za-z]{36,}\b"),
    re.compile(r"\bgithub_pat_[0-9A-Za-z_]{40,}\b"),
    re.compile(r"\bsk-(?:proj-)?[0-9A-Za-z_-]{32,}\b"),
)
FORBIDDEN_SUFFIXES = {".jks", ".keystore", ".p12", ".pfx", ".pem", ".key"}


def check_tree(root, paths):
    errors = []
    for relative in paths:
        path = root / relative
        if not path.is_file():
            continue
        if (path.suffix.lower() in FORBIDDEN_SUFFIXES or path.name == "local.properties"
                or path.name == ".env" or (path.name.startswith(".env.") and path.name != ".env.example")):
            errors.append(f"{relative}: local secret/signing file must not be tracked")
        try:
            text = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        for line_number, line in enumerate(text.splitlines(), 1):
            if any(pattern.search(line) for pattern in SECRET_PATTERNS):
                # Report location only, never expose the suspected credential.
                errors.append(f"{relative}:{line_number}: suspected credential/private key")
            if path.suffix in {".gradle", ".kts"}:
                if re.search(r"(?:https?://)[^'\"\s]*", line) and "http://" in line:
                    errors.append(f"{relative}:{line_number}: insecure dependency repository URL")
                if re.search(r"['\"][^'\"]+:[^'\"]+:[^'\"]*(?:\+|latest\.|SNAPSHOT)[^'\"]*['\"]", line):
                    errors.append(f"{relative}:{line_number}: dependency version must be fixed")
                if re.search(r"\bversion\s+['\"][^'\"]*(?:\+|latest\.|SNAPSHOT)[^'\"]*['\"]", line):
                    errors.append(f"{relative}:{line_number}: plugin version must be fixed")
    return errors


def main():
    root = Path(__file__).resolve().parents[1]
    tracked = subprocess.check_output(["git", "ls-files", "-z"], cwd=root).decode().split("\0")
    errors = check_tree(root, [path for path in tracked if path])
    for error in errors:
        print(error, file=sys.stderr)
    if errors:
        return 1
    print("Tracked source policy passed: known credential patterns, local signing files, dependency URLs and versions.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
