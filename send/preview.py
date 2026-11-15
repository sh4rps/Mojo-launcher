#!/usr/bin/env python3
"""Dev tool: render send/res/fragment_launcher_land.xml (+ top bar) to a PNG via headless Chromium, and report overflow.
usage: preview.py <art-res-dir (drawable-nodpi)> <art-assets-dir> <out.png> [W_dp H_dp]
Approximation only (flexbox instead of Android measure), but good enough to catch clipped / overflowing / overlapping widgets."""
import sys, os, re, json, base64
import xml.etree.ElementTree as ET
from playwright.sync_api import sync_playwright

HERE = os.path.dirname(os.path.abspath(__file__))
ND, AS, OUT = sys.argv[1], sys.argv[2], sys.argv[3]
W = float(sys.argv[4]) if len(sys.argv) > 4 else 851
H = float(sys.argv[5]) if len(sys.argv) > 5 else 392
A = '{http://schemas.android.com/apk/res/android}'
BG = {  # drawable backgrounds, from make.py
 'send_side_bg': 'background:#120d26f0;',
 'send_card': 'background:#1b1436e6;border:1.5px solid #3f2d7e;border-radius:14px;',
 'send_inner': 'background:#120b2b;border:1.2px solid #3b2a73;border-radius:10px;',
 'send_pill': 'background:transparent;border-radius:12px;',
 'send_nav_on': 'background:linear-gradient(#7c3aed,#5b21b6);border:1.5px solid #a78bfa;border-radius:13px;',
 'send_chip': 'background:#3b1f86;border:1.5px solid #8b5cf6;border-radius:10px;',
 'send_btn2': 'background:#2a1f4d;border:1.5px solid #8b5cf6;border-radius:11px;',
}
def col(c):
    c = c.lstrip('#')
    if len(c) == 8: a = int(c[:2], 16) / 255; return f'rgba({int(c[2:4],16)},{int(c[4:6],16)},{int(c[6:8],16)},{a:.2f})'
    return '#' + c
def dp(v):
    if v is None: return None
    m = re.match(r'(-?[\d.]+)(dp|sp|dip)?$', v)
    return float(m.group(1)) if m else None
def img(name):
    p = f'{ND}/{name}.png'
    if not os.path.exists(p): return ''
    return 'data:image/png;base64,' + base64.b64encode(open(p, 'rb').read()).decode()

def gravity_css(g, horiz):
    g = g or ''
    css = ''
    if horiz:
        if 'center_vertical' in g or g == 'center': css += 'align-items:center;'
        if 'center_horizontal' in g or g == 'center': css += 'justify-content:center;'
    else:
        if 'center_vertical' in g or g == 'center': css += 'justify-content:center;'
        if 'center_horizontal' in g or g == 'center': css += 'align-items:center;'
    return css

def node(e, parent_orient=None, root=False):
    tag = e.tag.split('.')[-1]
    a = lambda k: e.get(A + k)
    css = ['box-sizing:border-box;position:relative;min-width:0;min-height:0;']
    lw, lh, wt = a('layout_width'), a('layout_height'), a('layout_weight')
    horiz = parent_orient == 'h'
    def size(v, main):
        if v in ('fill_parent', 'match_parent'): return ('flex:1 1 0;' if main else 'align-self:stretch;') if parent_orient else ('width:100%;' if main else 'height:100%;')
        if v in (None, 'wrap_content'): return ''
        d = dp(v)
        return '' if d is None else f'{"width" if main==horiz else "height"}:{d}px;flex-shrink:0;'
    if root: lw = lh = None; parent_orient = 'h'; horiz = True; css.append('flex:1;align-self:stretch;')
    # width
    for v, isw in ((lw, True), (lh, False)):
        main = (isw == horiz) if parent_orient else isw
        if wt and v == '0dp' and main: css.append(f'flex:{wt} 1 0;')
        elif v in ('fill_parent', 'match_parent'):
            css.append(('flex:1 1 0;' if main else 'align-self:stretch;') if parent_orient else ('width:100%;' if isw else 'height:100%;'))
        elif v not in (None, 'wrap_content'):
            d = dp(v)
            if d is not None: css.append(f'{"width" if isw else "height"}:{d}px;' + ('flex-shrink:0;' if parent_orient else ''))
    for k, p in ((('layout_margin', 'margin') if not root else ('x', 'x')), ('padding', 'padding')):
        v = dp(a(k))
        if v is not None: css.append(f'{p}:{v}px;')
        for side, n in (('Start', 'left'), ('End', 'right'), ('Top', 'top'), ('Bottom', 'bottom')):
            v = dp(a(k + side))
            if v is not None: css.append(f'{p}-{n}:{v}px;')
    if a('layout_gravity') and 'center_horizontal' in a('layout_gravity'): css.append('align-self:center;')
    bg = a('background')
    if bg:
        if bg.startswith('@drawable/'): css.append(BG.get(bg[10:], ''))
        elif bg.startswith('#'): css.append(f'background:{col(bg)};')
        elif 'transparent' in bg: css.append('background:transparent;')
    inner = ''
    cls = tag
    if tag in ('LinearLayout',):
        o = 'h' if a('orientation') == 'horizontal' else 'v'
        css.append(f'display:flex;flex-direction:{"row" if o=="h" else "column"};' + gravity_css(a('gravity'), o == 'h'))
        inner = ''.join(node(c, o) for c in e)
    elif tag in ('FrameLayout',):
        css.append('display:grid;')
        inner = ''.join(node(c, None).replace('style="', 'style="grid-area:1/1;', 1) for c in e)
    elif tag == 'ScrollView':
        css.append('overflow-y:auto;overflow-x:hidden;display:flex;flex-direction:column;')
        inner = ''.join(node(c, 'v').replace('style="', 'style="flex-shrink:0;min-height:100%;' if a('fillViewport') == 'true' else 'style="', 1) for c in e)
        cls += ' scroll'
    elif tag == 'TextView':
        css.append('display:flex;')
        css.append(f'font-size:{dp(a("textSize")) or 14}px;color:{col(a("textColor") or "#fff")};line-height:1.15;')
        if a('textStyle') == 'bold': css.append('font-weight:700;')
        if a('maxLines') == '1': css.append('white-space:nowrap;overflow:hidden;text-overflow:ellipsis;')
        css.append(gravity_css(a('gravity'), True) + ('justify-content:center;align-items:center;' if a('gravity') == 'center' else ''))
        if a('shadowRadius'): css.append('text-shadow:1px 2px 4px #000a;')
        t = (a('text') or '').replace('@string/', '')
        inner = f'<span>{t}</span>'
    elif tag in ('ImageView', 'ImageButton'):
        src = a('src') or ''
        if src.startswith('@drawable/'):
            u = img(src[10:])
            if u: inner = f'<img src="{u}" style="width:100%;height:100%;object-fit:contain;image-rendering:auto">'
            elif src[10:] == 'ic_px_edit': inner = '<span style="color:#fff">✎</span>'
        pad = dp(a('padding'))
        if pad: css.append(f'padding:{pad}px;')
    elif tag == 'mcVersionSpinner':
        css.append('display:flex;align-items:center;color:#f5f3ff;font-weight:700;font-size:15px;padding-left:6px;')
        inner = '<img src="%s" style="width:26px;height:26px;image-rendering:pixelated;margin-right:8px"><span style="flex:1">1.21.4</span><span style="color:#c4b5fd">⌄</span>' % img('send_grass')
    elif tag == 'MineButton':
        css.append('background:linear-gradient(90deg,#6d28d9,#a855f7);border:2px solid #c4b5fd;border-radius:14px;')
    elif tag == 'AccountSpinner':
        css.append('display:flex;align-items:center;color:#fff;font-size:12px;padding:0 8px;background:#1b1436e6;border:1px solid #3f2d7e;border-radius:8px;')
        inner = 'Player ⌄'
    elif tag == 'LauncherMenuButton':
        css.append('display:none;')
    elif tag in ('HomeStage', 'ForestView', 'Guideline'):
        css.append('display:none;')
    elif tag == 'View':
        pass
    if a('visibility') == 'gone': css.append('display:none;')
    return f'<div class="{cls}" data-id="{(a("id") or "").split("/")[-1]}" style="{"".join(css)}">{inner}</div>'

frag = ET.parse(f'{HERE}/res/fragment_launcher_land.xml').getroot()
act = ET.parse(f'{HERE}/res/activity_mojo_ui_land.xml').getroot()
by = {}
for c in frag:
    i = c.get(A + 'id'); by[(i or '').split('/')[-1] or c.tag.split('.')[-1]] = c
def html_of(key): return node(by[key], None, True)

bx = by['send_bottom']; rx = by['send_right']
SB = dp(by['send_sidebar'].get(A + 'layout_width')); BOT_H = dp(bx.get(A + 'layout_height')); BOT_M = dp(bx.get(A + 'layout_marginBottom'))
R_TOP = dp(rx.get(A + 'layout_marginTop')); R_BOT = dp(rx.get(A + 'layout_marginBottom'))
bottom_top = H - BOT_M - BOT_H
html = f'''<!doctype html><meta charset=utf-8><style>
*{{margin:0;padding:0}} body{{width:{W}px;height:{H}px;background:#0e0b1f;font-family:Roboto,"Noto Sans","DejaVu Sans",sans-serif;overflow:hidden}}
#r div.w{{display:flex}} #r{{position:relative;width:{W}px;height:{H}px;overflow:hidden}}
.abs{{position:absolute}}
.scroll::-webkit-scrollbar{{display:none}}
</style><div id=r>
<div class=abs style="inset:0;background:url({img('send_forest')}) center/cover;image-rendering:pixelated"></div>
<canvas id=stage class=abs width={int((W*0.64-SB)*2)} height={int(bottom_top*2)} style="left:{SB}px;top:0;width:{W*0.64-SB}px;height:{bottom_top}px"></canvas>
<div class="abs w" style="left:{SB+14}px;top:4px">{html_of('send_title')}</div>
<div class="abs w" style="left:0;top:0;width:{SB}px;height:{H}px">{html_of('send_sidebar')}</div>
<div class="abs w" style="left:{SB+10}px;right:12px;bottom:{BOT_M}px;height:{BOT_H}px">{html_of('send_bottom')}</div>
<div class="abs w" id=right style="left:{W*0.64+2}px;right:12px;top:{R_TOP}px;bottom:{H-bottom_top+R_BOT}px">{html_of('send_right')}</div>
<div class=abs style="right:6px;top:2px">{node(act.find('{*}LinearLayout'), None)}</div>
</div>'''
# stage canvas: podium + front view of the default skin, same geometry as HomeStage
GRASS = img('send_grass')
skin = base64.b64encode(open(f'{AS}/send/skin.png', 'rb').read()).decode()
html += f'''<script>
const cv=document.getElementById('stage'),c=cv.getContext('2d');c.scale(2,2);
const w={W*0.64-SB},h={bottom_top},s=Math.min(h/38,w/24)*0.80,cx=w/2,cy=h*0.47+16*s+4,rw=Math.min(w*0.46,s*15),rh=rw*0.26;
const g=c.createRadialGradient(w/2,h*0.55,0,w/2,h*0.55,Math.min(w,h)*0.70);g.addColorStop(0,'rgba(139,92,246,.47)');g.addColorStop(1,'rgba(139,92,246,0)');c.fillStyle=g;c.fillRect(0,0,w,h);
function ov(x,y,rx,ry,f,st,lw){{c.beginPath();c.ellipse(x,y,rx,ry,0,0,7);if(f){{c.fillStyle=f;c.fill()}}if(st){{c.strokeStyle=st;c.lineWidth=lw;c.stroke()}}}}
ov(cx,cy+14,rw,rh,'#120a2a');ov(cx,cy+7,rw,rh,'#1d1040');ov(cx,cy,rw,rh,'#2d1a5c');ov(cx,cy,rw*.93,rh*.93,null,'rgba(139,92,246,.2)',6);ov(cx,cy,rw*.93,rh*.93,null,'#c4a8ff',2.2);
const im=new Image();im.onload=()=>{{c.imageSmoothingEnabled=false;const oy=h*0.47,P=(sx,sy,sw,sh,x,y,u,v)=>c.drawImage(im,sx,sy,sw,sh,cx+x*s,oy-y*s,u*s,v*s);
 P(4,20,4,12,-4,-4,4,12);P(20,52,4,12,0,-4,4,12);P(20,20,8,12,-4,8,8,12);P(44,20,4,12,-8,8,4,12);P(36,52,4,12,4,8,4,12);P(8,8,8,8,-4,16,8,8);
 c.fillStyle='#fff';c.font='bold 13px sans-serif';
 for(const [x,l] of [[10,'‹'],[w-48,'›']]){{c.fillStyle='rgba(26,16,48,.72)';c.strokeStyle='#6d4bd6';c.lineWidth=1.5;c.beginPath();c.roundRect(x,h*.46-19,38,38,11);c.fill();c.stroke();c.fillStyle='#fff';c.font='bold 24px sans-serif';c.fillText(l,x+14,h*.46+8)}}
 c.fillStyle='rgba(26,16,48,.72)';c.beginPath();c.roundRect(w/2-50,cy+rh*.45,100,30,11);c.fill();c.strokeStyle='#6d4bd6';c.stroke();c.fillStyle='#fff';c.font='bold 13px sans-serif';c.fillText('⟳  Rotate',w/2-30,cy+rh*.45+19);
 
 const row=document.querySelector('[data-id=send_vers]');const gr=document.querySelector('img[src^="data"]');
 [['1.21.4',0],['1.21.1',1],['1.20.6',2],['1.19.4',2],['1.18.2',2]].forEach(([t,st])=>{{const d=document.createElement('div');
  d.style.cssText='flex:1;margin-right:4px;display:flex;flex-direction:column;align-items:center;justify-content:center;padding:2px;border-radius:12px;box-sizing:border-box;'+(st==0?'background:#241650;border:2px solid #a78bfa;':'background:#1b1436ee;border:1px solid #3b2a73;');
  d.innerHTML='<img src="{GRASS}" style="width:24px;height:24px;image-rendering:pixelated"><div style="color:#f5f3ff;font:700 11px sans-serif">'+t+'</div>'+(st==0?'<div style="background:#6d28d9;color:#fff;font:8.5px sans-serif;border-radius:7px;padding:0 5px">Installed</div>':st==1?'<div style="color:#c4b5fd;font:8.5px sans-serif">Ready</div>':'<div style="color:#c4b5fd;font-size:11px">⬇</div>');row.appendChild(d)}});
 document.title='ready'}};im.src='data:image/png;base64,{skin}';
</script>'''
open(OUT + '.html', 'w').write(html)
with sync_playwright() as p:
    b = p.chromium.launch(); pg = b.new_page(viewport={'width': int(W), 'height': int(H)}, device_scale_factor=2)
    pg.goto('file://' + os.path.abspath(OUT + '.html')); pg.wait_for_function("document.title==='ready'", timeout=8000)
    res = pg.evaluate('''()=>{const o=[];document.querySelectorAll('.scroll').forEach(e=>{if(e.scrollHeight>e.clientHeight+1)o.push(['scroll overflow',e.dataset.id,e.scrollHeight,e.clientHeight])});
      const r=document.getElementById('right').getBoundingClientRect();document.querySelectorAll('#right *').forEach(e=>{const b=e.getBoundingClientRect();if(b.height>0&&(b.bottom>r.bottom+1||b.right>r.right+1))o.push(['clipped',e.dataset.id||e.className,Math.round(b.bottom-r.bottom)])});
      return o}''')
    pg.screenshot(path=OUT); b.close()
print('overflow report:', json.dumps(res[:10]))
