"""Combined diagnostic branch must retain both independent Android test gates."""
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
workflow = (ROOT / ".github/workflows/build-apk.yml").read_text()
smoke = (ROOT / ".github/scripts/panel-diagnostic-smoke.sh").read_text()
overlay = (ROOT / "tools/apply_overlay.py").read_text()
host = (ROOT / "tests/run.sh").read_text()
for token in ("NooirEpubXtcInstrumentedTest", "NooirWebTocHtmlInstrumentedTest",
              "NooirWebEpubEndToEndInstrumentedTest", "OK (18 tests)",
              "OK (6 tests)", "OK (2 tests)"):
    assert token in smoke, token
for token in ("universal-converter-ui-screenshots.sh", "web-epub-ui-screenshots.sh",
              "android-web-toc-instrumentation.txt", "android-epub-xtc-instrumentation.txt"):
    assert token in workflow, token
for token in ("NooirEpubBook", "NooirFinishedBookPreview", "NooirWebArticle",
              "NooirWebTocPagination", "ReflowEpubWriter"):
    assert '-keep class dev.nooir.' in overlay and token in overlay, token
for token in ("NooirWebImageSignatureTest", "NooirEpubBookTest",
              "test_web_article_save_cleanup.py", "NooirLocalConversionPolicyTest"):
    assert token in host, token
print("PASS: both Android QA suites coexist in the isolated build")
