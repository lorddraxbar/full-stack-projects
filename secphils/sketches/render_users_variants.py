#!/usr/bin/env python3
"""Render each Users-card variant to a PNG via headless Chrome (2x), trim
trailing whitespace with PIL."""
import re, subprocess, os
from PIL import Image

ROOT = "/Users/draxbarroga/Git/full-stack-projects/secphils/sketches/users_card_variants.html"
OUTDIR = "/Users/draxbarroga/Git/full-stack-projects/secphils/sketches/users_card"
CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
os.makedirs(OUTDIR, exist_ok=True)

src = open(ROOT, encoding="utf-8").read()
style = re.search(r"<style>(.*?)</style>", src, re.S).group(1)
body = src.split("<body>", 1)[1].split("</body>", 1)[0]

# rows sit between the intro <p class="page"> and </body>; split on the row markers
after_intro = body.split('<p class="page">', 1)[1]
chunks = {}
for k in ("A", "B", "C"):
    start = after_intro.find(f'<div class="row" data-shot="{k}">')
    if start < 0:
        print(f"!! row {k} not found"); continue
    # end = the next row marker or the end
    ends = [after_intro.find(f'<div class="row" data-shot="{x}">') for x in ("A","B","C") if x != k]
    ends = [e for e in ends if e > start]
    end = min(ends) if ends else len(after_intro)
    chunks[k] = after_intro[start:end].strip()

def standalone(k, inner):
    return f"""<!DOCTYPE html><html><head><meta charset="utf-8">
<link rel="preconnect" href="https://fonts.googleapis.com">
<link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&display=swap" rel="stylesheet">
<link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.1/css/all.min.css">
<style>{style}
  body{{padding:0;margin:0}}
  .row{{max-width:1160px;margin:0 auto;padding:0}}
</style></head>
<body>
{inner}
</body></html>"""

for k in ("A","B","C"):
    if k not in chunks: continue
    html = standalone(k, chunks[k])
    p = f"{OUTDIR}/_render_{k}.html"
    open(p, "w", encoding="utf-8").write(html)
    out = f"{OUTDIR}/users_card_{k}.png"
    cmd = [CHROME, "--headless=new", "--disable-gpu", "--no-sandbox",
           "--hide-scrollbars", "--force-device-scale-factor=2",
           "--window-size=1200,540", f"--screenshot={out}", f"file://{p}"]
    subprocess.run(cmd, capture_output=True, timeout=60)
    im = Image.open(out).convert("RGB")
    w, h = im.size
    px = im.load()
    def is_bg(x, y):
        r, g, b = px[x, y]
        return abs(r-245) < 7 and abs(g-246) < 7 and abs(b-248) < 7
    bottom = h
    for y in range(h - 1, -1, -1):
        if any(not is_bg(x, y) for x in range(0, w, 7)):
            bottom = y + 1
            break
    im.crop((0, 0, w, min(h, bottom + 10))).save(out)
    print(f"{k}: {w}x{bottom+10}  {os.path.getsize(out)} bytes")
print("DONE")
