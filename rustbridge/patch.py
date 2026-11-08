#!/usr/bin/env python3
"""RustBridge: patches a decoded DroidBridge OFFLINE-FORK 0.10.21 (apktool dir). Usage: patch.py DECODED_DIR"""
import sys,re,glob,os,colorsys
import numpy as np
from PIL import Image
D=sys.argv[1]
def rd(p): return open(os.path.join(D,p),encoding='utf8').read()
def wr(p,s): open(os.path.join(D,p),'w',encoding='utf8').write(s)

# 1. visible name (strings only; style/id names stay)
for p in glob.glob(D+'/res/values*/strings.xml'):
    s=open(p,encoding='utf8').read(); n=s.replace('DroidBridge','RustBridgeLauncher')
    if n!=s: open(p,'w',encoding='utf8').write(n)

# 2. red theme: rotate warm hues of the launcher palette to red
skip=('blue','indigo','green_primary','purple','pink','violet','yellow','mono','rainbow','red','error')
def shift(h):
    hx=h[-6:]; a=h[:-6]
    r,g,b=[int(hx[i:i+2],16)/255 for i in(0,2,4)]
    H,L,S=colorsys.rgb_to_hls(r,g,b)
    if not 8<=H*360<=50: return None
    H2=(2 if S>0.5 else 355)/360
    if S>0.5: S=min(1,S*1.05)
    r,g,b=colorsys.hls_to_rgb(H2,L,S)
    return a+''.join('%02x'%round(x*255) for x in(r,g,b))
for p in ['res/values/colors.xml','res/values-night/colors.xml']:
    def f(m):
        n=m.group(1)
        if not n.startswith('launcher_') or any(k in n for k in skip): return m.group(0)
        v=shift(m.group(2))
        return m.group(0) if v is None else '<color name="%s">%s</color>'%(n,v)
    wr(p,re.sub(r'<color name="([^"]+)">(#[0-9a-fA-F]{8})</color>',f,rd(p)))
wr('res/drawable/ic_launcher_background.xml',rd('res/drawable/ic_launcher_background.xml').replace('#ff102116','#ff160a0a'))

# 3. red icon
for p in glob.glob(D+'/res/mipmap-*/ic_launcher*.webp')+glob.glob(D+'/res/drawable*/ic_launcher*.webp'):
    a=np.array(Image.open(p).convert('RGBA')).astype(float)/255
    rgb=a[...,:3]; mx=rgb.max(-1); mn=rgb.min(-1); d=mx-mn
    r,g,b=rgb[...,0],rgb[...,1],rgb[...,2]
    h=np.zeros_like(mx); m=d>1e-6
    rm=m&(mx==r); gm=m&(mx==g)&~rm; bm=m&~rm&~gm
    h[rm]=((g-b)[rm]/d[rm])%6; h[gm]=((b-r)[gm]/d[gm])+2; h[bm]=((r-g)[bm]/d[bm])+4
    h*=60
    sel=m&(d>0.15)&(h>8)&(h<55)
    s=np.where(mx>0,d/np.maximum(mx,1e-6),0)
    nh=np.where(sel,2.0,h)/60
    i=np.floor(nh).astype(int)%6; fr=nh-np.floor(nh)
    v=mx; pp=v*(1-s); q=v*(1-fr*s); t=v*(1-(1-fr)*s)
    out=np.zeros_like(rgb)
    for k,(x,y,z) in enumerate([(v,t,pp),(q,v,pp),(pp,v,t),(pp,q,v),(t,pp,v),(v,pp,q)]):
        mk=(i==k)&sel
        out[...,0][mk]=x[mk]; out[...,1][mk]=y[mk]; out[...,2][mk]=z[mk]
    a[...,:3]=np.where(sel[...,None],out,rgb)
    Image.fromarray((a*255).round().astype('uint8'),'RGBA').save(p,lossless=True)

# 4. games folder: base dir -> rb.Store.dir(ctx)
p='smali/a1/b.smali'; s=rd(p)
m=re.search(r'\.method public static c\(Landroid/content/Context;\)Ljava/io/File;.*?\.end method',s,re.S)
assert m
s=s.replace(m.group(0),'''.method public static c(Landroid/content/Context;)Ljava/io/File;
    .locals 1

    invoke-static {p0}, Lrb/Store;->dir(Landroid/content/Context;)Ljava/io/File;

    move-result-object v0

    return-object v0
.end method''')
wr(p,s)
p='smali/com/pairip/application/Application.smali'; s=rd(p)
old='    invoke-super {p0, p1}, Lcom/pairip/application/Application;->attachBaseContext(Landroid/content/Context;)V\n'
assert old in s
wr(p,s.replace(old,old+'\n    invoke-static {p0}, Lrb/Store;->init(Landroid/app/Application;)V\n',1))
p='AndroidManifest.xml'; s=rd(p)
s=s.replace('<application ','<uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE"/>\n    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"/>\n    <application ',1)
s=re.sub(r'<uses-permission android:maxSdkVersion="28" android:name="android.permission.WRITE_EXTERNAL_STORAGE"/>','',s)
wr(p,s)

# 5. size trim (keeps all four Java runtimes + LWJGL 3.3.3): drops LWJGL 3.3.3-bta, 3.4.1, WebRTC bridge and optional mod-compat libs
import shutil
u=os.path.join(D,'smali/ca/dnamobile/droidbridgelauncher/feature/unpack/Components.smali'); s=open(u).read()
old='    filled-new-array/range {v0 .. v7}, [Lca/dnamobile/droidbridgelauncher/feature/unpack/Components;'
assert old in s
s=s.replace(old,'    move-object v4, v6\n\n    filled-new-array/range {v0 .. v4}, [Lca/dnamobile/droidbridgelauncher/feature/unpack/Components;',1); open(u,'w').write(s)
for c in ('lwjgl3.3.3-bta','lwjgl3.4.1','webrtc_bridge'): shutil.rmtree(os.path.join(D,'assets/components',c),ignore_errors=True)
for f in 'sherpa-onnx-jni jingle_peerconnection_so netty_quiche_retro dev_xhyrom_e4mc_shadow_netty_quiche imgui-java imgui-javaarm64 OSMesa_2121 OSMesa_2300d sable_rapier sable_rapier_v2 shaderc_shared spirv-cross-c-shared shaderconv'.split():
    try: os.remove(os.path.join(D,'lib/arm64-v8a/lib%s.so'%f))
    except FileNotFoundError: pass
print('patched')
