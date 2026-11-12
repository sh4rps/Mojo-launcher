#!/usr/bin/env python3
"""FeatherAndroid 2.0 upgrade on top of the reskinned tree: usage upgrade.py <decoded dir>
 - Skins & Capes screen (3D preview) wired into the main menu (code: java/rl/*, dex added after build)
 - games folder renamed games/FeatherAndroid
 - Horizon routing in Play disabled (Horizon engine was removed)
 - lighter: unused translations dropped"""
import sys, re, os, shutil
d = sys.argv[1]
def rw(p, f):
    s = open(p, encoding='utf-8').read(); n = f(s)
    assert n != s, p
    open(p, 'w', encoding='utf-8').write(n)

rw(f'{d}/AndroidManifest.xml', lambda s: s.replace('<application ', '<application ', 1).replace('</application>', '<activity android:name="rl.Skins" android:theme="@android:style/Theme.Material.NoActionBar" android:exported="false" android:configChanges="orientation|screenSize|keyboardHidden"/></application>', 1))
rw(f'{d}/res/values/strings.xml', lambda s: s.replace('</resources>', '    <string name="robin_skins_title">Skins &amp; Capes</string>\n</resources>'))
def lay(s):
    s = s.replace('<com.kdt.mcgui.LauncherMenuButton android:tag="robin_content"', '<com.kdt.mcgui.LauncherMenuButton android:id="@+id/robin_content_btn" android:tag="robin_content"', 1)
    btn = ('            <com.kdt.mcgui.LauncherMenuButton android:tag="robin_skins" android:layout_width="fill_parent" android:text="@string/robin_skins_title" '
           'android:drawableStart="@drawable/ic_px_edit" app:layout_constraintTop_toBottomOf="@id/robin_content_btn" style="@style/LauncherMenuButton.Universal" />\n')
    i = s.index('        </androidx.constraintlayout.widget.ConstraintLayout>\n    </ScrollView>')
    return s[:i] + btn + s[i:]
rw(f'{d}/res/layout/fragment_launcher.xml', lay)

R = f'{d}/smali/com/robin/launcher/Robin.smali'
rw(R, lambda s: s.replace('.method public static hook(Landroid/view/View;)V\n    .locals 2\n',
    '.method public static hook(Landroid/view/View;)V\n    .locals 2\n\n    invoke-static {p0}, Lrl/Skins;->hook(Landroid/view/View;)V\n', 1))
rw(R, lambda s: s.replace('.method public static onPlay(Landroid/view/View;)Z\n    .locals 7\n',
    '.method public static onPlay(Landroid/view/View;)Z\n    .locals 7\n\n    const/4 v0, 0x0\n\n    return v0\n', 1))
rw(f'{d}/smali/git/artdeell/mojo/Tools.smali', lambda s: s.replace('"games/RustLauncher"', '"games/FeatherAndroid"'))

n = 0
for x in os.listdir(f'{d}/res'):
    m = re.fullmatch(r'values-([a-z]{2,3})(-r[A-Z]{2})?', x)
    if m and m.group(1) not in ('en', 'hi'):
        shutil.rmtree(f'{d}/res/{x}'); n += 1
print('dropped translations:', n)
y = open(f'{d}/apktool.yml').read()
y = re.sub(r"versionCode: '?\d+'?", "versionCode: '2000'", y); y = re.sub(r"versionName: .*", "versionName: 2.0", y)
open(f'{d}/apktool.yml', 'w').write(y)
print('upgrade done')
