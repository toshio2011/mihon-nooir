#!/usr/bin/env python3
"""Inspect a PRIVATE MihonNooir panel diagnostic ZIP without uploading images.

Usage:
  python3 tools/panel_diagnostic_inspect.py capture.zip --json
  python3 tools/panel_diagnostic_inspect.py capture.zip --frame 143
The --frame option uses OUTPUT frame numbers, not unstable source page indices.
"""
import argparse
import json
from pathlib import Path
import struct
import sys
import zlib
import zipfile

MAX_ZIP = 120 * 1024 * 1024
MAX_ORIGINAL = 8 * 1024 * 1024
MAX_ORIGINAL_TOTAL = 80 * 1024 * 1024
FORBIDDEN = {"title", "url", "uri", "filename", "chaptername", "manganame",
             "sourceid", "accountid", "deviceip", "host"}

class InvalidDiagnostic(ValueError):
    pass

def require(ok, message):
    if not ok:
        raise InvalidDiagnostic(message)

def rects(value, width, height, field):
    require(isinstance(value, list), field + " is not an array")
    for index, rect in enumerate(value):
        require(isinstance(rect, list) and len(rect) == 4
                and all(type(x) is int for x in rect),
                f"{field}[{index}] is not an integer rectangle")
        x1, y1, x2, y2 = rect
        require(0 <= x1 < x2 <= width and 0 <= y1 < y2 <= height,
                f"{field}[{index}] exceeds its coordinate space")


def verify_gray_png(png, width, height):
    """Validate PNG chunks, decompress scanlines, and require opaque RGB=gray."""
    require(png.startswith(b"\x89PNG\r\n\x1a\n"), "Bad PNG signature")
    position = 8
    joined = []
    kind = None
    seen_end = False
    while position < len(png):
        require(position + 12 <= len(png), "Truncated PNG chunk")
        size = struct.unpack(">I", png[position:position + 4])[0]
        require(size <= 3 * 1024 * 1024 and position + 12 + size <= len(png),
                "Invalid PNG chunk size")
        tag = png[position + 4:position + 8]
        body = png[position + 8:position + 8 + size]
        checksum = struct.unpack(">I", png[position + 8 + size:position + 12 + size])[0]
        require(zlib.crc32(tag + body) & 0xffffffff == checksum,
                "Invalid PNG chunk checksum")
        if tag == b"IHDR":
            require(kind is None and size == 13, "Duplicate/malformed PNG header")
            pw, ph, depth, kind, compression, filtering, interlace = struct.unpack(">IIBBBBB", body)
            require((pw, ph) == (width, height) and depth == 8 and kind in (0, 2, 6)
                    and compression == filtering == interlace == 0,
                    "PNG must contain non-interlaced 8-bit grayscale/RGB/RGBA pixels")
        elif tag == b"IDAT":
            joined.append(body)
        elif tag == b"IEND":
            require(size == 0 and position + 12 == len(png), "Trailing bytes after PNG")
            seen_end = True
        position += 12 + size
    require(kind is not None and seen_end and joined, "Incomplete PNG")
    bpp = {0: 1, 2: 3, 6: 4}[kind]
    stride = width * bpp
    max_raw = height * (stride + 1)
    decompress = zlib.decompressobj()
    raw = decompress.decompress(b"".join(joined), max_raw + 1)
    require(len(raw) <= max_raw and decompress.eof, "PNG pixel payload out of bounds")
    raw += decompress.flush()
    require(len(raw) == max_raw, "PNG scanline length mismatch")
    previous = bytearray(stride)
    offset = 0
    for _ in range(height):
        method = raw[offset]
        row = bytearray(raw[offset + 1:offset + 1 + stride])
        require(method <= 4, "Unsupported PNG scanline filter")
        for i in range(stride):
            left = row[i - bpp] if i >= bpp else 0
            up = previous[i]
            corner = previous[i - bpp] if i >= bpp else 0
            if method == 1: predict = left
            elif method == 2: predict = up
            elif method == 3: predict = (left + up) // 2
            elif method == 4:
                p = left + up - corner
                distances = (abs(p - left), abs(p - up), abs(p - corner))
                predict = (left, up, corner)[distances.index(min(distances))]
            else: predict = 0
            row[i] = (row[i] + predict) & 255
        if kind != 0:
            for i in range(0, stride, bpp):
                require(row[i] == row[i + 1] == row[i + 2],
                        "PNG is not lossless grayscale")
                if bpp == 4:
                    require(row[i + 3] == 255, "Unexpected thumbnail transparency")
        previous = row
        offset += stride + 1

def inspect(path, frame=None):
    path = Path(path)
    require(path.is_file() and 0 < path.stat().st_size <= MAX_ZIP,
            "ZIP missing, empty or above the 120 MiB bound")
    with zipfile.ZipFile(path) as archive:
        infos = archive.infolist()
        names = [info.filename for info in infos]
        require(len(names) == len(set(names)), "Duplicate ZIP entry")
        require(all(name == "README.txt" or name == "summary.json" or
                    (name.startswith(("pages/", "thumbnails/", "originals/"))
                     and ".." not in name and not name.startswith("/")) for name in names),
                "Unexpected or unsafe ZIP entry")
        require(archive.testzip() is None, "ZIP CRC or member content invalid")
        require("summary.json" in names and "README.txt" in names,
                "Required archive metadata missing")
        summary = json.loads(archive.read("summary.json"))
        require(summary.get("schema") == 1 and summary.get("complete") is True,
                "Unsupported or incomplete diagnostic summary")
        pages = summary.get("sourcePageCount")
        output = summary.get("outputFrameCountExcludingCover")
        cover = summary.get("separateCoverFrameCount")
        require(type(pages) is int and 1 <= pages <= 3000, "Invalid page count")
        require(type(output) is int and output >= pages, "Invalid output count")
        require(type(cover) is int and cover in (0, 1), "Invalid cover count")
        require(summary.get("outputFrameCountIncludingCover") == cover + output,
                "Frame totals contradict summary")
        original_infos = [info for info in infos if info.filename.startswith("originals/")]
        require(len(original_infos) <= 24 and
                sum(info.file_size for info in original_infos) <= MAX_ORIGINAL_TOTAL and
                all(info.file_size <= MAX_ORIGINAL for info in original_infos),
                "Original page sampling exceeds privacy/memory caps")
        require(summary.get("originalImageCount") == len(original_infos),
                "Original entry count differs from summary")
        next_frame = cover + 1
        hit = None
        interesting = []
        for n in range(1, pages + 1):
            stem = f"{n:05d}"
            data_name = f"pages/{stem}.json"
            png_name = f"thumbnails/{stem}.png"
            require(data_name in names and png_name in names,
                    f"Missing page JSON or thumbnail for input {n}")
            item = json.loads(archive.read(data_name))
            require(item.get("schema") == 1 and item.get("sourceOrdinal") == n,
                    f"Bad ordinal/schema for page {n}")
            require(not (set(k.lower() for k in item) & FORBIDDEN),
                    f"Unexpected sensitive JSON key for page {n}")
            w, h = item.get("sourceWidth"), item.get("sourceHeight")
            tw, th = item.get("thumbWidth"), item.get("thumbHeight")
            require(all(type(x) is int and x > 0 for x in (w, h, tw, th)),
                    f"Invalid image dimensions on page {n}")
            require(tw <= 600 and th <= 900 and tw * th <= 1_048_576,
                    f"Detector thumbnail unbounded on page {n}")
            png = archive.read(png_name)
            verify_gray_png(png, tw, th)
            for key in ("detectorRegionsThumbnail",):
                rects(item.get(key), tw, th, key)
            for key in ("sourceRegionsWithPadding", "preOverviewSlicesSource", "outputSlicesSource"):
                rects(item.get(key), w, h, key)
            count = item.get("outputFrameCount")
            start = item.get("outputFrameStartOneBased")
            require(type(count) is int and count > 0 and start == next_frame,
                    f"Non-contiguous frame mapping on input page {n}")
            require(count == len(item["outputSlicesSource"]) ==
                    len(item.get("outputRotated", [])),
                    f"Output slice geometry count mismatch on page {n}")
            require(all(type(v) is bool for v in item["outputRotated"]),
                    f"Invalid rotation flags on page {n}")
            require(type(item.get("rtl")) is bool and
                    type(item.get("showFullPageBeforeSplits")) is bool,
                    f"RTL/overview flags not recorded on page {n}")
            require(item.get("mode") == "PANEL_BY_PANEL" or
                    item.get("path") == "whole-page",
                    f"Unexpected detector mode for page {n}")
            for event in item.get("events", []):
                rect = event.get("region")
                # Android skip events use source coordinates; detector events use thumb coordinates.
                limitw, limith = (w, h) if event.get("stage") == "android" else (tw, th)
                rects([rect], limitw, limith, "event.region")
            original = item.get("originalImage")
            if original is not None:
                require(original == f"originals/{stem}.source" and original in names,
                        f"Wrong original-image reference for page {n}")
            if frame is not None and start <= frame < start + count:
                hit = {"sourceOrdinal": n,
                       "chapterOrdinal": item.get("chapterOrdinal"),
                       "chapterPageOrdinal": item.get("chapterPageOrdinal"),
                       "firstFrame": start, "frameCount": count,
                       "path": item.get("path"), "reason": item.get("reason"),
                       "events": item.get("events", []), "originalImage": original}
            if count >= 4 or any(e.get("decision") == "reject" for e in item.get("events", [])):
                interesting.append(n)
            next_frame += count
        require(next_frame == cover + output + 1,
                "Source-to-output mapping does not cover the book")
        require(sum(name.startswith("pages/") for name in names) == pages and
                sum(name.startswith("thumbnails/") for name in names) == pages,
                "Extra/unmapped page records")
        if frame is not None:
            require(hit is not None or (cover == 1 and frame == 1),
                    f"Output frame {frame} not covered by archive")
        return {"valid": True, "sourcePages": pages, "totalOutputFrames": cover + output,
                "separateCoverFrames": cover, "originalSamples": len(original_infos),
                "suspiciousSourceOrdinals": interesting,
                "requestedOutputFrame": frame,
                "frameDetails": hit if hit is not None else
                    ({"separateCover": True} if frame == 1 and cover == 1 else None)}

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("zip", help="The user-chosen PRIVATE diagnostic ZIP")
    p.add_argument("--frame", type=int, help="One-based exported reader frame to investigate")
    p.add_argument("--json", action="store_true", help="Print structured JSON summary")
    args = p.parse_args()
    try:
        result = inspect(args.zip, args.frame)
    except (InvalidDiagnostic, OSError, ValueError, KeyError, zipfile.BadZipFile) as exc:
        print("INVALID diagnostic ZIP: " + str(exc), file=sys.stderr)
        return 1
    if args.json:
        print(json.dumps(result, indent=2))
    else:
        print("Verified ZIP: {sourcePages} source pages, {totalOutputFrames} output frames, "
              "{originalSamples} original samples".format(**result))
        if args.frame is not None:
            print(json.dumps(result["frameDetails"], indent=2))
    return 0

if __name__ == "__main__":
    sys.exit(main())
