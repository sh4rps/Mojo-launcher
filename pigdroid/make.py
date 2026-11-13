#!/usr/bin/env python3
"""Pigdroid Launcher: usage make.py <apktool-decoded AllJava tree>  (own package, name, pink theme, icon, skins, games/PigdroidLauncher)"""
import sys, os, re
from PIL import Image, ImageDraw
d = sys.argv[1]; R = f'{d}/res'
def rw(p, f):
    s = open(p, encoding='utf-8').read(); n = f(s); open(p, 'w', encoding='utf-8').write(n)
PKG = 'com.pigdroid.launcher'

def man(s):
    s = re.sub(r'<activity[^>]*dev\.horizon\.android\.HorizonActivity".*?</activity>', '', s, flags=re.S)
    s = re.sub(r'<activity[^>]*com\.robin\.launcher\.Chooser".*?</activity>', '', s, flags=re.S)
    s = s.replace('android:name="git.artdeell.mojo.TestStorageActivity"/>',
      'android:name="git.artdeell.mojo.TestStorageActivity"><intent-filter><action android:name="android.intent.action.MAIN"/>'
      '<category android:name="android.intent.category.LAUNCHER"/></intent-filter></activity>', 1)
    assert 'category.LAUNCHER' in s
    s = s.replace('com.rustlauncher.app', PKG)
    s = s.replace('</application>', '<activity android:name="rl.Skins" android:theme="@android:style/Theme.Material.NoActionBar" android:exported="false" android:configChanges="orientation|screenSize|keyboardHidden"/></application>', 1)
    return s
rw(f'{d}/AndroidManifest.xml', man)
rw(f'{R}/values/strings.xml', lambda s: s.replace('com.rustlauncher.app', PKG))
rw(f'{d}/smali/git/artdeell/mojo/BuildConfig.smali', lambda s: s.replace('com.rustlauncher.app', PKG))
for f in (f'{d}/lib/arm64-v8a/libhorizon_android.so', f'{d}/assets/engine-data.zip'):
    if os.path.exists(f): os.remove(f)
for f in os.listdir(f'{d}/assets'):
    if f.startswith('horizon-'): os.remove(f'{d}/assets/{f}')

# name: nothing of the old names may remain in strings
for root, _, fs in os.walk(R):
    for f in fs:
        if f == 'strings.xml':
            rw(f'{root}/{f}', lambda s: s.replace('Rust Launcher', 'Pigdroid Launcher').replace('RustLauncher', 'Pigdroid Launcher').replace('Robin Launcher', 'Pigdroid Launcher'))
rw(f'{R}/values/strings.xml', lambda s: re.sub(r'(<string name="app_short_name">)[^<]*', r'\1Pigdroid Launcher', s).replace('</resources>', '    <string name="robin_skins_title">Skins &amp; Capes</string>\n</resources>'))

# pink-on-cocoa palette
pal = {'background_app': '#ff1a1114', 'background_bottom_bar': '#ff24181c', 'background_overlay': '#ff3a2630',
       'background_status_bar': '#ff1a1114', 'ic_launcher_background': '#fff6a9c1', 'minebutton_color': '#fff58bb0',
       'minebutton_secondary': '#ff3a2630', 'preference_fallback_accent_color': '#fff58bb0',
       'primary_text': '#fffff1f5', 'secondary_text': '#ffe3b9c8'}
def colors(s):
    for k, v in pal.items(): s = re.sub(rf'(<color name="{k}">)[^<]*', rf'\1{v}', s)
    return s
rw(f'{R}/values/colors.xml', colors)

# rounded flat buttons (different from any pixel-bevel look)
def rr(fill, stroke):
    return f'''<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="{fill}"/><corners android:radius="14dp"/><stroke android:width="2dp" android:color="{stroke}"/>
</shape>'''
open(f'{R}/drawable/button_flat.xml', 'w').write(rr('#ff3a2630', '#ff5b3a48'))
open(f'{R}/drawable/button_flat_toggle_off.xml', 'w').write(rr('#ff3a2630', '#ff5b3a48'))
open(f'{R}/drawable/button_flat_toggle_on.xml', 'w').write(rr('#ffd4668f', '#fff58bb0'))

# skins button + games folder + Horizon routing off
def lay(s):
    s = s.replace('<com.kdt.mcgui.LauncherMenuButton android:tag="robin_content"', '<com.kdt.mcgui.LauncherMenuButton android:id="@+id/robin_content_btn" android:tag="robin_content"', 1)
    btn = ('            <com.kdt.mcgui.LauncherMenuButton android:tag="robin_skins" android:layout_width="fill_parent" android:text="@string/robin_skins_title" '
           'android:drawableStart="@drawable/ic_px_edit" app:layout_constraintTop_toBottomOf="@id/robin_content_btn" style="@style/LauncherMenuButton.Universal" />\n')
    i = s.index('        </androidx.constraintlayout.widget.ConstraintLayout>\n    </ScrollView>')
    return s[:i] + btn + s[i:]
rw(f'{R}/layout/fragment_launcher.xml', lay)
Rb = f'{d}/smali/com/robin/launcher/Robin.smali'
rw(Rb, lambda s: s.replace('.method public static hook(Landroid/view/View;)V\n    .locals 2\n', '.method public static hook(Landroid/view/View;)V\n    .locals 2\n\n    invoke-static {p0}, Lrl/Skins;->hook(Landroid/view/View;)V\n', 1))
rw(Rb, lambda s: s.replace('.method public static onPlay(Landroid/view/View;)Z\n    .locals 7\n', '.method public static onPlay(Landroid/view/View;)Z\n    .locals 7\n\n    const/4 v0, 0x0\n\n    return v0\n', 1))
rw(f'{d}/smali/git/artdeell/mojo/Tools.smali', lambda s: s.replace('"games/RustLauncher"', '"games/PigdroidLauncher"'))
import shutil
for x in os.listdir(R):
    m = re.fullmatch(r'values-([a-z]{2,3})(-r[A-Z]{2})?', x)
    if m and m.group(1) not in ('en', 'hi'): shutil.rmtree(f'{R}/{x}')
y = open(f'{d}/apktool.yml').read()
y = re.sub(r"versionCode: '?\d+'?", "versionCode: '1'", y); y = re.sub(r"versionName: .*", "versionName: 1.0", y)
open(f'{d}/apktool.yml', 'w').write(y)

# icon: pixel pig face
PIG = ["..............", ".PP........PP.", ".PPPPPPPPPPPP.", "PPPPPPPPPPPPPP", "PPEEPPPPPPEEPP", "PPEEPPPPPPEEPP", "PPPPPPPPPPPPPP",
       "PPPPDDDDDDPPPP", "PPPPDKDDKDPPPP", "PPPPDDDDDDPPPP", "PPPPPPPPPPPPPP", ".PPPPPPPPPPPP.", "..............", ".............."]
COL = {'P': '#f6a9c1', 'E': '#2b1a21', 'D': '#e8789f', 'K': '#2b1a21'}
def glyph(size, pad, bg):
    im = Image.new('RGBA', (size, size), bg); dr = ImageDraw.Draw(im)
    cell = (size - 2 * pad) // 14; ox = (size - cell * 14) // 2
    for y, row in enumerate(PIG):
        for x, c in enumerate(row):
            if c in COL: dr.rectangle([ox + x * cell, ox + y * cell, ox + (x + 1) * cell - 1, ox + (y + 1) * cell - 1], fill=COL[c])
    return im
for dens, sz in (('mdpi', 48), ('hdpi', 72), ('xhdpi', 96), ('xxhdpi', 144), ('xxxhdpi', 192)):
    full = glyph(sz, sz // 8, '#ff3a2630'); fg = glyph(sz * 108 // 48, sz * 108 // 48 * 3 // 10, (0, 0, 0, 0))
    for n, im in (('ic_launcher', full), ('ic_launcher_round', full), ('ic_launcher_foreground', fg)):
        p = f'{R}/mipmap-{dens}/{n}.webp'
        if os.path.exists(p): im.save(p, 'WEBP', lossless=True)
print('pigdroid tree ready')
