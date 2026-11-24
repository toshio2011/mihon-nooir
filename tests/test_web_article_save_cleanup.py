"""Structural safety checks for the single-article Web -> EPUB save path.

The complete Android save path is tested by the Android build; this host
regression catches an accidental removal of the only-new-document guard.
"""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
base = root / "overlay/app/src/main/java/dev/nooir/mangabridge"
worker = (base / "NooirWebExportWorker.kt").read_text(encoding="utf-8")
dialog = (base / "NooirWebToReaderDialog.kt").read_text(encoding="utf-8")
novel = (base / "NooirNovelBatchWorker.kt").read_text(encoding="utf-8")

assert 'const val SAVE_PICKER_CREATED = "web_save_picker_created"' in worker
assert 'NooirWebExportWorker.SAVE_PICKER_CREATED to (saveUri != null)' in dialog
assert 'val pickerCreated = inputData.getBoolean(SAVE_PICKER_CREATED, false)' in worker
assert 'if (uri != null && newlyCreated)' in worker
assert 'DocumentsContract.deleteDocument(applicationContext.contentResolver, uri)' in worker
assert worker.count('deleteIncompletePickerDocument(destination, pickerCreated)') == 3, (
    "Need early-invalid, cancellation and failed-write cleanup")
assert 'return Result.success()' in worker
assert worker.index('return Result.success()') < worker.index(
    'catch (e: CancellationException)')
assert 'DocumentsContract.deleteDocument(resolver, out)' in novel, (
    "Do not regress the already-protected multi-chapter save path")
print("PASS: only fresh single-article SAF output is cleaned on invalid, failed or cancelled work")
