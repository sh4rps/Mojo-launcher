#!/usr/bin/env python3
"""Send Launcher V1.0 — usage make.py <apktool-decoded AllJava tree>
Fork of the Mojo (Pojav) launcher: own name/package/branding, no wiki/Discord, landscape, games folder, lighter, tuned defaults.
(GPL: license texts under assets/licenses are kept; source of these patches is in this repo.)"""
import sys, os, re, shutil
from PIL import Image, ImageDraw
d = sys.argv[1]; R = f'{d}/res'
def rw(p, f):
    s = open(p, encoding='utf-8').read(); n = f(s); open(p, 'w', encoding='utf-8').write(n)
PKG = 'com.send.launcher'
NAME = 'Send Launcher'

# ---------- manifest ----------
def man(s):
    s = re.sub(r'<activity[^>]*dev\.horizon\.android\.HorizonActivity".*?</activity>', '', s, flags=re.S)
    s = re.sub(r'<activity[^>]*com\.robin\.launcher\.Chooser".*?</activity>', '', s, flags=re.S)
    s = s.replace('android:name="git.artdeell.mojo.TestStorageActivity"/>',
      'android:name="git.artdeell.mojo.TestStorageActivity"><intent-filter><action android:name="android.intent.action.MAIN"/>'
      '<category android:name="android.intent.category.LAUNCHER"/></intent-filter></activity>', 1)
    assert 'category.LAUNCHER' in s
    s = s.replace('com.rustlauncher.app', PKG)
    s = re.sub(r'\s*<package android:name="dev\.horizon\.android"\s*/>', '', s)
    # landscape launcher
    for cls in ('git.artdeell.mojo.LauncherActivity', 'com.robin.launcher.ContentActivity', 'git.artdeell.mojo.TestStorageActivity', 'git.artdeell.mojo.MissingStorageActivity'):
        s = re.sub(r'(<activity )((?:(?!>).)*?android:name="%s")' % re.escape(cls), r'\1android:screenOrientation="sensorLandscape" \2', s, 1)
    s = s.replace('</application>', '<activity android:name="rl.Skins" android:screenOrientation="sensorLandscape" android:theme="@android:style/Theme.Material.NoActionBar" android:exported="false" android:configChanges="orientation|screenSize|keyboardHidden"/></application>', 1)
    return s
rw(f'{d}/AndroidManifest.xml', man)
rw(f'{d}/smali/git/artdeell/mojo/BuildConfig.smali', lambda s: s.replace('com.rustlauncher.app', PKG))
for f in (f'{d}/lib/arm64-v8a/libhorizon_android.so', f'{d}/assets/engine-data.zip'):
    if os.path.exists(f): os.remove(f)
for f in os.listdir(f'{d}/assets'):
    if f.startswith('horizon-'): os.remove(f'{d}/assets/{f}')

# ---------- branding, notifications, no wiki/discord ----------
def strings(s):
    s = s.replace('com.rustlauncher.app', PKG)
    s = re.sub(r'(<string name="app_short_name">)[^<]*', rf'\g<1>{NAME}', s)
    s = re.sub(r'(<string name="lazy_service_default_title">)[^<]*', rf'\g<1>{NAME}', s)
    s = re.sub(r'(<string name="modpack_install_notification_title">)[^<]*', rf'\g<1>{NAME}', s)
    s = re.sub(r'(<string name="notif_channel_name">)[^<]*', rf'\g<1>{NAME}', s)
    s = re.sub(r'(<string name="social_media_invite">)[^<]*', r'\1', s)
    s = re.sub(r'(<string name="mcl_button_social_media">)[^<]*', r'\1', s)
    s = re.sub(r'(<string name="mcl_tab_wiki">)[^<]*', r'\1', s)
    s = re.sub(r',? ?(?:please )?contact us on GitHub or on Discord', '', s)
    s = s.replace(' on our GitHub or on the Discord', '').replace('Rust Launcher', NAME).replace('RustLauncher', NAME)
    s = re.sub(r'(<string name="[^"]*">)([^<]*?)(MojoLauncher|Mojo Launcher|PojavLauncher|Pojav Launcher|MJ Launcher)', rf'\1\2{NAME}', s)
    return s.replace('</resources>', '    <string name="robin_skins_title">Skins &amp; Capes</string>\n</resources>')
for root, _, fs in os.walk(R):
    for f in fs:
        if f == 'strings.xml': rw(f'{root}/{f}', strings)
rw(f'{R}/values/strings.xml', lambda s: s)

def hide(s):
    for vid in ('news_button', 'social_divider', 'social_media_button'):
        s = re.sub(r'(<[\w.]+ android:id="@id/%s")' % vid, r'\1 android:visibility="gone"', s)
    s = s.replace('android:visibility="gone" android:visibility="gone"', 'android:visibility="gone"')
    return s
for lay in ('layout', 'layout-land'):
    p = f'{R}/{lay}/fragment_launcher.xml'
    rw(p, hide)
    def skins(s):
        s = s.replace('<com.kdt.mcgui.LauncherMenuButton android:tag="robin_content"', '<com.kdt.mcgui.LauncherMenuButton android:id="@+id/robin_content_btn" android:tag="robin_content"', 1)
        btn = ('            <com.kdt.mcgui.LauncherMenuButton android:tag="robin_skins" android:layout_width="fill_parent" android:text="@string/robin_skins_title" '
               'android:drawableStart="@drawable/ic_px_edit" app:layout_constraintTop_toBottomOf="@id/robin_content_btn" style="@style/LauncherMenuButton.Universal" />\n')
        i = s.index('        </androidx.constraintlayout.widget.ConstraintLayout>\n    </ScrollView>')
        return s[:i] + btn + s[i:]
    rw(p, skins)

# smali: Skins hook, no Horizon routing, folders, user agents
Rb = f'{d}/smali/com/robin/launcher/Robin.smali'
rw(Rb, lambda s: s.replace('.method public static hook(Landroid/view/View;)V\n    .locals 2\n', '.method public static hook(Landroid/view/View;)V\n    .locals 2\n\n    invoke-static {p0}, Lrl/Skins;->hook(Landroid/view/View;)V\n', 1))
rw(Rb, lambda s: s.replace('.method public static onPlay(Landroid/view/View;)Z\n    .locals 7\n', '.method public static onPlay(Landroid/view/View;)Z\n    .locals 7\n\n    const/4 v0, 0x0\n\n    return v0\n', 1))
for root, _, fs in os.walk(d):
    if '/smali' not in root + '/': continue
    for f in fs:
        if not f.endswith('.smali'): continue
        p = f'{root}/{f}'
        s = open(p, encoding='utf-8').read(); n = s
        for a, b in (('"games/RustLauncher"', '"games/SendLauncher"'), ('"games/PojavLauncher"', '"games/SendLauncher"'), ('"/games/PojavLauncher"', '"/games/SendLauncher"'),
                     ('"MojoLauncher ("', '"SendLauncher ("'), ('"MojoLauncher-Ping/1.0"', '"SendLauncher-Ping/1.0"'), ('"RustLauncher/1.0 (Android)"', '"SendLauncher/1.0 (Android)"'),
                     ('const-string v0, "RustLauncher"', 'const-string v0, "SendLauncher"'), ('"Rust Launcher"', '"Send Launcher"')):
            n = n.replace(a, b)
        if n != s: open(p, 'w', encoding='utf-8').write(n)

# ---------- palette: night teal ----------
pal = {'background_app': '#ff0b1416', 'background_bottom_bar': '#ff122124', 'background_overlay': '#ff1b3236',
       'background_status_bar': '#ff0b1416', 'ic_launcher_background': '#ff0b1416', 'minebutton_color': '#ff2ee6c5',
       'minebutton_secondary': '#ff1b3236', 'preference_fallback_accent_color': '#ff2ee6c5',
       'primary_text': '#ffeafffb', 'secondary_text': '#ffa6cfc8'}
def colors(s):
    for k, v in pal.items(): s = re.sub(rf'(<color name="{k}">)[^<]*', rf'\1{v}', s)
    return s
rw(f'{R}/values/colors.xml', colors)
def rr(fill, stroke):
    return f'''<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="{fill}"/><corners android:radius="10dp"/><stroke android:width="2dp" android:color="{stroke}"/>
</shape>'''
open(f'{R}/drawable/button_flat.xml', 'w').write(rr('#ff1b3236', '#ff25454a'))
open(f'{R}/drawable/button_flat_toggle_off.xml', 'w').write(rr('#ff1b3236', '#ff25454a'))
open(f'{R}/drawable/button_flat_toggle_on.xml', 'w').write(rr('#ff169b86', '#ff2ee6c5'))

# ---------- speed defaults for weak phones ----------
rw(f'{R}/xml/pref_video.xml', lambda s: re.sub(r'(android:key="sustainedPerformance"[^>]*android:defaultValue=")false', r'\1true', s))
rw(f'{R}/xml/pref_experimental.xml', lambda s: re.sub(r'(android:key="bigCoreAffinity"[^>]*android:defaultValue=")false', r'\1true', s))

# ---------- lighter: only English + Hindi ----------
for x in os.listdir(R):
    m = re.fullmatch(r'values-([a-z]{2,3})(-r[A-Z]{2})?', x)
    if m and m.group(1) not in ('en', 'hi'): shutil.rmtree(f'{R}/{x}')
y = open(f'{d}/apktool.yml').read()
y = re.sub(r"versionCode: '?\d+'?", "versionCode: '1'", y); y = re.sub(r"versionName: .*", "versionName: V1.0", y)
open(f'{d}/apktool.yml', 'w').write(y)

# ---------- icon: pixel paper plane ----------
PL = ["..............", "..........PP..", "........PPPP..", "......PPPPPP..", "....PPPPPPPP..", "..PPPPPPPPPP..", "PPPPPPPPPPP...", "..DDPPPPPP....",
      "....DDPPP.....", "......DPP.....", "......DDP.....", ".......DD.....", "..............", ".............."]
COL = {'P': '#2ee6c5', 'D': '#169b86'}
def glyph(size, pad, bg):
    im = Image.new('RGBA', (size, size), bg); dr = ImageDraw.Draw(im)
    cell = (size - 2 * pad) // 14; ox = (size - cell * 14) // 2
    for y, row in enumerate(PL):
        for x, c in enumerate(row):
            if c in COL: dr.rectangle([ox + x * cell, ox + y * cell, ox + (x + 1) * cell - 1, ox + (y + 1) * cell - 1], fill=COL[c])
    return im
for dens, sz in (('mdpi', 48), ('hdpi', 72), ('xhdpi', 96), ('xxhdpi', 144), ('xxxhdpi', 192)):
    full = glyph(sz, sz // 8, '#ff0b1416'); fg = glyph(sz * 108 // 48, sz * 108 // 48 * 3 // 10, (0, 0, 0, 0))
    for n, im in (('ic_launcher', full), ('ic_launcher_round', full), ('ic_launcher_foreground', fg)):
        p = f'{R}/mipmap-{dens}/{n}.webp'
        if os.path.exists(p): im.save(p, 'WEBP', lossless=True)
print('send tree ready')
