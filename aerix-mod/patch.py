#!/usr/bin/env python3
"""Aerix Launcher 2.3.2 edit. usage: patch.py <apktool-decoded dir>
1. in-game FPS / Hz readout (top-left) removed for good  2. 'Contributors' section removed from the Info page."""
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
print('patched')
