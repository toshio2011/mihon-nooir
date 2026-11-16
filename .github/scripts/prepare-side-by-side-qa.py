#!/usr/bin/env python3
"""Make a disposable side-by-side QA build from an already-overlaid checkout.

Only edits generated upstream files. Does NOT change any public release
sources, stable signing credentials or the installed official app.
"""
from pathlib import Path
import argparse

PUBLIC_ID = "dev.nooir.mihontrial.foss"
QA_ID = "dev.nooir.mihontrial.foss.qa"
PUBLIC_LABEL = '<string name="app_name" translatable="false">MihonNooir</string>'
QA_LABEL = '<string name="app_name" translatable="false">MihonNooir QA</string>'

def replace_exactly_once(path: Path, old: str, new: str) -> None:
    content = path.read_text(encoding="utf-8")
    count = content.count(old)
    if count != 1:
        raise ValueError(f"Expected one reviewed anchor in {path}; got {count}")
    path.write_text(content.replace(old, new, 1), encoding="utf-8")

def prepare(upstream: Path) -> None:
    root = upstream.resolve()
    gradle = root / "app/build.gradle.kts"
    translations = root / "i18n/src/commonMain/moko-resources/base/strings.xml"
    manifest = root / "app/src/main/AndroidManifest.xml"
    if not all(f.is_file() for f in (gradle, translations, manifest)):
        raise ValueError("Expected fresh fully integrated Mihon checkout")
    if 'applicationId = "dev.nooir.mihontrial"' not in gradle.read_text(encoding="utf-8"):
        raise ValueError("Expected MihonNooir public applicationId, refusing modification")
    original = '''        create("foss") {
            initWith(release)

            applicationIdSuffix = ".foss"
'''
    replacement = '''        create("foss") {
            initWith(release)

            // Disposable, debug-signed tester; NEVER an official in-place update.
            applicationIdSuffix = ".foss.qa"
            versionNameSuffix = "-qa"
'''
    # Verify all expected properties BEFORE any write to avoid half-patching.
    gradle_text = gradle.read_text(encoding="utf-8")
    strings = translations.read_text(encoding="utf-8")
    xml = manifest.read_text(encoding="utf-8")
    if gradle_text.count(original) != 1 or strings.count(PUBLIC_LABEL) != 1:
        raise ValueError("FOSS flavor or generated app label anchor changed")
    if 'android:authorities="' + "$" + '{applicationId}.provider"' not in xml:
        raise ValueError("FileProvider authority is not applicationId-derived")
    if 'android:label="@string/app_name"' not in xml:
        raise ValueError("Application label is not the localized app_name resource")

    replace_exactly_once(gradle, original, replacement)
    replace_exactly_once(translations, PUBLIC_LABEL, QA_LABEL)
    if 'applicationIdSuffix = ".foss.qa"' not in gradle.read_text(encoding="utf-8"):
        raise ValueError("QA application suffix missing after substitution")
    print(f"Prepared isolated debug tester {QA_ID} (official stays {PUBLIC_ID})")

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("upstream", type=Path)
    prepare(parser.parse_args().upstream)
