#!/usr/bin/env python3
"""FeatherAndroid: usage reskin.py <apktool-decoded RustLauncherStarling-AllJava dir> <horizon-android clone dir>
Turns the Mojo-based Java launcher into 'FeatherAndroid' with a Horizon-Android style look (purple, pixel font, beveled blocks)."""
import sys, os, re, shutil
from PIL import Image, ImageDraw
d, hz = sys.argv[1], sys.argv[2]
R = f'{d}/res'
def rw(p, f):
    s = open(p, encoding='utf-8').read(); n = f(s); open(p, 'w', encoding='utf-8').write(n)

# ---- 1. manifest: Java launcher is the entry, Horizon engine removed, new package id ----
def man(s):
    s = re.sub(r'<activity[^>]*dev\.horizon\.android\.HorizonActivity".*?</activity>', '', s, flags=re.S)
    s = re.sub(r'<activity[^>]*com\.robin\.launcher\.Chooser".*?</activity>', '', s, flags=re.S)
    s = s.replace('android:name="git.artdeell.mojo.TestStorageActivity"/>',
      'android:name="git.artdeell.mojo.TestStorageActivity"><intent-filter><action android:name="android.intent.action.MAIN"/>'
      '<category android:name="android.intent.category.LAUNCHER"/></intent-filter></activity>', 1)
    assert 'category.LAUNCHER' in s
    return s.replace('com.rustlauncher.app', 'com.feather.android')
rw(f'{d}/AndroidManifest.xml', man)
rw(f'{R}/values/strings.xml', lambda s: s.replace('com.rustlauncher.app', 'com.feather.android'))
rw(f'{d}/smali/git/artdeell/mojo/BuildConfig.smali', lambda s: s.replace('com.rustlauncher.app', 'com.feather.android'))
for f in (f'{d}/lib/arm64-v8a/libhorizon_android.so', f'{d}/assets/engine-data.zip'):
    if os.path.exists(f): os.remove(f)
for f in os.listdir(f'{d}/assets'):
    if f.startswith('horizon-'): os.remove(f'{d}/assets/{f}')

# ---- 2. name ----
for root, _, fs in os.walk(R):
    for f in fs:
        if f == 'strings.xml':
            p = f'{root}/{f}'
            rw(p, lambda s: s.replace('Rust Launcher', 'FeatherAndroid').replace('RustLauncher', 'FeatherAndroid'))
rw(f'{R}/values/strings.xml', lambda s: re.sub(r'(<string name="app_short_name">)[^<]*', r'\1FeatherAndroid', s))

# ---- 3. Horizon palette (docs/android-appearance.md accent #9a86ff, night theme) ----
pal = {'background_app': '#ff0d0e13', 'background_bottom_bar': '#ff16181f', 'background_overlay': '#ff3b3354',
       'background_status_bar': '#ff0d0e13', 'ic_launcher_background': '#ff1b1530', 'minebutton_color': '#ff9a86ff',
       'minebutton_secondary': '#ff1b1530', 'preference_fallback_accent_color': '#ff9a86ff',
       'primary_text': '#ffecedf3', 'secondary_text': '#ffc5c7d1'}
def colors(s):
    for k, v in pal.items(): s = re.sub(rf'(<color name="{k}">)[^<]*', rf'\1{v}', s)
    return s
rw(f'{R}/values/colors.xml', colors)

# ---- 4. pixel font like Horizon ----
shutil.copy(f'{hz}/crates/horizon-theme/fonts/PixelifySans-Medium.ttf', f'{R}/font/minecraft_ten.ttf')
shutil.copy(f'{hz}/crates/horizon-theme/fonts/PixelifySans-Bold.ttf', f'{R}/font/noto_sans_bold.ttf')

# ---- 5. beveled Horizon blocks (outline 2dp, bevel, lip) ----
def block(face, hi, lip, outline='#ff0d0e13'):
    return f'''<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item><shape android:shape="rectangle"><solid android:color="{outline}"/></shape></item>
    <item android:left="2dp" android:top="2dp" android:right="2dp" android:bottom="2dp"><shape android:shape="rectangle"><solid android:color="{lip}"/></shape></item>
    <item android:left="2dp" android:top="2dp" android:right="2dp" android:bottom="4dp"><shape android:shape="rectangle"><solid android:color="{hi}"/></shape></item>
    <item android:left="4dp" android:top="4dp" android:right="2dp" android:bottom="4dp"><shape android:shape="rectangle"><solid android:color="{face}"/></shape></item>
</layer-list>'''
open(f'{R}/drawable/button_flat.xml', 'w').write(block('#ff3b3354', '#ff585071', '#ff1b1530'))
open(f'{R}/drawable/button_flat_toggle_off.xml', 'w').write(block('#ff3b3354', '#ff585071', '#ff1b1530'))
open(f'{R}/drawable/button_flat_toggle_on.xml', 'w').write(block('#ff5b3fd6', '#ff8069ef', '#ff3a2694'))
open(f'{R}/drawable/background_card.xml', 'w').write('''<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item><shape android:shape="rectangle"><solid android:color="#ff0d0e13"/></shape></item>
    <item android:left="2dp" android:top="2dp" android:right="2dp" android:bottom="2dp"><shape android:shape="rectangle"><solid android:color="#ff16181f"/></shape></item>
</layer-list>''')

# ---- 6. icon: purple pixel feather on dark plate ----
PX = [
 "..........XXX.", ".........XXXXX", "........XXXXXX", ".......XXXXXXX", "......XXXXXXX.", ".....XXXXXXX..",
 "....XXXXXXX...", "...XXXXXXX....", "..XXXXXXX.....", ".XXXXXX.......", "XXXXX.........", "XXX...........",
]
def glyph(size, pad, bg):
    im = Image.new('RGBA', (size, size), bg); dr = ImageDraw.Draw(im)
    n = 14; cell = (size - 2 * pad) // n; ox = (size - cell * n) // 2
    for y, row in enumerate(PX):
        for x, c in enumerate(row):
            if c == 'X':
                X0, Y0 = ox + x * cell, ox + (y + 1) * cell
                dr.rectangle([X0, Y0, X0 + cell - 1, Y0 + cell - 1], fill='#9a86ff')
                dr.rectangle([X0, Y0, X0 + cell - 1, Y0 + max(1, cell // 4)], fill='#c9bfff')
                dr.rectangle([X0, Y0 + cell - max(1, cell // 4), X0 + cell - 1, Y0 + cell - 1], fill='#5b3fd6')
    # quill line
    for i in range(3):
        dr.rectangle([ox + i * cell, ox + (12 + i) * cell, ox + (i + 1) * cell - 1, ox + (13 + i) * cell - 1], fill='#ecedf3')
    return im
for dens, sz in (('mdpi', 48), ('hdpi', 72), ('xhdpi', 96), ('xxhdpi', 144), ('xxxhdpi', 192)):
    full = glyph(sz, sz // 6, '#ff1b1530')
    fg = glyph(sz * 108 // 48, sz * 108 // 48 * 3 // 10, (0, 0, 0, 0))
    for n, im in (('ic_launcher', full), ('ic_launcher_round', full), ('ic_launcher_foreground', fg)):
        p = f'{R}/mipmap-{dens}/{n}.webp'
        if os.path.exists(p): im.save(p, 'WEBP', lossless=True)
print('reskin done')
