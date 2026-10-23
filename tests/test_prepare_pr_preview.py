"""PR APK identity must never collide with installed stable MangaNooir."""
from pathlib import Path
from tempfile import TemporaryDirectory
import subprocess
import sys
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / "tools" / "prepare_pr_preview.py"


class PreviewIdentityTest(unittest.TestCase):
    def fixture(self, root):
        app = root / "app/build.gradle.kts"
        app.parent.mkdir(parents=True)
        app.write_text('defaultConfig { applicationId = "dev.nooir.mihon" }\n'
                       'create("foss") { applicationIdSuffix = ".foss" }\n', encoding="utf-8")
        strings = root / "i18n/src/commonMain/moko-resources/base/strings.xml"
        strings.parent.mkdir(parents=True)
        strings.write_text('<resources><string name="app_name" translatable="false">'
                           'MangaNooir</string></resources>\n', encoding="utf-8")
        return app, strings

    def test_panel_preview_is_separate_app_and_distinctly_labeled(self):
        with TemporaryDirectory() as d:
            root = Path(d)
            app, strings = self.fixture(root)
            done = subprocess.run([sys.executable, str(SCRIPT), d, "32"],
                                  capture_output=True, text=True)
            self.assertEqual(done.returncode, 0, done.stderr)
            self.assertIn('applicationId = "dev.nooir.mihon.pr32"', app.read_text())
            self.assertIn('applicationIdSuffix = ".foss"', app.read_text())
            self.assertIn(">MihonNooir Panels</string>", strings.read_text())
            self.assertNotIn('applicationId = "dev.nooir.mihon"', app.read_text())
            self.assertIn("dev.nooir.mihon.pr32.foss", done.stdout)

    def test_other_pr_has_distinct_identity(self):
        with TemporaryDirectory() as d:
            root = Path(d)
            app, strings = self.fixture(root)
            done = subprocess.run([sys.executable, str(SCRIPT), d, "99"],
                                  capture_output=True, text=True)
            self.assertEqual(done.returncode, 0, done.stderr)
            self.assertIn("dev.nooir.mihon.pr99", app.read_text())
            self.assertIn(">MangaNooir PR 99</string>", strings.read_text())

    def test_invalid_number_leaves_stable_app_identity_unchanged(self):
        with TemporaryDirectory() as d:
            root = Path(d)
            app, strings = self.fixture(root)
            done = subprocess.run([sys.executable, str(SCRIPT), d, "32;rm"],
                                  capture_output=True, text=True)
            self.assertNotEqual(done.returncode, 0)
            self.assertIn('applicationId = "dev.nooir.mihon"', app.read_text())
            self.assertIn(">MangaNooir</string>", strings.read_text())


if __name__ == "__main__":
    unittest.main()
