#!/usr/bin/env python3
"""Give PR-built Android APKs separate install identities from release builds.

Run only after tools/apply_overlay.py, only for pull_request workflows.
Stable-release and main-branch APK identity is intentionally unchanged.
"""
from pathlib import Path
import re
import sys


def prepare(root: Path, pr_number: str) -> tuple[str, str]:
    if not re.fullmatch(r"[1-9][0-9]{0,5}", pr_number):
        raise ValueError("Pull-request number must be a positive decimal integer")
    app_build = root / "app/build.gradle.kts"
    i18n = root / "i18n/src/commonMain/moko-resources/base/strings.xml"

    original_id = 'applicationId = "dev.nooir.mihon"'
    new_id = f'applicationId = "dev.nooir.mihon.pr{pr_number}"'
    source = app_build.read_text(encoding="utf-8")
    if source.count(original_id) != 1:
        raise RuntimeError("Expected the exact MangaNooir applicationId after overlay")
    # The Mihon foss build adds its normal .foss suffix, e.g.
    # dev.nooir.mihon.pr32.foss, so installed stable data stays isolated.
    app_build.write_text(source.replace(original_id, new_id, 1), encoding="utf-8")

    old_label = '<string name="app_name" translatable="false">MangaNooir</string>'
    display = "MihonNooir Panels" if pr_number == "32" else f"MangaNooir PR {pr_number}"
    new_label = f'<string name="app_name" translatable="false">{display}</string>'
    strings = i18n.read_text(encoding="utf-8")
    if strings.count(old_label) != 1:
        raise RuntimeError("Expected MangaNooir's translated launcher label after overlay")
    i18n.write_text(strings.replace(old_label, new_label, 1), encoding="utf-8")
    print(f"PR #{pr_number}: built as {new_id}.foss; label: {display}")
    return new_id + ".foss", display


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit("Usage: prepare_pr_preview.py UPSTREAM_DIR PR_NUMBER")
    prepare(Path(sys.argv[1]).resolve(), sys.argv[2])
