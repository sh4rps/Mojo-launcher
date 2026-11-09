#!/usr/bin/env python3
"""RustLauncher + Starling Horizon 0.7.8: usage patch.py <decoded RustLauncher69 dir> <decoded starling dir>.
Swaps in the Starling engine/assets and puts a chooser (Horizon / Java Edition) first."""
import sys, shutil, os, re
ml, hd = sys.argv[1], sys.argv[2]
shutil.copy(f'{hd}/lib/arm64-v8a/libhorizon_android.so', f'{ml}/lib/arm64-v8a/libhorizon_android.so')
shutil.copy(f'{hd}/assets/engine-data.zip', f'{ml}/assets/engine-data.zip')
for a, b in (('LICENSE.txt', 'horizon-LICENSE.txt'), ('THIRD_PARTY_NOTICES.md', 'horizon-THIRD_PARTY_NOTICES.md')):
    if os.path.exists(f'{hd}/assets/{a}'): shutil.copy(f'{hd}/assets/{a}', f'{ml}/assets/{b}')
p = f'{ml}/AndroidManifest.xml'; s = open(p).read()
old = re.search(r'(<activity [^>]*git\.artdeell\.mojo\.TestStorageActivity">)\s*<intent-filter>.*?</intent-filter>\s*(</activity>)', s, re.S)
assert old, 'TestStorageActivity filter not found'
s = s[:old.start()] + old.group(1) + old.group(2) + s[old.end():]
chooser = ('<activity android:exported="true" android:label="@string/app_short_name" android:name="com.robin.launcher.Chooser" '
           'android:theme="@android:style/Theme.Material.NoActionBar" android:configChanges="orientation|screenSize|keyboardHidden">'
           '<intent-filter><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER"/></intent-filter></activity>')
s = s.replace('<activity android:label="@string/app_short_name" android:name="git.artdeell.mojo.LauncherActivity"', chooser + '<activity android:label="@string/app_short_name" android:name="git.artdeell.mojo.LauncherActivity"', 1)
assert 'com.robin.launcher.Chooser' in s
open(p, 'w').write(s)
print('patched')

# --- all Java runtimes (done by hand on the decoded tree; see java/rl/Jres.java) ---
# 1. assets/components/jre-17 and jre-21 (universal.tar.xz + bin-arm64.tar.xz + version) copied from the RustBridge APK;
#    jre/bin-{arm,x86,x86_64}.tar.xz dropped (arm64 only).
# 2. javac --release 8 java/rl/Jres.java -> dx -> classes4.dex, zipped into the built APK.
# 3. smali: AsyncAssetManager.unpackRuntime calls rl.Jres.ensureAsync(am) first;
#    NewJREUtil.installNewJreIfNeeded calls rl.Jres.ensure(am) first (blocking).
