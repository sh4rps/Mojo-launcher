#!/usr/bin/env python3
"""Aerix Launcher 2.3.2 edit. usage: patch.py <apktool-decoded dir>
1. in-game FPS / Hz readout (top-left) removed for good  2. 'Contributors' section removed from the Info page  3. package renamed to net.al.aerixlauncher.edit so it installs next to the original (no signature clash)."""
import sys
d = sys.argv[1]; S = f'{d}/smali_classes3/net/kdt/pojavlaunch'
def rw(p, f):
    s = open(p, encoding='utf-8').read(); n = f(s)
    assert n != s, p
    open(p, 'w', encoding='utf-8').write(n)

# 1. FpsView can never leave GONE -> readout never drawn, frame sampler never started
rw(f'{S}/game/FpsView.smali', lambda s: s.replace('.method protected onAttachedToWindow()V', '''.method public setVisibility(I)V
    .locals 1

    const/16 v0, 0x8

    invoke-super {p0, v0}, Landroid/view/View;->setVisibility(I)V

    return-void
.end method

.method protected onAttachedToWindow()V''', 1))

# 2. Contributors section on the Info page
call = '    invoke-static {v0, v8, v2}, Lnet/kdt/pojavlaunch/mobileglues/ui/material/MaterialContributorsKt;->MaterialContributorsSection(Lnet/kdt/pojavlaunch/mobileglues/ui/AppController;Landroidx/compose/runtime/Composer;I)V\n'
rw(f'{S}/mobileglues/ui/material/MaterialInfoKt.smali', lambda s: s.replace(call, '', 1))

# 3. package rename (manifest, authorities/permission, strings, hard-coded strings in smali)
import os
OLD, NEW = 'net.al.aerixlauncher', 'net.al.aerixlauncher.edit'
n = 0
for root, _, fs in os.walk(d):
    if '/original' in root or '/lib' in root or '/assets' in root: continue
    for f in fs:
        if not f.endswith(('.smali', '.xml')) or (f.endswith('.xml') and '/res/' in root and not root.endswith('/res/values')): continue
        p = os.path.join(root, f); t = open(p, encoding='utf-8').read()
        if OLD in t:
            open(p, 'w', encoding='utf-8').write(t.replace(OLD, NEW)); n += 1
assert n >= 5, n
rw(f'{d}/res/values/strings.xml', lambda t: t.replace('<string name="aerix_app_label">Aerix Launcher</string>', '<string name="aerix_app_label">Aerix Launcher Edit</string>'))
print('patched, package files:', n)
