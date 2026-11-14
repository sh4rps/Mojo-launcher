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
NOSKINS = os.environ.get('NOSKINS') == '1'
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
    if not NOSKINS: s = s.replace('</application>', '<activity android:name="rl.Skins" android:screenOrientation="sensorLandscape" android:theme="@android:style/Theme.Material.NoActionBar" android:exported="false" android:configChanges="orientation|screenSize|keyboardHidden"/></application>', 1)
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
for lay in ('layout',):
    p = f'{R}/{lay}/fragment_launcher.xml'
    rw(p, hide)
    def skins(s):
        s = s.replace('<com.kdt.mcgui.LauncherMenuButton android:tag="robin_content"', '<com.kdt.mcgui.LauncherMenuButton android:id="@+id/robin_content_btn" android:tag="robin_content"', 1)
        btn = ('            <com.kdt.mcgui.LauncherMenuButton android:tag="robin_skins" android:layout_width="fill_parent" android:text="@string/robin_skins_title" '
               'android:drawableStart="@drawable/ic_px_edit" app:layout_constraintTop_toBottomOf="@id/robin_content_btn" style="@style/LauncherMenuButton.Universal" />\n')
        i = s.index('        </androidx.constraintlayout.widget.ConstraintLayout>\n    </ScrollView>')
        return s[:i] + btn + s[i:]
    if not NOSKINS: rw(p, skins)

# smali: Skins hook, no Horizon routing, folders, user agents
Rb = f'{d}/smali/com/robin/launcher/Robin.smali'
if not NOSKINS: rw(Rb, lambda s: s.replace('.method public static hook(Landroid/view/View;)V\n    .locals 2\n', '.method public static hook(Landroid/view/View;)V\n    .locals 2\n\n    invoke-static {p0}, Lrl/Skins;->hook(Landroid/view/View;)V\n', 1))
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

# ---------- slim: Java 8 stays bundled, Java 17/21 are downloaded on first use (like the original Mojo) ----------
for jr in ('jre-17', 'jre-21'):
    shutil.rmtree(f'{d}/assets/components/{jr}', ignore_errors=True)
for rel, call in (('smali/git/artdeell/mojo/tasks/AsyncAssetManager.smali', '    invoke-static {p0}, Lrl/Jres;->ensureAsync(Landroid/content/res/AssetManager;)V\n'),
                  ('smali/git/artdeell/mojo/NewJREUtil.smali', '    invoke-static {p0}, Lrl/Jres;->ensure(Landroid/content/res/AssetManager;)V\n')):
    rw(f'{d}/{rel}', lambda t, c=call: t.replace(c, ''))

# ---------- palette: night teal ----------
pal = {'background_app': '#ff0e0b1f', 'background_bottom_bar': '#ff151029', 'background_overlay': '#ff2a1f4d',
       'background_status_bar': '#ff0e0b1f', 'ic_launcher_background': '#ff0e0b1f', 'minebutton_color': '#ff8b5cf6',
       'minebutton_secondary': '#ff2a1f4d', 'preference_fallback_accent_color': '#ff8b5cf6',
       'primary_text': '#fff5f3ff', 'secondary_text': '#ffc4b5fd'}
def colors(s):
    for k, v in pal.items(): s = re.sub(rf'(<color name="{k}">)[^<]*', rf'\1{v}', s)
    return s
rw(f'{R}/values/colors.xml', colors)
def rr(fill, stroke):
    return f'''<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="{fill}"/><corners android:radius="10dp"/><stroke android:width="2dp" android:color="{stroke}"/>
</shape>'''
open(f'{R}/drawable/button_flat.xml', 'w').write(rr('#ff2a1f4d', '#ff3b2a73'))
open(f'{R}/drawable/button_flat_toggle_off.xml', 'w').write(rr('#ff2a1f4d', '#ff3b2a73'))
open(f'{R}/drawable/button_flat_toggle_on.xml', 'w').write(rr('#ff6d28d9', '#ff8b5cf6'))

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

# ---------- icon: purple isometric cube ----------
def glyph(size, pad, bg):
    im = Image.new('RGBA', (size, size), bg); dr = ImageDraw.Draw(im)
    s = size - 2 * pad; ox = pad; oy = pad
    def P(x, y): return (ox + x * s, oy + y * s)
    top = [P(.5, .06), P(.92, .28), P(.5, .5), P(.08, .28)]
    left = [P(.08, .28), P(.5, .5), P(.5, .94), P(.08, .72)]
    right = [P(.92, .28), P(.5, .5), P(.5, .94), P(.92, .72)]
    dr.polygon(top, fill='#c4b5fd'); dr.polygon(left, fill='#7c3aed'); dr.polygon(right, fill='#5b21b6')
    for poly in (top, left, right): dr.line(poly + [poly[0]], fill='#ede9fe', width=max(1, size // 48))
    return im
for dens, sz in (('mdpi', 48), ('hdpi', 72), ('xhdpi', 96), ('xxhdpi', 144), ('xxxhdpi', 192)):
    full = glyph(sz, sz // 6, '#ff0e0b1f'); fg = glyph(sz * 108 // 48, sz * 108 // 48 * 3 // 10, (0, 0, 0, 0))
    for n, im in (('ic_launcher', full), ('ic_launcher_round', full), ('ic_launcher_foreground', fg)):
        p = f'{R}/mipmap-{dens}/{n}.webp'
        if os.path.exists(p): im.save(p, 'WEBP', lossless=True)

# ---------- new home layout (landscape) ----------
HERE = os.path.dirname(os.path.abspath(__file__))
if not NOSKINS:
    pass
shutil.copy(f'{HERE}/res/fragment_launcher_land.xml', f'{R}/layout-land/fragment_launcher.xml')
if NOSKINS:
    rw(f'{R}/layout-land/fragment_launcher.xml', lambda s: s.replace('android:tag="robin_skins"', ''))
def shape(body): return '<?xml version="1.0" encoding="utf-8"?>\n' + body
open(f'{R}/drawable/send_bg.xml', 'w').write(shape('<shape xmlns:android="http://schemas.android.com/apk/res/android"><gradient android:angle="270" android:startColor="#ff1d1140" android:endColor="#ff0b0818"/></shape>'))
open(f'{R}/drawable/send_side_bg.xml', 'w').write(shape('<shape xmlns:android="http://schemas.android.com/apk/res/android"><solid android:color="#ee120d26"/></shape>'))
open(f'{R}/drawable/send_card.xml', 'w').write(shape('<shape xmlns:android="http://schemas.android.com/apk/res/android"><solid android:color="#ee1b1436"/><corners android:radius="14dp"/><stroke android:width="1.5dp" android:color="#ff3b2a73"/></shape>'))
open(f'{R}/drawable/send_play.xml', 'w').write(shape('<shape xmlns:android="http://schemas.android.com/apk/res/android"><gradient android:angle="0" android:startColor="#ff6d28d9" android:endColor="#ffa855f7"/><corners android:radius="16dp"/><stroke android:width="2dp" android:color="#ffc4b5fd"/></shape>'))
open(f'{R}/drawable/send_pill.xml', 'w').write(shape('<selector xmlns:android="http://schemas.android.com/apk/res/android"><item android:state_pressed="true"><shape><solid android:color="#ff2a1f4d"/><corners android:radius="12dp"/></shape></item><item><shape><solid android:color="#00000000"/><corners android:radius="12dp"/></shape></item></selector>'))
rw(f'{R}/layout-land/fragment_launcher.xml', lambda s: s.replace('android:text="@string/main_play" android:textAllCaps="true" android:textSize="20sp"', 'android:text="@string/main_play" android:background="@drawable/send_play" android:textColor="#ffffffff" android:textAllCaps="true" android:textSize="20sp"'))
rw(f'{R}/values/styles.xml', lambda s: s.replace('</resources>', '    <style name="SendMenuButton" parent="@style/LauncherMenuButton">\n        <item name="android:layout_height">48dp</item>\n        <item name="android:background">@drawable/send_pill</item>\n        <item name="android:textColor">@color/primary_text</item>\n        <item name="android:textSize">12sp</item>\n        <item name="android:layout_marginBottom">4dp</item>\n    </style>\n</resources>'))
print('send tree ready')
