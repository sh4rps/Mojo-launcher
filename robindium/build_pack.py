#!/usr/bin/env python3
"""Robindium: Sodium + Sodium Extra + Podium (Pojav check removal) for Minecraft 1.21.11 Fabric.
Downloads the real, unmodified mods from Modrinth at build time and zips them as a ready-to-use mods folder."""
import hashlib, json, sys, urllib.parse, urllib.request, zipfile

MC = "1.21.11"
SLUGS = ["sodium", "sodium-extra", "podium"]
UA = {"User-Agent": "sh4rps/Mojo-launcher robindium-builder (github.com/sh4rps/Mojo-launcher)"}

def get(url):
    req = urllib.request.Request(url, headers=UA)
    with urllib.request.urlopen(req, timeout=120) as r:
        return r.read()

def versions(slug, strict=True):
    q = {"loaders": json.dumps(["fabric"])}
    if strict: q["game_versions"] = json.dumps([MC])
    return json.loads(get(f"https://api.modrinth.com/v2/project/{slug}/version?" + urllib.parse.urlencode(q)))

def pick(slug):
    vs = versions(slug, True)
    if not vs:
        print(f"!! {slug}: no version tagged {MC}; listing all fabric versions")
        allv = versions(slug, False)
        for v in allv[:8]: print("   ", v["version_number"], v["game_versions"][-6:])
        # fall back to the newest build that lists a 1.21.x release
        vs = [v for v in allv if any(g.startswith("1.21") for g in v["game_versions"])]
    rel = [v for v in vs if v["version_type"] == "release"] or vs
    if not rel: raise SystemExit(f"no usable version for {slug}")
    return rel[0]

chosen, files, seen = [], {}, set()
def add(slug_or_ver, is_version=False):
    v = slug_or_ver if is_version else pick(slug_or_ver)
    if v["id"] in seen: return
    seen.add(v["id"])
    f = next((x for x in v["files"] if x.get("primary")), v["files"][0])
    print(f"{v['project_id']} {v['name']} -> {f['filename']} (mc {v['game_versions'][-4:]})")
    data = get(f["url"])
    assert hashlib.sha512(data).hexdigest() == f["hashes"]["sha512"], "hash mismatch " + f["filename"]
    files[f["filename"]] = data
    chosen.append((v["name"], f["filename"]))
    for d in v.get("dependencies", []):
        if d["dependency_type"] == "required" and d.get("version_id"):
            dv = json.loads(get(f"https://api.modrinth.com/v2/version/{d['version_id']}"))
            if dv["project_id"] not in ("P7dR8mSH",):          # fabric-api: users already have it
                add(dv, True)
        elif d["dependency_type"] == "required" and d.get("project_id") and d["project_id"] not in ("P7dR8mSH",):
            print("   (needs project", d["project_id"], "- resolving latest)")
            add(d["project_id"])

for s in SLUGS: add(s)

readme = """ROBINDIUM for Minecraft %s (Fabric)
=====================================
Put every .jar from the mods/ folder into your game's mods folder (also needs Fabric API).

Contents (unmodified, downloaded from Modrinth):
%s

How it fits together
- Sodium: the rendering optimisation engine (chunk rendering, culling, GPU-side improvements). Its settings are in Options > Video Settings.
- Sodium Extra: extra settings pages (animations, particles, details, render, extras, FPS/coordinates overlay...), shown inside the same Sodium screen.
- Podium: tiny mod that disables Sodium's PojavLauncher check so it can start on Android launchers (MobileGlues / LTW renderers).

Do NOT install together with VulkanMod, Optifine, Embeddium or Rubidium - only one renderer mod at a time.
Sodium, Sodium Extra and Podium keep their own licences (see their Modrinth pages). This pack only bundles them.
""" % (MC, "\n".join(f"- {n}  ({f})" for n, f in chosen))

OUT = "Robindium-%s.zip" % MC
with zipfile.ZipFile(OUT, "w", zipfile.ZIP_DEFLATED) as z:
    for name, data in files.items(): z.writestr("mods/" + name, data)
    z.writestr("README.txt", readme)
print("wrote", OUT, list(files))
