"""Regression tests for content-based EPUB panel QA, using synthetic payloads."""
import json
import subprocess
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
from panel_epub_diff import compare_epubs, scan_epub, as_text  # noqa: E402


def write_epub(path: Path, frames: list[tuple[str, bytes]], *, svg: bool = False):
    """Build a tiny valid OPF spine; image bytes are test markers, not real manga."""
    container = (
        '<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0">'
        '<rootfiles><rootfile full-path="OEBPS/content.opf" '
        'media-type="application/oebps-package+xml"/></rootfiles></container>'
    )
    manifest = []
    spine = []
    pages = {}
    images = {}
    for i, (name, payload) in enumerate(frames, 1):
        image_path = f"OEBPS/images/{name}"
        page_path = f"OEBPS/pages/frame{i}.xhtml"
        if svg:
            body = (
                '<svg xmlns="http://www.w3.org/2000/svg">'
                f'<image xmlns:xlink="http://www.w3.org/1999/xlink" '
                f'xlink:href="../images/{name}"/></svg>'
            )
        else:
            body = f'<img src="../images/{name}" alt="synthetic"/>'
        pages[page_path] = f'<html xmlns="http://www.w3.org/1999/xhtml"><body>{body}</body></html>'
        images[image_path] = payload
        manifest.append(
            f'<item id="p{i}" href="pages/frame{i}.xhtml" '
            'media-type="application/xhtml+xml"/>'
        )
        spine.append(f'<itemref idref="p{i}"/>')
    opf = (
        '<package xmlns="http://www.idpf.org/2007/opf" version="3.0">'
        f'<manifest>{"".join(manifest)}</manifest>'
        f'<spine>{"".join(spine)}</spine></package>'
    )
    with zipfile.ZipFile(path, "w") as archive:
        archive.writestr("mimetype", "application/epub+zip")
        archive.writestr("META-INF/container.xml", container)
        archive.writestr("OEBPS/content.opf", opf)
        for name, data in pages.items():
            archive.writestr(name, data)
        for name, data in images.items():
            archive.writestr(name, data)


class PanelEpubComparisonTests(unittest.TestCase):
    def test_same_art_different_filenames_and_chapter_order(self):
        with tempfile.TemporaryDirectory() as d:
            first, second = Path(d) / "old.epub", Path(d) / "new.epub"
            write_epub(first, [("p001.png", b"PAGE-A"), ("p002.png", b"PAGE-B")])
            write_epub(second, [("f00001.png", b"PAGE-A"), ("f00002.png", b"PAGE-B")])
            result = compare_epubs(first, second)
            self.assertEqual((result["old_frames"], result["new_frames"]), (2, 2))
            self.assertEqual(result["exact_unchanged_frames"], 2)
            self.assertEqual(result["changed_sections"], [])

    def test_extra_panels_are_localized_not_mislabeled_missing_art(self):
        with tempfile.TemporaryDirectory() as d:
            old, new = Path(d) / "old.epub", Path(d) / "new.epub"
            write_epub(old, [(f"old{i}.png", x) for i, x in
                             enumerate((b"OVERVIEW", b"GROUPED", b"AFTER"), 1)])
            write_epub(new, [(f"new{i}.png", x) for i, x in
                             enumerate((b"OVERVIEW", b"PANEL-R", b"PANEL-L", b"AFTER"), 1)])
            result = compare_epubs(old, new)
            self.assertEqual(result["exact_unchanged_frames"], 2)
            self.assertEqual(len(result["changed_sections"]), 1)
            change = result["changed_sections"][0]
            self.assertEqual((change["old_range"], change["new_range"]), ([2, 2], [2, 3]))
            self.assertEqual((change["old_count"], change["new_count"]), (1, 2))
            self.assertIn("cannot certify", result["warning"])
            self.assertIn("old 2–2", as_text(result))

    def test_reading_spine_not_filename_sort_and_svg_image(self):
        with tempfile.TemporaryDirectory() as d:
            epub = Path(d) / "books.epub"
            write_epub(epub, [("z10.png", b"FIRST"), ("a1.png", b"SECOND")], svg=True)
            rows = scan_epub(epub)
            self.assertEqual([r.name for r in rows],
                             ["OEBPS/images/z10.png", "OEBPS/images/a1.png"])
            self.assertNotEqual(rows[0].sha256, rows[1].sha256)

    def test_missing_embedded_image_rejected(self):
        with tempfile.TemporaryDirectory() as d:
            epub = Path(d) / "damaged.epub"
            write_epub(epub, [("p1.png", b"ONE")])
            with zipfile.ZipFile(epub, "r") as source:
                entries = [(info.filename, source.read(info)) for info in source.infolist()
                           if not info.filename.endswith("images/p1.png")]
            with zipfile.ZipFile(epub, "w") as output:
                for name, content in entries:
                    output.writestr(name, content)
            with self.assertRaisesRegex(ValueError, "Cannot parse fixed-image EPUB"):
                scan_epub(epub)

    def test_json_cli_fails_on_invalid_input_without_claiming_success(self):
        with tempfile.TemporaryDirectory() as d:
            old, new = Path(d) / "old.epub", Path(d) / "new.epub"
            write_epub(old, [("p1.png", b"ONE")])
            write_epub(new, [("p1.png", b"ONE"), ("p2.png", b"TWO")])
            proc = subprocess.run(
                [sys.executable, str(ROOT / "tools" / "panel_epub_diff.py"),
                 str(old), str(new), "--json"],
                check=True, capture_output=True, text=True,
            )
            report = json.loads(proc.stdout)
            self.assertEqual(report["new_frames"], 2)
            self.assertEqual(report["exact_unchanged_frames"], 1)
            bad = subprocess.run(
                [sys.executable, str(ROOT / "tools" / "panel_epub_diff.py"),
                 str(old), str(Path(d) / "missing.epub")],
                capture_output=True, text=True,
            )
            self.assertNotEqual(bad.returncode, 0)
            self.assertIn("Comparison failed", bad.stderr)


if __name__ == "__main__":
    unittest.main()
