#!/usr/bin/env python3
"""Aerix 2.3.2 mod: usage patch.py <apktool-decoded dir>
1. FPS / Hz overlay permanently hidden  2. game data in internal storage /games folder (All-files access)
3. extra 'Physical mouse sensitivity' slider in quick settings (rl.Phys)."""
import sys, re
d = sys.argv[1]; S = f'{d}/smali_classes3/net/kdt/pojavlaunch'
def rw(p, f):
    s = open(p, encoding='utf-8').read(); n = f(s)
    assert n != s, p
    open(p, 'w', encoding='utf-8').write(n)

# 1. FpsView never becomes visible
rw(f'{S}/game/FpsView.smali', lambda s: s.replace('.method protected onAttachedToWindow()V', '''.method public setVisibility(I)V
    .locals 1

    const/16 v0, 0x8

    invoke-super {p0, v0}, Landroid/view/View;->setVisibility(I)V

    return-void
.end method

.method protected onAttachedToWindow()V''', 1))

# 2. storage root -> /storage/emulated/0/games/AerixLauncher when All-files access is granted; ask once
def tools(s):
    s = s.replace('''.method private static getPojavStorageRoot(Landroid/content/Context;)Ljava/io/File;
    .locals 3
''', '''.method private static getPojavStorageRoot(Landroid/content/Context;)Ljava/io/File;
    .locals 3

    sget v0, Landroid/os/Build$VERSION;->SDK_INT:I

    const/16 v1, 0x1e

    if-lt v0, v1, :fa_orig

    invoke-static {}, Landroid/os/Environment;->isExternalStorageManager()Z

    move-result v0

    if-eqz v0, :fa_orig

    new-instance v0, Ljava/io/File;

    const-string v1, "/storage/emulated/0/games/AerixLauncher"

    invoke-direct {v0, v1}, Ljava/io/File;-><init>(Ljava/lang/String;)V

    invoke-virtual {v0}, Ljava/io/File;->mkdirs()Z

    return-object v0

    :fa_orig
''', 1)
    s = s.replace('''.method public static checkStorageInteractive(Landroid/app/Activity;)Z
    .locals 2
''', '''.method public static checkStorageInteractive(Landroid/app/Activity;)Z
    .locals 2

    :fa_try_start
    sget v0, Landroid/os/Build$VERSION;->SDK_INT:I

    const/16 v1, 0x1e

    if-lt v0, v1, :fa_skip

    invoke-static {}, Landroid/os/Environment;->isExternalStorageManager()Z

    move-result v0

    if-nez v0, :fa_skip

    sget-boolean v0, Lnet/kdt/pojavlaunch/Tools;->faAsked:Z

    if-nez v0, :fa_skip

    const/4 v0, 0x1

    sput-boolean v0, Lnet/kdt/pojavlaunch/Tools;->faAsked:Z

    new-instance v0, Landroid/content/Intent;

    const-string v1, "android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION"

    invoke-direct {v0, v1}, Landroid/content/Intent;-><init>(Ljava/lang/String;)V

    invoke-virtual {p0}, Landroid/app/Activity;->getPackageName()Ljava/lang/String;

    move-result-object v1

    const-string v2, "package:"

    invoke-virtual {v2, v1}, Ljava/lang/String;->concat(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v1

    invoke-static {v1}, Landroid/net/Uri;->parse(Ljava/lang/String;)Landroid/net/Uri;

    move-result-object v1

    invoke-virtual {v0, v1}, Landroid/content/Intent;->setData(Landroid/net/Uri;)Landroid/content/Intent;

    invoke-virtual {p0, v0}, Landroid/app/Activity;->startActivity(Landroid/content/Intent;)V

    :fa_try_end
    .catchall {:fa_try_start .. :fa_try_end} :fa_skip

    :fa_skip
''', 1)
    s = s.replace('.field public static DIR_GAME_HOME:Ljava/lang/String;', '.field public static faAsked:Z\n\n.field public static DIR_GAME_HOME:Ljava/lang/String;', 1)
    return s
rw(f'{S}/Tools.smali', tools)
rw(f'{d}/AndroidManifest.xml', lambda s: s.replace('<application ', '<uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE"/><application ', 1)
   if 'MANAGE_EXTERNAL_STORAGE' not in s else s.replace('x', 'x', 0) + ' ')

# 3. physical mouse sensitivity
rw(f'{S}/prefs/LauncherPreferences.smali', lambda s: re.sub(
    r'(\.method public static loadPreferences\(Landroid/content/Context;\)V\n    \.locals \d+\n)',
    r'\1\n    invoke-static {p0}, Lrl/Phys;->load(Landroid/content/Context;)V\n', s, 1))
def bind(s):
    m = re.search(r'\.method private bindLayout\(\)V.*?    return-void\n\.end method', s, re.S)
    blk = m.group(0)
    new = blk.replace('    return-void\n.end method', '''    iget-object v0, p0, Lnet/kdt/pojavlaunch/prefs/QuickSettingSideDialog;->mDialogContent:Landroid/view/View;

    invoke-static {v0}, Lrl/Phys;->attach(Landroid/view/View;)V

    return-void
.end method''')
    return s.replace(blk, new)
rw(f'{S}/prefs/QuickSettingSideDialog.smali', bind)
def cap(s):
    old = '    sget v0, Lnet/kdt/pojavlaunch/prefs/LauncherPreferences;->PREF_MOUSESPEED:F\n'
    old2 = '    sget v0, Lnet/kdt/pojavlaunch/prefs/LauncherPreferences;->PREF_GAME_MOUSE_SENSITIVITY:F\n'
    fix = '\n    invoke-static {v0}, Lrl/Phys;->scale(F)F\n\n    move-result v0\n'
    assert old in s and old2 in s
    return s.replace(old, old + fix, 1).replace(old2, old2 + fix, 1)
rw(f'{S}/customcontrols/mouse/AndroidPointerCapture.smali', cap)

# layout + string
def lay(s):
    s = s.replace('android:id="@id/editCursorSize_textView" android:layout_width="wrap_content" android:layout_height="wrap_content" android:layout_marginTop="@dimen/_5sdp" android:text="@string/quick_setting_cursor_size" android:paddingEnd="5.0dip" app:layout_constraintStart_toStartOf="parent" app:layout_constraintTop_toBottomOf="@id/editMouseSpeed_seekbar"',
                  'android:id="@id/editCursorSize_textView" android:layout_width="wrap_content" android:layout_height="wrap_content" android:layout_marginTop="@dimen/_5sdp" android:text="@string/quick_setting_cursor_size" android:paddingEnd="5.0dip" app:layout_constraintStart_toStartOf="parent" app:layout_constraintTop_toBottomOf="@+id/physMouse_seekbar"')
    ins = '''    <TextView android:gravity="center" android:id="@+id/physMouse_textView" android:layout_width="wrap_content" android:layout_height="wrap_content" android:layout_marginTop="@dimen/_5sdp" android:text="@string/quick_setting_phys_mouse" android:paddingEnd="5.0dip" app:layout_constraintStart_toStartOf="parent" app:layout_constraintTop_toBottomOf="@id/editMouseSpeed_seekbar" />
    <com.kdt.CustomSeekbar android:id="@+id/physMouse_seekbar" android:layout_width="0.0dip" android:layout_height="@dimen/_36sdp" android:max="500" android:min="10" app:layout_constraintEnd_toEndOf="parent" app:layout_constraintStart_toStartOf="parent" app:layout_constraintTop_toBottomOf="@id/physMouse_textView" app:seekBarIncrement="5" />
    <TextView android:gravity="end" android:id="@+id/physMouse_textView_percent" android:layout_width="150.0dip" android:layout_height="0.0dip" app:layout_constraintBottom_toBottomOf="@id/physMouse_textView" app:layout_constraintEnd_toEndOf="parent" app:layout_constraintTop_toTopOf="@id/physMouse_textView" />
'''
    k = '    <TextView android:gravity="center" android:id="@id/editCursorSize_textView"'
    assert k in s
    return s.replace(k, ins + k, 1)
rw(f'{d}/res/layout/dialog_quick_setting.xml', lay)
rw(f'{d}/res/values/strings.xml', lambda s: s.replace('</resources>', '    <string name="quick_setting_phys_mouse">Physical mouse sensitivity</string>\n</resources>'))
print('patched')
