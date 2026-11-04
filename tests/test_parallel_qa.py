"""Guardrails for QA-only Android identity mutation (no Android SDK required)."""
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "tools/prepare_parallel_qa.py"
VERSION = (ROOT / "config/version.txt").read_text().strip()


class ParallelQaIdentityTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.gradle = self.root / "app/build.gradle.kts"
        self.strings = self.root / "i18n/src/commonMain/moko-resources/base/strings.xml"
        self.gradle.parent.mkdir(parents=True)
        self.strings.parent.mkdir(parents=True)
        self.production_gradle = f'''android {{
  defaultConfig {{
    applicationId = "dev.nooir.mihontrial"
    versionName = "{VERSION}"
    buildConfigField("boolean", "UPDATER_ENABLED", "${{Config.enableUpdater}}")
  }}
  buildTypes {{
    create("foss") {{
      applicationIdSuffix = ".foss"
    }}
  }}
}}
'''
        self.production_strings = '<string name="app_name" translatable="false">MihonNooir</string>'
        self.gradle.write_text(self.production_gradle)
        self.strings.write_text(self.production_strings)

    def apply(self):
        return subprocess.run(
            [sys.executable, str(SCRIPT), str(self.root)],
            text=True, capture_output=True, check=False,
        )

    def test_parallel_qa_is_distinct_and_updater_disabled(self):
        run = self.apply()
        self.assertEqual(run.returncode, 0, run.stderr)
        qa = self.gradle.read_text()
        self.assertIn('applicationId = "dev.nooir.mihonqa2"', qa)
        self.assertIn('applicationIdSuffix = ".foss"', qa)
        self.assertIn('versionName = "1.0.17-dark-text-qa"', qa)
        self.assertIn('buildConfigField("boolean", "UPDATER_ENABLED", "false")', qa)
        self.assertNotIn('applicationId = "dev.nooir.mihontrial"', qa)
        self.assertEqual(
            self.strings.read_text(),
            '<string name="app_name" translatable="false">MihonNooir QA2</string>',
        )

    def test_fails_closed_when_label_anchor_changes(self):
        self.strings.write_text('<string name="app_name">Something Else</string>')
        run = self.apply()
        self.assertNotEqual(run.returncode, 0)
        self.assertEqual(self.gradle.read_text(), self.production_gradle)
        self.assertEqual(self.strings.read_text(), '<string name="app_name">Something Else</string>')

    def test_fails_closed_without_foss_suffix(self):
        self.gradle.write_text(self.production_gradle.replace('applicationIdSuffix = ".foss"', ''))
        before = self.gradle.read_text()
        run = self.apply()
        self.assertNotEqual(run.returncode, 0)
        self.assertEqual(self.gradle.read_text(), before)
        self.assertEqual(self.strings.read_text(), self.production_strings)

    def test_reapplication_refused(self):
        self.assertEqual(self.apply().returncode, 0)
        gradle_once, strings_once = self.gradle.read_text(), self.strings.read_text()
        self.assertNotEqual(self.apply().returncode, 0)
        self.assertEqual(self.gradle.read_text(), gradle_once)
        self.assertEqual(self.strings.read_text(), strings_once)


if __name__ == "__main__":
    unittest.main()
