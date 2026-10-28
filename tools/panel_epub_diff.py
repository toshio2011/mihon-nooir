#!/usr/bin/env python3
"""Compare two fixed-image EPUB exports without copying or decoding artwork.

Uses EPUB spine order and exact SHA-256 image identity. This is a change
locator, NOT a guarantee that a crop kept all dialogue or source artwork.
No non-standard dependencies or network requests are required.
"""
from __future__ import annotations

import argparse
import difflib
import hashlib
import json
import posixpath
import sys
import xml.etree.ElementTree as ET
import zipfile
from dataclasses import asdict, dataclass
from pathlib import Path
from urllib.parse import unquote, urlsplit

IMAGE_MEDIA = {"image/jpeg", "image/png", "image/gif", "image/webp", "image/bmp"}
DOC_MEDIA = {"application/xhtml+xml", "text/html"}


@dataclass(frozen=True)
class Frame:
    name: str
    sha256: str


def _tag(element: ET.Element) -> str:
    return element.tag.rsplit("}", 1)[-1]


def _archive_path(base: str, href: str) -> str:
    parsed = urlsplit(href)
    if parsed.scheme or parsed.netloc or not parsed.path:
        raise ValueError(f"External/empty EPUB resource reference: {href!r}")
    path = posixpath.normpath(posixpath.join(posixpath.dirname(base), unquote(parsed.path)))
    if path.startswith("../") or path in ("..", ".") or path.startswith("/"):
        raise ValueError(f"Invalid EPUB resource path: {href!r}")
    return path


def scan_epub(epub: Path) -> list[Frame]:
    try:
        with zipfile.ZipFile(epub) as archive:
            container = ET.fromstring(archive.read("META-INF/container.xml"))
            rootfile = next((e for e in container.iter() if _tag(e) == "rootfile"), None)
            if rootfile is None or not rootfile.get("full-path"):
                raise ValueError("EPUB container does not name an OPF package")
            opf_path = rootfile.attrib["full-path"]
            package = ET.fromstring(archive.read(opf_path))
            manifest = next((e for e in package if _tag(e) == "manifest"), None)
            spine = next((e for e in package if _tag(e) == "spine"), None)
            if manifest is None or spine is None:
                raise ValueError("Missing OPF manifest or reading spine")
            items = {
                item.attrib["id"]: item
                for item in manifest
                if _tag(item) == "item" and item.get("id")
            }

            ordered_images: list[str] = []
            for ref in spine:
                if _tag(ref) != "itemref":
                    continue
                key = ref.get("idref")
                if key not in items:
                    raise ValueError(f"Spine references missing manifest ID: {key}")
                item = items[key]
                mime = item.get("media-type", "")
                resource = _archive_path(opf_path, item.attrib["href"])
                if mime in IMAGE_MEDIA:
                    ordered_images.append(resource)
                elif mime in DOC_MEDIA:
                    document = ET.fromstring(archive.read(resource))
                    for node in document.iter():
                        tag = _tag(node)
                        if tag not in ("img", "image"):
                            continue
                        href = (node.get("src") or node.get("href") or
                                node.get("{http://www.w3.org/1999/xlink}href"))
                        if href and not href.startswith("data:"):
                            ordered_images.append(_archive_path(resource, href))
            if not ordered_images:
                raise ValueError("No raster-image reading frames found in the EPUB spine")
            return [
                Frame(name, hashlib.sha256(archive.read(name)).hexdigest())
                for name in ordered_images
            ]
    except (zipfile.BadZipFile, KeyError, ET.ParseError) as exc:
        raise ValueError(f"Cannot parse fixed-image EPUB {epub}: {exc}") from exc


def compare_epubs(old: Path, new: Path) -> dict:
    before = scan_epub(old)
    after = scan_epub(new)
    match = difflib.SequenceMatcher(
        a=[frame.sha256 for frame in before],
        b=[frame.sha256 for frame in after],
        autojunk=False,
    )
    exact = 0
    changes = []
    for action, a0, a1, b0, b1 in match.get_opcodes():
        if action == "equal":
            exact += a1 - a0
            continue
        changes.append({
            "kind": action,
            "old_range": [a0 + 1, a1],
            "new_range": [b0 + 1, b1],
            "old_count": a1 - a0,
            "new_count": b1 - b0,
            "old_first": before[a0].name if a1 > a0 else None,
            "new_first": after[b0].name if b1 > b0 else None,
        })
    return {
        "old_epub": str(old),
        "new_epub": str(new),
        "old_frames": len(before),
        "new_frames": len(after),
        "exact_unchanged_frames": exact,
        "changed_sections": changes,
        "warning": (
            "Exact PNG/JPEG payload comparison only; resized/recompressed identical "
            "artwork may appear changed. Frame indices can shift after a split. "
            "This cannot certify correct reading order, no missing artwork, "
            "or no clipped dialogue; visually review changed sections."
        ),
    }


def as_text(report: dict) -> str:
    lines = [
        "MihonNooir — panel export comparison",
        f"Old frames: {report['old_frames']}  New frames: {report['new_frames']}",
        f"Exact unchanged frames: {report['exact_unchanged_frames']}",
        f"Changed sections: {len(report['changed_sections'])}",
        "",
    ]
    if not report["changed_sections"]:
        lines.append("No changed frame payloads detected.")
    else:
        for change in report["changed_sections"]:
            a, b = change["old_range"], change["new_range"]
            lines.append(
                f"{change['kind']:7} old {a[0]}–{a[1]} "
                f"({change['old_count']} frames) → new {b[0]}–{b[1]} "
                f"({change['new_count']} frames)"
            )
            lines.append(
                f"         first files: {change['old_first'] or 'none'} → "
                f"{change['new_first'] or 'none'}"
            )
    lines.extend(["", report["warning"]])
    return "\n".join(lines) + "\n"


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("old_epub", type=Path)
    parser.add_argument("new_epub", type=Path)
    parser.add_argument("--json", action="store_true", help="Print machine-readable change sections")
    parser.add_argument("-o", "--output", type=Path, help="Write report to a local file")
    args = parser.parse_args(argv)
    try:
        report = compare_epubs(args.old_epub, args.new_epub)
    except (OSError, ValueError) as exc:
        parser.exit(2, f"Comparison failed: {exc}\n")
    output = json.dumps(report, indent=2) + "\n" if args.json else as_text(report)
    if args.output:
        args.output.write_text(output, encoding="utf-8")
    else:
        sys.stdout.write(output)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
