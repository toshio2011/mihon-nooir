"""Fast host-side smoke for the signed Android in-place-upgrade shell contract.

Mock adb, not the Android package manager: this catches shell argument, version,
UID, and pipeline regressions before the expensive real Android 16 emulator job.
"""
import os
from pathlib import Path
import subprocess
import tempfile
import textwrap
import unittest

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / ".github/scripts/verify-signed-upgrade.sh"


class SignedUpgradeShellTest(unittest.TestCase):
    def _run(self, after_version="1.0.18", after_uid="10157"):
        with tempfile.TemporaryDirectory(prefix="nooir-upgrade-smoke-") as work:
            folder = Path(work)
            for sub in ("official-v1.0.17", "candidate-v1.0.18"):
                target = folder / sub / "app-x86_64-foss.apk"
                target.parent.mkdir(parents=True)
                target.write_bytes(b"fake APK - only for mocked shell regression")
            bin_dir = folder / "bin"
            bin_dir.mkdir()
            adb = bin_dir / "adb"
            adb.write_text(textwrap.dedent(
                """#!/usr/bin/env python3
                import os, pathlib, sys
                args = sys.argv[1:]
                state = pathlib.Path(os.environ["NOOIR_MOCK_ADB_STATE"])
                if args == ["wait-for-device"]:
                    sys.exit(0)
                if args[:2] == ["install", "-r"]:
                    state.write_text("old" if "v1.0.17/" in args[2] else "new")
                    print("Performing Streamed Install")
                    print("Success")
                    sys.exit(0)
                if args[:3] == ["shell", "pm", "path"]:
                    print("package:/data/app/~~synthetic/base.apk")
                    sys.exit(0)
                if args[:3] == ["shell", "dumpsys", "package"]:
                    is_new = state.read_text() == "new"
                    version = os.environ.get("NOOIR_MOCK_AFTER_VERSION", "1.0.18") if is_new else "1.0.17"
                    code = "1000018" if is_new else "1000017"
                    uid = os.environ.get("NOOIR_MOCK_AFTER_UID", "10157") if is_new else "10157"
                    print(f"  versionCode={code} minSdk=26 targetSdk=36")
                    print(f"  versionName={version}")
                    print(f"    userId={uid}")
                    sys.exit(0)
                print(f"Unexpected mock adb: {args}", file=sys.stderr)
                sys.exit(55)
                """
            ).lstrip())
            adb.chmod(0o755)
            env = dict(os.environ)
            env["PATH"] = str(bin_dir) + os.pathsep + env.get("PATH", "")
            env["NOOIR_MOCK_ADB_STATE"] = str(folder / "mock.state")
            env["NOOIR_MOCK_AFTER_VERSION"] = after_version
            env["NOOIR_MOCK_AFTER_UID"] = after_uid
            return subprocess.run(
                ["bash", str(SCRIPT)], cwd=folder, env=env,
                text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                timeout=20,
            )

    def test_valid_signed_version_and_uid_progression(self):
        result = self._run()
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("PASS: Android upgraded signed official v1.0.17 to v1.0.18", result.stdout)

    def test_wrong_new_version_is_rejected(self):
        result = self._run(after_version="1.0.17")
        self.assertNotEqual(result.returncode, 0)

    def test_package_uid_change_is_rejected(self):
        result = self._run(after_uid="10999")
        self.assertNotEqual(result.returncode, 0)


if __name__ == "__main__":
    unittest.main()
