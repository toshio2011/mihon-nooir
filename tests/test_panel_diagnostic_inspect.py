"""Private ZIP schema/CRC/geometry regression tests for Android diagnostics."""
import importlib.util
import json
from pathlib import Path
import struct
import tempfile
import unittest
import warnings
import zipfile
import zlib

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location(
    "panel_diagnostic_inspect", ROOT / "tools/panel_diagnostic_inspect.py")
validator = importlib.util.module_from_spec(spec)
spec.loader.exec_module(validator)

def chunk(tag, data):
    return (struct.pack(">I", len(data)) + tag + data +
            struct.pack(">I", zlib.crc32(tag + data) & 0xffffffff))

def png_gray(value=140, colored=False):
    channels = bytes([value, 10 if colored else value, value, 255])
    raw = b"\0" + channels * 2
    return (b"\x89PNG\r\n\x1a\n" +
            chunk(b"IHDR", struct.pack(">IIBBBBB", 2, 1, 8, 6, 0, 0, 0)) +
            chunk(b"IDAT", zlib.compress(raw)) +
            chunk(b"IEND", b""))

def page(n, start, count):
    return {
        "schema": 1, "sourceOrdinal": n,
        "chapterOrdinal": 1, "chapterPageOrdinal": n,
        "sourceWidth": 200, "sourceHeight": 100,
        "thumbWidth": 2, "thumbHeight": 1,
        "mode": "PANEL_BY_PANEL", "path": "detector",
        "reason": "detected", "rtl": True,
        "showFullPageBeforeSplits": True,
        "detectorRegionsThumbnail": [[0, 0, 2, 1]],
        "sourceRegionsWithPadding": [[0, 0, 200, 100]],
        "preOverviewSlicesSource": (
            [[0, 0, 100, 100], [100, 0, 200, 100]] if count > 1
            else [[0, 0, 200, 100]]),
        "outputSlicesSource": (
            [[0, 0, 200, 100], [0, 0, 100, 100], [100, 0, 200, 100]]
            if count > 1 else [[0, 0, 200, 100]]),
        "outputRotated": [False] * count,
        "traceTruncated": False, "traceDroppedEvents": 0,
        "outputFrameStartOneBased": start, "outputFrameCount": count,
        "events": [{"stage": "tier", "region": [0, 0, 2, 1],
                    "decision": "reject", "detail": "unsafe seam"}],
    }

def make_zip(path, overrides=None, png=None, duplicate=False):
    first = page(1, 2, 3)
    second = page(2, 5, 1)
    summary = {"schema": 1, "complete": True,
               "sourcePageCount": 2, "outputFrameCountExcludingCover": 4,
               "separateCoverFrameCount": 1, "outputFrameCountIncludingCover": 5,
               "originalImageCount": 0}
    components = {"first": first, "second": second, "summary": summary}
    if overrides:
        for target, key, value in overrides:
            if value is None:
                components[target].pop(key, None)
            else:
                components[target][key] = value
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("README.txt", "Private diagnostic fixture")
        z.writestr("summary.json", json.dumps(summary))
        for n, info in enumerate((first, second), 1):
            z.writestr(f"pages/{n:05d}.json", json.dumps(info))
            z.writestr(f"thumbnails/{n:05d}.png", png if png is not None else png_gray())
        if duplicate:
            with warnings.catch_warnings():
                warnings.simplefilter("ignore", UserWarning)
                z.writestr("pages/00001.json", json.dumps(first))

class DiagnosticInspectionTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.path = Path(self.tmp.name) / "diagnostic.zip"

    def tearDown(self):
        self.tmp.cleanup()

    def valid(self):
        return validator.inspect(self.path)

    def test_realistic_cover_and_overview_mapping(self):
        make_zip(self.path)
        result = self.valid()
        self.assertEqual(result["sourcePages"], 2)
        self.assertEqual(result["totalOutputFrames"], 5)
        self.assertEqual(result["suspiciousSourceOrdinals"], [1, 2])
        self.assertEqual(validator.inspect(self.path, 1)["frameDetails"],
                         {"separateCover": True})
        self.assertEqual(validator.inspect(self.path, 3)["frameDetails"]["sourceOrdinal"], 1)
        self.assertEqual(validator.inspect(self.path, 5)["frameDetails"]["sourceOrdinal"], 2)

    def assert_invalid(self, overrides=None, png=None, duplicate=False):
        make_zip(self.path, overrides, png, duplicate)
        with self.assertRaises(validator.InvalidDiagnostic):
            self.valid()

    def test_noncontiguous_frame_mapping(self):
        self.assert_invalid([("second", "outputFrameStartOneBased", 6)])

    def test_bad_source_geometry(self):
        self.assert_invalid([("second", "outputSlicesSource", [[0, 0, 201, 100]])])

    def test_missing_source_details(self):
        self.assert_invalid([("first", "outputRotated", None)])

    def test_privacy_name_field(self):
        self.assert_invalid([("second", "title", "Do not include manga name")])

    def test_cannot_claim_incomplete_zip_as_complete(self):
        self.assert_invalid([("summary", "complete", False)])

    def test_duplicate_entries_rejected(self):
        self.assert_invalid(duplicate=True)

    def test_malformed_png_rejected(self):
        self.assert_invalid(png=b"not PNG")

    def test_colored_thumbnail_rejected(self):
        self.assert_invalid(png=png_gray(colored=True))

    def test_overview_frame_must_not_disappear(self):
        self.assert_invalid([("first", "outputSlicesSource",
                              [[0, 0, 100, 100], [100, 0, 200, 100], [0, 0, 200, 100]])])

    def test_truncated_trace_must_be_declared(self):
        self.assert_invalid([("first", "traceTruncated", True)])

    def test_missing_output_frame_rejected(self):
        make_zip(self.path)
        with self.assertRaises(validator.InvalidDiagnostic):
            validator.inspect(self.path, 9000)

if __name__ == "__main__":
    unittest.main()
