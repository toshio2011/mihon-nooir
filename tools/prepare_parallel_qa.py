#!/usr/bin/env python3
"""Isolate a temporary v1.0.17 QA APK without modifying any release source.

Run ONLY against a disposable, already-integrated pinned Mihon checkout.
The repository overlay and normal FOSS build keep their production identity.
"""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
PRODUCTION_BASE_ID = "dev.nooir.mihontrial"
QA_BASE_ID = "dev.nooir.mihonqa4"
QA_VERSION_NAME = "1.0.17-angled-qa"
PRODUCTION_NAME = '<string name="app_name" translatable="false">MihonNooir</string>'
QA_NAME = '<string name="app_name" translatable="false">MihonNooir QA4</string>'


def substitute_once(source: str, old: str, new: str, filename: Path) -> str:
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"QA isolation anchor missing or duplicated ({count}) in {filename}: {old}")
    return source.replace(old, new, 1)


def prepare(upstream: Path) -> None:
    upstream = upstream.resolve()
    gradle = upstream / "app/build.gradle.kts"
    strings = upstream / "i18n/src/commonMain/moko-resources/base/strings.xml"
    production_version = (ROOT / "config/version.txt").read_text(encoding="utf-8").strip()
    if not all(part.isdigit() for part in production_version.split(".")) or len(production_version.split(".")) != 3:
        raise RuntimeError("Unexpected production version format")

    # Validate BOTH files fully before modifying either one. Any upstream
    # layout drift fails closed, leaving the production build unchanged.
    original_gradle = gradle.read_text(encoding="utf-8")
    original_strings = strings.read_text(encoding="utf-8")
    updated_gradle = substitute_once(
        original_gradle,
        f'applicationId = "{PRODUCTION_BASE_ID}"',
        f'applicationId = "{QA_BASE_ID}"',
        gradle,
    )
    updated_gradle = substitute_once(
        updated_gradle,
        f'versionName = "{production_version}"',
        f'versionName = "{QA_VERSION_NAME}"',
        gradle,
    )
    updated_gradle = substitute_once(
        updated_gradle,
        'buildConfigField("boolean", "UPDATER_ENABLED", "${Config.enableUpdater}")',
        'buildConfigField("boolean", "UPDATER_ENABLED", "false")',
        gradle,
    )
    updated_strings = substitute_once(original_strings, PRODUCTION_NAME, QA_NAME, strings)

    # Keep Gradle's existing FOSS suffix (.foss), which gives the final QA
    # package dev.nooir.mihonqa4.foss versus official dev.nooir.mihontrial.foss.
    if 'applicationIdSuffix = ".foss"' not in updated_gradle:
        raise RuntimeError("Expected upstream FOSS applicationIdSuffix missing")
    gradle.write_text(updated_gradle, encoding="utf-8")
    strings.write_text(updated_strings, encoding="utf-8")
    print(f"QA-only identity prepared: {QA_BASE_ID}.foss / {QA_VERSION_NAME} / MihonNooir QA4")
    print(f"Updater disabled only in the disposable QA build. Original package: {PRODUCTION_BASE_ID}.foss")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit("Usage: python3 tools/prepare_parallel_qa.py PATH_TO_DISPOSABLE_UPSTREAM")
    prepare(Path(sys.argv[1]))
