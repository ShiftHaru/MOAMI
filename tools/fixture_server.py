"""G1 self-authored fixtures. No libraries, remote content, credentials or external binding."""
import argparse
import json
import struct
import time
import zlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlsplit

IMAGES = {
    "original.png": (1280, 960, (28, 110, 170)),
    "thumbnail.png": (160, 120, (28, 110, 170)),
    "responsive-small.png": (320, 240, (180, 70, 90)),
    "responsive-large.png": (1280, 960, (180, 70, 90)),
    "late-original.png": (1024, 768, (30, 140, 90)),
    "decorative.png": (32, 32, (160, 160, 30)),
    "background.png": (640, 480, (90, 40, 170)),
}


def png(width, height, color):
    def chunk(kind, data):
        return struct.pack("!I", len(data)) + kind + data + struct.pack("!I", zlib.crc32(kind + data))
    # A non-uniform, exact known source makes decoded dimension and byte checks deterministic.
    rows = b"".join(b"\0" + bytes(color if y % 32 < 16 else tuple(255-c for c in color)) * width
                    for y in range(height))
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack("!IIBBBBB", width, height, 8, 2, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(rows)) + chunk(b"IEND", b""))


PAGE = """<!doctype html><html lang="en"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>BrowserDownloader G1 fixture</title>
<style>body{font:16px sans-serif;margin:16px;background:#fff}img{max-width:100%;height:auto}
section{margin:24px 0}h1{font-size:22px}.spacer{height:1800px}.background{width:200px;height:150px;background-image:url('/images/background.png')}</style>
<h1>BrowserDownloader G1 fixture</h1><p>Self-authored, no login. No collector script or app bridge.</p>
<section><h2>1. Direct source</h2><img alt="g1-direct-original" src="/images/original.png" width="240" height="180"></section>
<section><h2>2. Link to original</h2><a href="/images/original.png"><img alt="g1-linked-thumbnail" src="/images/thumbnail.png" width="160" height="120"></a></section>
<section><h2>3. Responsive</h2><img alt="g1-responsive" src="/images/responsive-small.png" srcset="/images/responsive-small.png 320w, /images/responsive-large.png 1280w" sizes="240px" width="240" height="180"></section>
<section><h2>4. Decorative image</h2><img alt="" src="/images/decorative.png"></section>
<section><h2>5. CSS background</h2><div class="background"></div></section>
<div class="spacer">Scroll to load the final image.</div>
<section id="late"><h2>6. Added on intersection</h2><div id="late-slot"></div></section>
<p id="end">G1 fixture end</p>
<script>
const observer=new IntersectionObserver(entries=>{if(entries.some(e=>e.isIntersecting)){
const img=document.createElement('img');img.alt='g1-late-original';img.src='/images/late-original.png';img.width=240;img.height=180;
document.querySelector('#late-slot').append(img);observer.disconnect();}});
observer.observe(document.querySelector('#late'));
</script></html>"""


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        path = urlsplit(self.path).path
        if path in ("/", "/fixture.html"):
            body, mime = PAGE.encode(), "text/html; charset=utf-8"
        elif path == "/infinite.html":
            body = b'''<!doctype html><meta name="viewport" content="width=device-width,initial-scale=1">
<title>BrowserDownloader infinite fixture</title><div id="images" style="display:grid;grid-template-columns:repeat(5,100px)"></div>
<script>let count=0;const images=document.querySelector('#images');
function append(n){for(let i=0;i<n;i++){const image=document.createElement('img');
image.alt='infinite-'+count;image.src='/images/decorative.png?infinite='+count++;image.width=100;image.height=100;images.append(image);}}
append(200);addEventListener('scroll',()=>{if(scrollY+innerHeight>=document.documentElement.scrollHeight-400)append(40);});</script>'''
            mime = "text/html; charset=utf-8"
        elif path in ("/count.html", "/duration.html", "/other.html"):
            content = ('<div style="display:grid;grid-template-columns:repeat(8,32px)">' + ''.join(
                f'<img alt="count-{i}" src="/images/decorative.png?item={i}" width="32" height="32">'
                for i in range(600)) + '</div>') if path == "/count.html" else (
                    '<img alt="duration-original" src="/images/original.png"><div style="height:2000000px">Long scroll fixture</div>'
                    if path == "/duration.html" else '<img alt="other-document" src="/images/background.png">')
            body = ('<!doctype html><meta name="viewport" content="width=device-width,initial-scale=1">'
                    '<title>BrowserDownloader boundary fixture</title>' + content).encode()
            mime = "text/html; charset=utf-8"
        elif path.startswith("/images/") and path.removeprefix("/images/") in IMAGES:
            body = png(*IMAGES[path.removeprefix("/images/")])
            mime = "image/png"
        elif path == "/expected.json":
            body = json.dumps({k: {"width": v[0], "height": v[1]} for k,v in IMAGES.items()}).encode()
            mime = "application/json"
        else:
            self.send_error(404)
            return
        print(json.dumps({"time": time.time(), "path": path, "userAgent": self.headers.get("User-Agent", "")}), flush=True)
        self.send_response(200)
        self.send_header("Content-Type", mime)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *_):
        pass


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", type=int, default=8787)
    args = parser.parse_args()
    server = ThreadingHTTPServer(("127.0.0.1", args.port), Handler)
    print(f"Fixture listening on 127.0.0.1:{args.port}", flush=True)
    server.serve_forever()
