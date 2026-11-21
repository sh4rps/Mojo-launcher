#!/usr/bin/env python3
"""Builds 'Robin SMP Pack' for Minecraft 1.21.11 from the vanilla client jar (downloaded at build time)."""
import io, json, os, sys, zipfile, urllib.request
from PIL import Image, ImageDraw

VER = "1.21.11"
OUT = "Robin-SMP-Pack-1.21.11.zip"

def get(url):
    with urllib.request.urlopen(url, timeout=120) as r:
        return r.read()

man = json.loads(get("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"))
vurl = next(v["url"] for v in man["versions"] if v["id"] == VER)
vj = json.loads(get(vurl))
jar = zipfile.ZipFile(io.BytesIO(get(vj["downloads"]["client"]["url"])))
names = set(jar.namelist())
pv = vj.get("pack_version") or {}
fmt = pv.get("resource_major") or pv.get("resource") or 75
print("resource pack format:", fmt, pv)

def read(p): return jar.read(p)
def rjson(p): return json.loads(read(p))
def img(p): return Image.open(io.BytesIO(read(p))).convert("RGBA")

pack = {}   # path -> bytes
def put_json(p, d): pack[p] = json.dumps(d, indent=1).encode()
def put_img(p, im):
    b = io.BytesIO(); im.save(b, "PNG"); pack[p] = b.getvalue()

A = "assets/minecraft/"

# ---------------------------------------------------------------- low shield
shield_models = sorted(n for n in names if n.startswith(A + "models/item/shield") and n.endswith(".json"))
print("shield models:", shield_models)
for p in shield_models:
    d = rjson(p)
    disp = d.setdefault("display", {})
    print(p, json.dumps(disp)[:400])
    for k in ("firstperson_righthand", "firstperson_lefthand"):
        t = disp.setdefault(k, {"rotation": [0, 180, 5], "translation": [0, 0, -10], "scale": [1.25, 1.25, 1.25]})
        tr = list(t.get("translation", [0, 0, 0]))
        tr[1] = max(-80, tr[1] - 5)          # lower in view
        t["translation"] = tr
    put_json(p, d)

# ---------------------------------------------------------------- swords 1 pixel smaller (in hand)
handheld = rjson(A + "models/item/handheld.json").get("display", {})
print("handheld display:", json.dumps(handheld)[:600])
K = 15 / 16
sw = sorted(n for n in names if n.startswith(A + "models/item/") and n.endswith("_sword.json"))
print("swords:", sw)
for p in sw:
    d = rjson(p)
    disp = {}
    src = d.get("display") or handheld
    for k in ("firstperson_righthand", "firstperson_lefthand", "thirdperson_righthand", "thirdperson_lefthand"):
        if k in src:
            t = json.loads(json.dumps(src[k]))
            t["scale"] = [round(s * K, 4) for s in t.get("scale", [1, 1, 1])]
            disp[k] = t
    d.setdefault("display", {}).update(disp)
    put_json(p, d)

# ---------------------------------------------------------------- low fire (flames half height, bottom anchored)
def squash_frames(im, factor):
    w = im.width; frames = im.height // w
    out = Image.new("RGBA", im.size, (0, 0, 0, 0))
    nh = max(1, int(w * factor))
    for i in range(frames):
        f = im.crop((0, i * w, w, (i + 1) * w)).resize((w, nh), Image.NEAREST)
        out.paste(f, (0, i * w + (w - nh)))
    return out
for n in ("fire_0", "fire_1"):
    p = f"{A}textures/block/{n}.png"
    if p in names: put_img(p, squash_frames(img(p), 0.5))

# ---------------------------------------------------------------- clear water
def alpha_mul(im, m):
    r, g, b, a = im.split()
    return Image.merge("RGBA", (r, g, b, a.point(lambda v: int(v * m))))
for n, m in (("water_still", 0.45), ("water_flow", 0.45), ("water_overlay", 0.45)):
    p = f"{A}textures/block/{n}.png"
    if p in names: put_img(p, alpha_mul(img(p), m))
p = A + "textures/misc/underwater.png"
if p in names: put_img(p, alpha_mul(img(p), 0.25))

# ---------------------------------------------------------------- fast grass: calm top texture, shorter grass plants
p = A + "textures/block/grass_block_top.png"
if p in names:
    im = img(p); px = im.load(); w, h = im.size
    tot = [0, 0, 0]
    for y in range(h):
        for x in range(w):
            for c in range(3): tot[c] += px[x, y][c]
    avg = [t / (w * h) for t in tot]
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            px[x, y] = tuple(int(avg[c] * 0.65 + (r, g, b)[c] * 0.35) for c in range(3)) + (a,)
    put_img(p, im)
for n in ("short_grass", "fern"):
    p = f"{A}textures/block/{n}.png"
    if p in names:
        im = img(p); w = im.width
        out = Image.new("RGBA", im.size, (0, 0, 0, 0))
        nh = max(1, int(im.height * 0.6))
        out.paste(im.resize((im.width, nh), Image.NEAREST), (0, im.height - nh))
        put_img(p, out)

# ---------------------------------------------------------------- no particles, small crit
cnt = 0
for p in sorted(n for n in names if n.startswith(A + "textures/particle/") and n.endswith(".png")):
    im = img(p)
    if p.endswith("/critical_hit.png"):
        w, h = im.size; s = max(1, w // 2)
        small = im.resize((s, s), Image.NEAREST)
        out = Image.new("RGBA", im.size, (0, 0, 0, 0))
        out.paste(small, ((w - s) // 2, (h - s) // 2))
        put_img(p, out)
    else:
        put_img(p, Image.new("RGBA", im.size, (0, 0, 0, 0))); cnt += 1
print("particles removed:", cnt)

# ---------------------------------------------------------------- meta
pack["pack.mcmeta"] = json.dumps({"pack": {
    "description": [{"text": "Robin SMP Pack", "color": "green", "bold": True},
                    {"text": "\nmade by Robin / Claude", "color": "gray"}],
    "min_format": fmt, "max_format": fmt}}, indent=1).encode()
ic = Image.new("RGBA", (128, 128), (30, 120, 50, 255)); d = ImageDraw.Draw(ic)
d.rectangle((8, 8, 119, 119), outline=(255, 255, 255, 255), width=4)
d.text((44, 52), "ROBIN\n  SMP", fill=(255, 255, 255, 255))
put_img("pack.png", ic)

with zipfile.ZipFile(OUT, "w", zipfile.ZIP_DEFLATED) as z:
    for k, v in sorted(pack.items()): z.writestr(k, v)
print("wrote", OUT, len(pack), "files")
