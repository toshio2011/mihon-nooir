"""Combined diagnostic branch must retain both independent Android test gates."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
workflow = (ROOT / ".github/workflows/build-apk.yml").read_text()
smoke = (ROOT / ".github/scripts/panel-diagnostic-smoke.sh").read_text()
overlay = (ROOT / "tools/apply_overlay.py").read_text()
host = (ROOT / "tests/run.sh").read_text()
web_ui = (ROOT / ".github/scripts/web-epub-ui-screenshots.sh").read_text()

success_count = "OK (2 tests)"
assert success_count in smoke
assert "android-web-epub-export-instrumentation.txt" in smoke
for token in (
    "NooirEpubXtcInstrumentedTest", "NooirWebTocHtmlInstrumentedTest",
    "NooirWebEpubEndToEndInstrumentedTest", "OK (18 tests)",
    "OK (6 tests)", "OK (2 tests)",
):
    assert token in smoke, token
for token in (
    "universal-converter-ui-screenshots.sh", "web-epub-ui-screenshots.sh",
    "android-web-toc-instrumentation.txt", "android-epub-xtc-instrumentation.txt",
):
    assert token in workflow, token
for token in (
    "NooirEpubBook", "NooirFinishedBookPreview", "NooirWebArticle",
    "NooirWebTocPagination", "ReflowEpubWriter",
):
    assert "-keep class dev.nooir." in overlay and token in overlay, token
for token in (
    "NooirWebImageSignatureTest", "NooirEpubBookTest",
    "test_web_article_save_cleanup.py", "NooirLocalConversionPolicyTest",
):
    assert token in host, token

assert 'adb shell am force-stop "$pkg"' in web_ui
assert web_ui.index('tap_visible "Nooir Tools" 5') < web_ui.index(
    'tap_visible "Universal Book Converter" 5') < web_ui.index(
    'tap_visible "Web novels" 4')
assert 'PASS: real Web pages & novels EPUB editor opened through Nooir Tools' in web_ui
assert 'exit 1' in web_ui
print("PASS: both Android QA suites and Web editor route coexist in isolated build")
