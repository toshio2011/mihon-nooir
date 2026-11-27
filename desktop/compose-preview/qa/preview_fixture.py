"""Loopback-only synthetic library for real Compose window captures.

No personal library, external source requests, extensions or persistent mutations.
"""
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from io import BytesIO
import json
import re
import zipfile
from PIL import Image, ImageDraw

TITLES = ["The Paper Moon", "Library of Clouds", "Garden of Stars",
          "The Quiet Robot", "Mountain Post", "Little Lantern"]
PALETTES = [("#483e72", "#dec1df"), ("#245964", "#bce8dd"),
            ("#74494b", "#f5c6a1"), ("#34465f", "#b7d4e5"),
            ("#42604c", "#d8e4b7"), ("#805b3f", "#f0d595")]

def cover(i):
    bg, fg = PALETTES[i-1]
    img = Image.new("RGB", (320, 464), bg)
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((18, 18, 302, 446), radius=16, outline=fg, width=3)
    d.ellipse((100, 74, 220, 194), fill=fg)
    for j in range(3):
        x = 25+j*82
        d.polygon([(x, 315), (x+50, 188+j*14), (x+105, 315)], fill=fg)
    d.rectangle((36, 351, 284, 353), fill=fg)
    d.text((36, 372), TITLES[i-1], fill=fg)
    d.text((36, 406), "ORIGINAL QA SAMPLE", fill=fg)
    out = BytesIO()
    img.save(out, "PNG")
    return out.getvalue()

def comic_page(n):
    img = Image.new("RGB", (480, 800), "white")
    d = ImageDraw.Draw(img)
    for j in range(3):
        top = 35 + 245*j
        d.rectangle((18, top, 462, top+225), outline="black", width=4)
        x = 100 + 55*((n+j)%4)
        d.rounded_rectangle((x, top+70, x+95, top+160), radius=12, fill="#dddddd", outline="black", width=3)
        d.ellipse((x+17, top+88, x+36, top+107), fill="black")
        d.ellipse((x+57, top+88, x+76, top+107), fill="black")
        d.line((x+30, top+135, x+65, top+135), fill="black", width=3)
        d.rounded_rectangle((35, top+10, 260, top+50), radius=12, outline="black", width=2)
        d.text((48, top+24), f"Page {n+1} - Panel {j+1}", fill="black")
    out = BytesIO()
    img.save(out, "PNG")
    return out.getvalue()

PAGES = [comic_page(n) for n in range(3)]
def chapter_archive():
    out = BytesIO()
    with zipfile.ZipFile(out, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for n, page in enumerate(PAGES, 1):
            archive.writestr(f"{n:03d}.png", page)
    return out.getvalue()

CBZ = chapter_archive()
COVERS = {i: cover(i) for i in range(1, 7)}
CARDS = [{"id": i, "title": t, "author": "QA fixture", "description": "Synthetic demonstration library.",
          "thumbnailUrl": f"/api/v1/manga/{i}/thumbnail", "inLibrary": True, "unreadCount": i*2}
         for i, t in enumerate(TITLES, 1)]

class Handler(BaseHTTPRequestHandler):
    def reply(self, code, body, content_type):
        self.send_response(code)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if self.path == "/health":
            self.reply(200, b"ready", "text/plain")
            return
        match = re.fullmatch(r"/api/v1/manga/([1-6])/thumbnail", self.path)
        if match:
            self.reply(200, COVERS[int(match.group(1))], "image/png")
            print("COVER_OK " + match.group(1), flush=True)
            return
        page = re.fullmatch(r"/api/v1/manga/1/chapter/1/page/([0-2])", self.path)
        if page:
            index = int(page.group(1))
            self.reply(200, PAGES[index], "image/png")
            print(f"PAGE_OK {index}", flush=True)
            return
        if self.path == "/api/v1/chapter/101/download?markAsRead=false":
            self.reply(200, CBZ, "application/zip")
            print("CBZ_OK 101", flush=True)
            return
        self.reply(404, b"not found", "text/plain")

    def do_POST(self):
        size = int(self.headers.get("Content-Length", "0"))
        if self.path != "/api/graphql" or not 0 < size <= 100000:
            self.reply(400, b"invalid request", "text/plain")
            return
        query = json.loads(self.rfile.read(size))["query"]
        if "query NooirLibrary" in query and "mutation" not in query:
            paging = re.search(r"first: (\d+), offset: (\d+)", query)
            if not paging or not 1 <= int(paging.group(1)) <= 200:
                self.reply(400, b"invalid library pagination", "text/plain")
                return
            limit, offset = map(int, paging.groups())
            category = re.search(r"categoryIds: \[(\d+)\]", query)
            rows = CARDS
            if category:
                cid = int(category.group(1))
                rows = [c for c in CARDS if c["id"] in ({1, 2, 3} if cid == 1 else {4, 5, 6} if cid == 2 else set())]
            data = {"data": {"mangas": {"totalCount": len(rows), "nodes": rows[offset:offset+limit]}}}
            marker = "CATEGORY_LIBRARY_OK" if category else ("STARTUP_LIBRARY_OK" if limit == 1 else "LIBRARY_OK")
        elif "query NooirCategories" in query:
            data = {"data": {"categories": {"totalCount": 2, "nodes": [
                {"id": 1, "name": "Reading", "order": 1},
                {"id": 2, "name": "Plan to read", "order": 2}
            ]}}}
            marker = "CATEGORIES_OK"
        elif "query NooirManga" in query and "mutation" not in query:
            match = re.search(r"manga\(id: ([1-6])\)", query)
            if not match:
                self.reply(400, b"unknown fixture manga", "text/plain")
                return
            manga_id = int(match.group(1))
            card = CARDS[manga_id-1]
            chapters = [{"id": manga_id*100+n, "name": f"Chapter {n} — Original QA sample",
                         "chapterNumber": float(n), "sourceOrder": n, "isDownloaded": n == 1}
                        for n in range(1, 5)]
            data = {"data": {"manga": {"id": manga_id, "title": card["title"],
                                     "author": card["author"], "chapters": {"nodes": chapters}}}}
            marker = f"MANGA_OK {manga_id}"
        elif "mutation NooirChapterPages" in query and re.search(r"chapterId: 101\b", query):
            data = {"data": {"fetchChapterPages": {
                "pages": [f"/api/v1/manga/1/chapter/1/page/{n}" for n in range(3)],
                "chapter": {"id": 101, "mangaId": 1, "sourceOrder": 1, "pageCount": 3, "lastPageRead": 0},
                "syncConflict": None
            }}}
            marker = "READER_OK 101"
        else:
            print("REJECTED_OPERATION", flush=True)
            self.reply(400, b"unsupported fixture operation", "text/plain")
            return
        self.reply(200, json.dumps(data).encode(), "application/json")
        print(marker, flush=True)

if __name__ == "__main__":
    ThreadingHTTPServer(("127.0.0.1", 4567), Handler).serve_forever()
