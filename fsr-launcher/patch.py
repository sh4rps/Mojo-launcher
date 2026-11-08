#!/usr/bin/env python3
"""FSR Launcher patcher: turns a decoded Zalith Launcher 1.4.1.4 into FSR Launcher.
usage: patch.py <decoded_dir> <libs_dir> <extra_assets_dir>
"""
import os, re, shutil, sys, glob

D, LIBS, ASSETS = sys.argv[1], sys.argv[2], sys.argv[3]
HERE = os.path.dirname(os.path.abspath(__file__))
OLD_PKG, NEW_PKG = "com.movtery.zalithlauncher", "com.fsrlauncher.app"

def rd(p): return open(os.path.join(D, p), encoding="utf-8").read()
def wr(p, s): open(os.path.join(D, p), "w", encoding="utf-8").write(s)
def sub1(s, old, new, what):
    if old not in s: raise SystemExit("PATCH FAILED: " + what)
    return s.replace(old, new, 1)

# ---------- 1. manifest: identity ----------
m = rd("AndroidManifest.xml")
m = sub1(m, 'package="%s"' % OLD_PKG, 'package="%s"' % NEW_PKG, "package")
m = sub1(m, 'android:label="Zalith Launcher"', 'android:label="FSR Launcher"', "label")
for tail in ("DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION", "provider", "fx.provider", "androidx-startup"):
    m = m.replace(OLD_PKG + "." + tail, NEW_PKG + "." + tail)
wr("AndroidManifest.xml", m)
s = rd("res/values/strings.xml")
s = sub1(s, "com.movtery.zalithlauncher.storage_provider", NEW_PKG + ".storage_provider", "storage authority")
wr("res/values/strings.xml", s)
# apktool.yml: keep renameManifestPackage unset; make sure no stale value
y = rd("apktool.yml")
y = re.sub(r"renameManifestPackage:.*\n", "", y)
wr("apktool.yml", y)

# ---------- 2. electric theme ----------
PALETTE = {
 "background_app": "#ff070c1f", "background_app_error": "#ff3a0f1a",
 "background_item": "#d9101a3a", "background_item_disabled": "#d9331a24",
 "background_item_selected": "#d9203b8c",
 "background_menu_element": "#ff1e6bff", "background_menu_top": "#ff0b1745",
 "background_menu_top_error": "#ff7a1530", "background_overlay": "#ff0f1736",
 "background_ripple_effect": "#ff3a63ff", "background_sponsor_advanced": "#80b83c1f",
 "background_sponsor_intermediate": "#80bd6c2d", "background_status_bar": "#ff070c1f",
 "black_or_white": "#ffffffff", "button_background": "#ff18265a",
 "button_background_selected": "#ff2a4bc4", "dialog_background": "#ff0e1633",
 "edit_box_fill": "#ff16224d", "edit_box_fill_selected": "#ff1f3278",
 "icon_color": "#ffe8f1ff", "menu_bar": "#ffe8f1ff", "menu_bar_text": "#ffe8f1ff",
 "menu_overlay": "#be070c1f", "menu_progress": "#ff00e5ff",
 "mod_category_fill": "#80202c5e", "preference_fallback_accent_color": "#ff00b8ff",
 "primary_text": "#ffeaf2ff", "settings_category": "#ff00e5ff",
 "text_background_fill": "#b31a2756", "favorite_color": "#fffa7c7c",
}
for f in ("res/values/colors.xml", "res/values-night/colors.xml"):
    if not os.path.exists(os.path.join(D, f)): continue
    c = rd(f)
    for k, v in PALETTE.items():
        c = re.sub(r'(<color name="%s">)[^<]*(</color>)' % re.escape(k), r"\g<1>%s\g<2>" % v, c)
    wr(f, c)
# (theme parents are left untouched; dark mode is forced at runtime by fsr.Hz.init)

# ---------- 3. hide About ----------
lay = rd("res/layout/fragment_launcher.xml")
lay = sub1(lay, 'android:id="@id/about_button"', 'android:id="@id/about_button" android:visibility="gone"', "about button")
wr("res/layout/fragment_launcher.xml", lay)

# ---------- 4. Hz row in video settings ----------
v = rd("res/layout/settings_fragment_video.xml")
row = '''<com.movtery.zalithlauncher.ui.layout.AnimRelativeLayout android:id="@id/fsr_hz_layout" android:background="?android:selectableItemBackground" android:padding="@dimen/_12sdp" android:layout_width="fill_parent" android:layout_height="wrap_content">
                <TextView android:textColor="@color/black_or_white" android:id="@id/fsr_hz_title" android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="Unlock highest refresh rate (Hz)" android:layout_marginEnd="@dimen/_8sdp" android:layout_toStartOf="@id/fsr_hz_switch" android:layout_alignParentStart="true" />
                <TextView android:id="@id/fsr_hz_summary" android:layout_width="0.0dip" android:layout_height="wrap_content" android:text="Runs the launcher and the game at your screen's maximum refresh rate (90/120/144 Hz). Turn off to let the system decide." android:layout_below="@id/fsr_hz_title" android:layout_marginEnd="@dimen/_8sdp" android:layout_toStartOf="@id/fsr_hz_switch" android:layout_alignParentStart="true" />
                <Switch android:id="@id/fsr_hz_switch" android:layout_width="wrap_content" android:layout_height="wrap_content" android:checked="true" android:layout_centerVertical="true" android:layout_alignParentEnd="true" />
            </com.movtery.zalithlauncher.ui.layout.AnimRelativeLayout>
            '''
anchor = '<com.movtery.zalithlauncher.ui.layout.AnimRelativeLayout android:id="@id/ignoreNotch_layout"'
v = sub1(v, anchor, row + anchor, "video layout anchor")
v = v.replace('@id/fsr_hz_', '@+id/fsr_hz_')
wr("res/layout/settings_fragment_video.xml", v)

# ---------- 5. smali: new renderers + registration + hooks ----------
dst = os.path.join(D, "smali_classes3")
shutil.copytree(os.path.join(HERE, "smali"), dst, dirs_exist_ok=True)

p = "smali/com/movtery/zalithlauncher/renderer/Renderers.smali"
r = rd(p)
r = sub1(r, "    const/4 p1, 0x5\n\n    new-array p1, p1, [Lcom/movtery/zalithlauncher/renderer/RendererInterface;",
         "    const/4 p1, 0x7\n\n    new-array p1, p1, [Lcom/movtery/zalithlauncher/renderer/RendererInterface;", "renderer array size")
tail = "    const/4 v1, 0x4\n\n    aput-object v0, p1, v1\n"
add = ("    new-instance v0, Lcom/fsr/FsrLtwRenderer;\n\n    invoke-direct {v0}, Lcom/fsr/FsrLtwRenderer;-><init>()V\n\n    const/4 v1, 0x5\n\n    aput-object v0, p1, v1\n\n"
       "    new-instance v0, Lcom/fsr/FsrMobileGluesRenderer;\n\n    invoke-direct {v0}, Lcom/fsr/FsrMobileGluesRenderer;-><init>()V\n\n    const/4 v1, 0x6\n\n    aput-object v0, p1, v1\n")
r = sub1(r, tail, tail + "\n" + add, "renderer registration")
wr(p, r)

p = "smali_classes2/net/kdt/pojavlaunch/PojavApplication.smali"
a = rd(p)
a = sub1(a, ".method public onCreate()V\n    .locals 4\n",
         ".method public onCreate()V\n    .locals 4\n\n    invoke-static {p0}, Lfsr/Hz;->init(Landroid/app/Application;)V\n", "application hook")
wr(p, a)

# update checker off (it would point at the original Zalith)
p = "smali/com/movtery/zalithlauncher/feature/update/UpdateUtils$Companion.smali"
u = rd(p)
m_ = re.search(r"(\.method public final declared-synchronized updateCheckerMainProgram\(Landroid/content/Context;Z\)V\n\s*\.locals \d+\n)", u)
if not m_: raise SystemExit("PATCH FAILED: update checker")
u = u.replace(m_.group(1), m_.group(1) + "\n    return-void\n", 1)
wr(p, u)

# ---------- 6. branding strings (StringFog-encrypted constants) ----------
DEC = re.compile(r"invoke-static \{(\w+), (\w+)\}, Lcom/movtery/zalithlauncher/StringFog;->decrypt\(\[B\[B\)Ljava/lang/String;\s*\n\s*move-result-object (\w+)")
def tobyte(t): return int(t.strip()[:-1], 16) & 0xFF
def arrays(body):
    out = {}
    for mm in re.finditer(r":array_(\d+)\n\s*\.array-data 1\n(.*?)\.end array-data", body, re.S):
        out[mm.group(1)] = bytes(tobyte(x) for x in mm.group(2).split())
    return out
def rename(plain, fname):
    base = os.path.basename(fname)
    if "http" in plain or "ZalithVersion" in plain: return None
    # game data lives in the shared games folder: /sdcard/games/FSRLauncher
    if plain in ("/games/ZalithLauncher", "games/ZalithLauncher"): return plain.replace("ZalithLauncher", "FSRLauncher")
    if plain == "ZalithLauncher" and base.startswith("InfoDistributor"): return "FSRLauncher"
    if "Zalith Launcher" in plain: return plain.replace("Zalith Launcher", "FSR Launcher")
    return None
changed = 0
METHOD = re.compile(r"^\.method .*?^\.end method", re.S | re.M)
for f in glob.glob(os.path.join(D, "smali*", "**", "*.smali"), recursive=True):
    t = open(f, encoding="utf-8").read()
    if "StringFog;->decrypt" not in t: continue
    def do_method(mt):
        global changed
        body = mt.group(0)
        if "StringFog;->decrypt" not in body: return body
        arr = arrays(body)
        def repl(mm):
            global changed
            a, b, c = mm.group(1), mm.group(2), mm.group(3)
            pre = body[max(0, mm.start() - 900):mm.start()]
            fa = re.findall(r"fill-array-data %s, :array_(\d+)" % re.escape(a), pre)
            fb = re.findall(r"fill-array-data %s, :array_(\d+)" % re.escape(b), pre)
            if not fa or not fb or fa[-1] not in arr or fb[-1] not in arr: return mm.group(0)
            dat, key = arr[fa[-1]], arr[fb[-1]]
            if not key: return mm.group(0)
            plain = bytes(dat[i] ^ key[i % len(key)] for i in range(len(dat))).decode("utf-8", "replace")
            new = rename(plain, f)
            if new is None: return mm.group(0)
            changed += 1
            return 'const-string %s, "%s"' % (c, new.replace("\\", "\\\\").replace('"', '\\"').replace("\n", "\\n"))
        return DEC.sub(repl, body)
    t2 = METHOD.sub(do_method, t)
    if t2 != t: open(f, "w", encoding="utf-8").write(t2)
print("renamed strings:", changed)
if changed < 4: raise SystemExit("PATCH FAILED: too few branding strings renamed")

# ---------- 7. native libs, licenses ----------
libd = os.path.join(D, "lib", "arm64-v8a")
for n in ("libltw.so", "libmobileglues.so"):
    shutil.copy(os.path.join(LIBS, n), os.path.join(libd, n))
ad = os.path.join(D, "assets", "licenses")
os.makedirs(ad, exist_ok=True)
for n in os.listdir(ASSETS):
    shutil.copy(os.path.join(ASSETS, n), os.path.join(ad, n))

# ---------- 8. icon ----------
from PIL import Image, ImageDraw
def bolt(size, bg=True, rnd=False):
    S = size * 4
    im = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    if bg:
        g = Image.new("RGBA", (S, S))
        gd = ImageDraw.Draw(g)
        for y in range(S):
            k = y / S
            gd.line([(0, y), (S, y)], fill=(int(8 + 10 * k), int(14 + 40 * k), int(40 + 120 * k), 255))
        mask = Image.new("L", (S, S), 0)
        md = ImageDraw.Draw(mask)
        if rnd: md.ellipse((0, 0, S - 1, S - 1), fill=255)
        else: md.rounded_rectangle((0, 0, S - 1, S - 1), int(S * 0.22), fill=255)
        im.paste(g, (0, 0), mask)
    # lightning bolt
    pts = [(0.56, 0.12), (0.30, 0.54), (0.47, 0.54), (0.40, 0.88), (0.72, 0.43), (0.54, 0.43), (0.64, 0.12)]
    poly = [(x * S, y * S) for x, y in pts]
    glow = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    ImageDraw.Draw(glow).polygon(poly, fill=(0, 229, 255, 150))
    from PIL import ImageFilter
    glow = glow.filter(ImageFilter.GaussianBlur(S * 0.03))
    im = Image.alpha_composite(im, glow)
    ImageDraw.Draw(im).polygon(poly, fill=(255, 255, 255, 255), outline=(0, 229, 255, 255))
    return im.resize((size, size), Image.LANCZOS)
dens = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
for dn, k in dens.items():
    dd = os.path.join(D, "res", "mipmap-" + dn)
    if not os.path.isdir(dd): continue
    bolt(int(108 * k), bg=False).save(os.path.join(dd, "ic_launcher_foreground.png"))
    bolt(int(48 * k)).save(os.path.join(dd, "ic_launcher.png"))
    bolt(int(48 * k), rnd=True).save(os.path.join(dd, "ic_launcher_round.png"))
bgx = rd("res/drawable/ic_launcher_background.xml")
bgx = re.sub(r'android:startColor="[^"]*"', 'android:startColor="#ff0a1233"', bgx)
bgx = re.sub(r'android:endColor="[^"]*"', 'android:endColor="#ff1e6bff"', bgx)
bgx = re.sub(r'android:centerColor="[^"]*"', 'android:centerColor="#ff0f2a78"', bgx)
wr("res/drawable/ic_launcher_background.xml", bgx)

open(os.path.join(ad, "FSR_LAUNCHER_NOTICE.txt"), "w").write(
"FSR Launcher is a modified build of Zalith Launcher 1.4.1.4 (https://github.com/ZalithLauncher/ZalithLauncher),\n"
"copyright MovTery and contributors, licensed under GPL-3.0 (see GPL license file in this folder / https://www.gnu.org/licenses/gpl-3.0.txt).\n"
"Based on PojavLauncher (LGPL-3.0). Added: MobileGlues (LGPL-2.1, https://github.com/MobileGL-Dev/MobileGlues) and OpenLTW (https://github.com/artdeell/LTW).\n"
"Modified: branding, About page removed, electric theme, refresh-rate unlock option. Source of the changes: https://github.com/sh4rps/Mojo-launcher/tree/main/fsr-launcher\n")
print("patch done")
