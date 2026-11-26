"""Loopback-only synthetic library for real Compose window captures.

No personal library, external source requests, extensions or mutations.
"""
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from io import BytesIO
import json
import re
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

COVERS = {i: cover(i) for i in range(1, 7)}
CARDS = [{"id": i, "title": t, "author": "QA fixture", "description": "Synthetic demonstration library.",
          "thumbnailUrl": f"/api/v1/manga/{i}/thumbnail", "inLibrary": True}
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
        self.reply(404, b"not found", "text/plain")

    def do_POST(self):
        size = int(self.headers.get("Content-Length", "0"))
        if self.path != "/api/graphql" or not 0 < size <= 100000:
            self.reply(400, b"invalid request", "text/plain")
            return
        query = json.loads(self.rfile.read(size))["query"]
        if "query NooirLibrary" not in query or "mutation" in query:
            self.reply(400, b"unsupported fixture operation", "text/plain")
            return
        data = {"data": {"mangas": {"totalCount": len(CARDS), "nodes": CARDS}}}
        self.reply(200, json.dumps(data).encode(), "application/json")
        print("LIBRARY_OK", flush=True)

if __name__ == "__main__":
    ThreadingHTTPServer(("127.0.0.1", 4567), Handler).serve_forever()
