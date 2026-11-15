#!/usr/bin/env python3
"""Send Launcher artwork, generated with PIL (no external assets): usage art.py <out-res-dir> <out-assets-dir>
Writes: drawable-nodpi/send_*.png (forest background, logo, icons, grass block) and assets/send/{skin,cape}.png"""
import sys, os, random, math
from PIL import Image, ImageDraw, ImageFilter

R = sys.argv[1]; A = sys.argv[2]
ND = f'{R}/drawable-nodpi'; os.makedirs(ND, exist_ok=True); os.makedirs(f'{A}/send', exist_ok=True)

# ------------------------------------------------------------------ default skin (64x64) + cape (64x32)
BLK, BLK2, PUR, PUR2, PURL, SKN, SKN2, WHT, EYE = (22, 18, 36, 255), (34, 28, 54, 255), (139, 92, 246, 255), (109, 40, 217, 255), (196, 181, 253, 255), \
    (224, 172, 134, 255), (196, 140, 106, 255), (240, 236, 255, 255), (168, 85, 247, 255)

def skin():
    im = Image.new('RGBA', (64, 64), (0, 0, 0, 0)); px = im.load(); d = ImageDraw.Draw(im)
    def rect(x, y, w, h, c): d.rectangle([x, y, x + w - 1, y + h - 1], fill=c)
    # head  (top 8,0  bottom 16,0  right 0,8  front 8,8  left 16,8  back 24,8)
    rect(8, 0, 8, 8, BLK); rect(16, 0, 8, 8, BLK2)
    for x, y, w in ((0, 8, 8), (8, 8, 8), (16, 8, 8), (24, 8, 8)): rect(x, y, w, 8, BLK)
    rect(9, 11, 6, 5, SKN); rect(8, 11, 1, 2, BLK); rect(15, 11, 1, 2, BLK)          # face under fringe
    rect(9, 15, 6, 1, SKN2)
    for ex in (9, 13): rect(ex, 12, 2, 2, EYE); px[ex, 12] = WHT                    # purple eyes with a highlight
    rect(11, 14, 2, 1, SKN2)
    for y in (10,): rect(8, y, 1, 1, BLK)
    for x, y in ((0, 9), (1, 10), (2, 12), (3, 10), (5, 13), (17, 10), (19, 12), (21, 9), (24, 12), (27, 10), (30, 13)): px[x, y] = PUR
    rect(0, 14, 8, 2, BLK); rect(16, 14, 8, 2, BLK)
    # hair / hat layer
    for x, y, w, h in ((40, 0, 8, 8), (48, 0, 8, 8), (32, 8, 8, 8), (40, 8, 8, 4), (48, 8, 8, 8), (56, 8, 8, 8)): rect(x, y, w, h, BLK2)
    rect(40, 12, 1, 3, BLK2); rect(47, 12, 1, 3, BLK2)
    for x, y in ((41, 8), (44, 9), (46, 8), (33, 10), (36, 13), (50, 11), (58, 12), (60, 9), (42, 3), (45, 5)): px[x, y] = PUR2
    # torso (top 20,16  bottom 28,16  right 16,20  front 20,20  left 28,20  back 32,20)
    rect(20, 16, 8, 4, BLK); rect(28, 16, 8, 4, BLK)
    for x, w in ((16, 4), (20, 8), (28, 4), (32, 8)): rect(x, 20, w, 12, BLK)
    rect(20, 31, 8, 1, PUR); rect(32, 31, 8, 1, PUR); rect(16, 31, 4, 1, PUR); rect(28, 31, 4, 1, PUR)
    S = ('.XXX.', 'X...X', 'X....', '.XXX.', '....X', 'X...X', '.XXX.')              # purple S on the chest
    for j, row in enumerate(S):
        for i, ch in enumerate(row):
            if ch == 'X': px[22 + i, 22 + j] = PUR
    rect(21, 21, 1, 10, BLK2); rect(27, 21, 1, 10, BLK2)
    # arms: right (40,16) front 44,20 ; left (32,48) front 36,52
    for ox, oy in ((40, 16), (32, 48)):
        rect(ox + 4, oy, 4, 4, BLK); rect(ox + 8, oy, 4, 4, BLK)
        for x, w in ((ox, 4), (ox + 4, 4), (ox + 8, 4), (ox + 12, 4)):
            rect(x, oy + 4, w, 12, BLK)
            rect(x, oy + 8, w, 2, PUR)                                             # purple band
            rect(x, oy + 13, w, 3, SKN)                                           # hand
        rect(ox + 8, oy, 4, 4, SKN2)
    # legs: right (0,16) front 4,20 ; left (16,48) front 20,52
    for ox, oy in ((0, 16), (16, 48)):
        rect(ox + 4, oy, 4, 4, BLK); rect(ox + 8, oy, 4, 4, BLK2)
        for x in (ox, ox + 4, ox + 8, ox + 12):
            rect(x, oy + 4, 4, 12, BLK)
            rect(x, oy + 9, 4, 1, BLK2)
            rect(x, oy + 12, 4, 1, PUR)                                           # purple trim
            rect(x, oy + 13, 4, 3, WHT)                                           # white sneakers
            rect(x, oy + 14, 4, 1, PURL)
        rect(ox + 8, oy, 4, 4, WHT)
    return im

def cape():
    im = Image.new('RGBA', (64, 32), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    for ox in (1, 12):          # outer / inner face are both 10x16
        for y in range(16):
            t = y / 15
            c = (int(22 + 70 * t * t), int(18 + 20 * t), int(36 + 150 * t * t), 255)
            d.line([(ox, y + 1), (ox + 9, y + 1)], fill=c)
        d.line([(ox, 1), (ox, 16)], fill=PUR2); d.line([(ox + 9, 1), (ox + 9, 16)], fill=PUR2)
        d.line([(ox, 16), (ox + 9, 16)], fill=PUR); d.line([(ox + 1, 15), (ox + 8, 15)], fill=PURL)
    d.rectangle([1, 0, 10, 0], fill=BLK); d.rectangle([11, 1, 11, 16], fill=BLK); d.rectangle([0, 1, 0, 16], fill=BLK)
    d.rectangle([22, 1, 22, 16], fill=BLK)
    return im

skin().save(f'{A}/send/skin.png'); cape().save(f'{A}/send/cape.png')

# ------------------------------------------------------------------ pixel forest background (384x216, drawn crisp)
def forest():
    W, H = 384, 216; rnd = random.Random(11)
    im = Image.new('RGB', (W, H)); px = im.load()
    stops = [(0.0, (10, 8, 32)), (0.30, (34, 18, 80)), (0.52, (112, 52, 176)), (0.62, (186, 108, 226)), (1.0, (60, 34, 120))]
    def grad(t):
        for (a, ca), (b, cb) in zip(stops, stops[1:]):
            if a <= t <= b:
                k = (t - a) / (b - a); return tuple(int(ca[i] * (1 - k) + cb[i] * k) for i in range(3))
        return stops[-1][1]
    for y in range(H):
        c = grad(y / H)
        for x in range(W): px[x, y] = c
    d = ImageDraw.Draw(im)
    for _ in range(70):
        x, y = rnd.randrange(W), rnd.randrange(int(H * .45)); b = rnd.choice(((200, 190, 255), (150, 130, 230), (255, 255, 255)))
        px[x, y] = b
    # glowing sun square
    sx, sy = int(W * .535), int(H * .16)
    glow = Image.new('RGB', (W, H), (0, 0, 0)); gd = ImageDraw.Draw(glow)
    for r, col in ((46, (40, 22, 70)), (34, (70, 36, 110)), (24, (120, 70, 170)), (15, (190, 140, 230))): gd.ellipse([sx - r, sy - r, sx + r, sy + r], fill=col)
    glow = glow.filter(ImageFilter.GaussianBlur(5))
    from PIL import ImageChops
    im = ImageChops.add(im, glow); d = ImageDraw.Draw(im)
    d.rectangle([sx - 6, sy - 6, sx + 6, sy + 6], fill=(250, 240, 255)); d.rectangle([sx + 2, sy - 6, sx + 6, sy + 6], fill=(225, 205, 250))
    def spruce(cx, base, h, col, hi=None):
        for i in range(h):
            w = max(1, int(i * (h / 3.2) / h) // 1 + 1) if False else 1 + (i * 5) // max(6, h) * 1
            w = 1 + int(i * (0.42 if h > 18 else 0.5))
            step = (i // 3) % 2
            d.rectangle([cx - w + step, base - h + i, cx + w - step, base - h + i], fill=col)
        d.rectangle([cx - 1, base - 3, cx, base + 6], fill=col)
    def layer(base, hmin, hmax, col, gap, jitter=0):
        x = -6
        while x < W + 6:
            spruce(x, base + rnd.randint(-jitter, jitter), rnd.randint(hmin, hmax), col); x += gap + rnd.randint(-2, 3)
        d.rectangle([0, base + 4, W, H], fill=col)
    layer(int(H * .60), 18, 34, (74, 38, 138), 9, 2)
    layer(int(H * .66), 22, 40, (52, 28, 108), 11, 2)
    # mid oak trees (green, purple-tinted)
    def oak(cx, base, s, tone):
        d.rectangle([cx - s // 5, base - s, cx + s // 5, base], fill=(58, 36, 52))
        for _ in range(int(s * s * 0.55)):
            ax = rnd.randint(-s, s); ay = rnd.randint(-s, s // 2)
            if (ax / s) ** 2 + (ay / (s * .8)) ** 2 < 1:
                g = rnd.choice(tone); d.rectangle([cx + ax, base - s * 1.5 + ay + s // 2, cx + ax + 2, base - s * 1.5 + ay + s // 2 + 2], fill=g)
    greens = [((36, 72, 70), (44, 88, 84), (30, 58, 70)), ((46, 98, 70), (60, 120, 78), (38, 80, 64)), ((62, 130, 74), (80, 156, 86), (50, 104, 72))]
    for cx in range(8, W, 58):
        oak(cx + rnd.randint(-8, 8), int(H * .72) + rnd.randint(-3, 3), rnd.randint(11, 16), greens[0])
    layer(int(H * .78), 24, 44, (34, 20, 78), 13, 3)
    for cx in range(30, W, 66):
        oak(cx + rnd.randint(-10, 10), int(H * .82) + rnd.randint(-3, 3), rnd.randint(16, 22), greens[1])
    # foreground stepped terrain on the right and left (grass blocks over dirt)
    def terrain(x0, x1, steps, top, rise, g=(76, 150, 66), dirt=(104, 70, 50)):
        n = (x1 - x0) // steps
        for i in range(steps):
            ytop = top + rise * (i if x1 > x0 else i)
            xa = x0 + i * n; xb = xa + n
            d.rectangle([xa, ytop, xb, H], fill=dirt)
            d.rectangle([xa, ytop, xb, ytop + 4], fill=g)
            for _ in range(int(n * (H - ytop) / 40)): 
                d.point((rnd.randint(xa, xb), rnd.randint(ytop + 5, H - 1)), fill=rnd.choice(((88, 60, 44), (118, 80, 58), (74, 50, 40))))
            for _ in range(int(n / 2)): d.point((rnd.randint(xa, xb), rnd.randint(ytop, ytop + 3)), fill=rnd.choice(((94, 170, 76), (62, 128, 58), (110, 186, 90))))
    terrain(int(W * .62), W, 7, int(H * .86), -8)
    terrain(0, int(W * .22), 4, int(H * .84), 8)
    for cx in (int(W * .70), int(W * .84), int(W * .94)):
        oak(cx, int(H * .80) - (cx > W * .8) * 12, 26, greens[2])
    # purple haze + vignette
    haze = Image.new('RGB', (W, H), (90, 40, 170)); mask = Image.new('L', (W, H)); md = ImageDraw.Draw(mask)
    for y in range(H): md.line([(0, y), (W, y)], fill=int(70 * math.exp(-((y - H * .62) / (H * .25)) ** 2)))
    im = Image.composite(haze, im, mask)
    vig = Image.new('L', (W, H)); vd = ImageDraw.Draw(vig)
    for x in range(W): vd.line([(x, 0), (x, H)], fill=int(max(0, (1 - x / (W * .18))) * 150 + (max(0, (x - W * .9) / (W * .1))) * 60))
    im = Image.composite(Image.new('RGB', (W, H), (10, 6, 26)), im, vig)
    bot = Image.new('L', (W, H)); bd = ImageDraw.Draw(bot)
    for y in range(H): bd.line([(0, y), (W, y)], fill=int(max(0, (y - H * .55) / (H * .45)) ** 1.6 * 150))
    im = Image.composite(Image.new('RGB', (W, H), (8, 5, 22)), im, bot)
    return im
forest().save(f'{ND}/send_forest.png', optimize=True)

# ------------------------------------------------------------------ icons (supersampled, white-lavender outline, 96x96)
SS = 4; N = 96; LW = 7
ICO = (233, 228, 255, 255)
def canvas(): return Image.new('RGBA', (N * SS, N * SS), (0, 0, 0, 0))
def fin(im, name): im.resize((N, N), Image.LANCZOS).save(f'{ND}/{name}.png', optimize=True)
def P(pts): return [(x * SS, y * SS) for x, y in pts]
def line(d, pts, w=LW, c=ICO):
    d.line(P(pts), fill=c, width=w * SS // 2 * 1, joint='curve')
    for x, y in P(pts): d.ellipse([x - w * SS / 4, y - w * SS / 4, x + w * SS / 4, y + w * SS / 4], fill=c)
def mk(name, fn, c=ICO):
    im = canvas(); d = ImageDraw.Draw(im); fn(d, c); fin(im, name)

def i_home(d, c):
    line(d, [(10, 46), (48, 14), (86, 46)], 8, c); line(d, [(20, 40), (20, 82), (38, 82), (38, 58), (58, 58), (58, 82), (76, 82), (76, 40)], 8, c)
def i_cube(d, c):
    line(d, [(48, 10), (84, 29), (84, 67), (48, 86), (12, 67), (12, 29), (48, 10)], 8, c); line(d, [(12, 29), (48, 48), (84, 29)], 8, c); line(d, [(48, 48), (48, 86)], 8, c)
def i_puzzle(d, c):
    d.polygon(P([(14, 34), (36, 34), (36, 28), (42, 18), (54, 18), (60, 28), (60, 34), (82, 34), (82, 52), (88, 52), (92, 62), (88, 72), (82, 72), (82, 86), (14, 86)]), fill=c)
    d.rectangle([x * SS for x in (28, 50, 44, 64)], fill=(0, 0, 0, 0)) if False else None
def i_image(d, c):
    line(d, [(12, 16), (84, 16), (84, 80), (12, 80), (12, 16)], 8, c); d.polygon(P([(18, 74), (38, 48), (52, 62), (62, 50), (80, 74)]), fill=c); d.ellipse([x * SS for x in (60, 26, 74, 40)], fill=c)
def i_sun(d, c):
    d.ellipse([x * SS for x in (30, 30, 66, 66)], outline=c, width=7 * SS // 2)
    for a in range(0, 360, 45):
        ca, sa = math.cos(math.radians(a)), math.sin(math.radians(a)); line(d, [(48 + 30 * ca, 48 + 30 * sa), (48 + 40 * ca, 48 + 40 * sa)], 8, c)
def i_gear(d, c):
    for a in range(0, 360, 45):
        ca, sa = math.cos(math.radians(a)), math.sin(math.radians(a)); line(d, [(48 + 28 * ca, 48 + 28 * sa), (48 + 38 * ca, 48 + 38 * sa)], 14, c)
    d.ellipse([x * SS for x in (20, 20, 76, 76)], fill=c); d.ellipse([x * SS for x in (34, 34, 62, 62)], fill=(0, 0, 0, 0))
def i_info(d, c):
    d.ellipse([x * SS for x in (10, 10, 86, 86)], outline=c, width=7 * SS // 2); line(d, [(48, 42), (48, 68)], 9, c); d.ellipse([x * SS for x in (43, 24, 53, 34)], fill=c)
def i_crown(d, c):
    d.polygon(P([(12, 70), (8, 28), (30, 46), (48, 18), (66, 46), (88, 28), (84, 70)]), fill=c); d.rectangle([12 * SS, 76 * SS, 84 * SS, 84 * SS], fill=c)
def i_shirt(d, c):
    d.polygon(P([(30, 14), (42, 14), (48, 22), (54, 14), (66, 14), (90, 30), (80, 46), (70, 40), (70, 84), (26, 84), (26, 40), (16, 46), (6, 30)]), fill=c)
def i_plus(d, c):
    line(d, [(48, 14), (48, 82)], 11, c); line(d, [(14, 48), (82, 48)], 11, c)
def i_dl(d, c):
    line(d, [(48, 12), (48, 62)], 10, c); line(d, [(26, 42), (48, 64), (70, 42)], 10, c); line(d, [(18, 84), (78, 84)], 10, c)
def i_play(d, c):
    d.polygon(P([(22, 10), (22, 86), (84, 48)]), fill=(255, 255, 255, 255))
def i_pencil(d, c):
    d.polygon(P([(14, 82), (18, 60), (62, 16), (80, 34), (36, 78)]), fill=c); d.polygon(P([(66, 12), (74, 8), (88, 22), (84, 30)]), fill=c)
def i_brush(d, c):
    line(d, [(78, 12), (44, 50)], 12, c); d.ellipse([x * SS for x in (20, 46, 50, 86)], fill=c); d.polygon(P([(30, 52), (48, 52), (44, 64), (24, 78)]), fill=c)
def i_chev(d, c): line(d, [(34, 16), (62, 48), (34, 80)], 10, c)
def i_arrowl(d, c): line(d, [(62, 14), (30, 48), (62, 82)], 11, c)
def i_arrowr(d, c): line(d, [(34, 14), (66, 48), (34, 82)], 11, c)
def i_rot(d, c):
    d.arc([x * SS for x in (16, 16, 80, 80)], 40, 330, fill=c, width=9 * SS // 2); d.polygon(P([(70, 6), (86, 30), (56, 32)]), fill=c)
def i_min(d, c): line(d, [(18, 66), (78, 66)], 10, c)
def i_close(d, c): line(d, [(22, 22), (74, 74)], 10, c); line(d, [(74, 22), (22, 74)], 10, c)
def i_check(d, c): line(d, [(22, 50), (42, 70), (76, 28)], 11, c)
for n, f in (('home', i_home), ('versions', i_cube), ('mods', i_puzzle), ('packs', i_image), ('shaders', i_sun), ('gear', i_gear), ('info', i_info),
             ('shirt', i_shirt), ('plus', i_plus), ('dl', i_dl), ('pencil', i_pencil), ('chev', i_chev), ('arrowl', i_arrowl), ('arrowr', i_arrowr),
             ('rot', i_rot), ('min', i_min), ('close', i_close), ('check', i_check)): mk(f'send_i_{n}', f)
mk('send_i_crown', i_crown, (167, 139, 250, 255)); mk('send_i_play', i_play); mk('send_i_dlv', i_dl, (167, 139, 250, 255)); mk('send_i_brush', i_brush, (167, 139, 250, 255))
mk('send_i_mods_p', i_puzzle, (167, 139, 250, 255)); mk('send_i_sun_p', i_sun, (167, 139, 250, 255)); mk('send_i_chev_p', i_chev, (196, 181, 253, 255))

# ------------------------------------------------------------------ grass block + logo
def grass(size=32):
    im = Image.new('RGBA', (size, size), (0, 0, 0, 0)); d = ImageDraw.Draw(im); r = random.Random(3)
    def poly(pts, col, noise):
        d.polygon(pts, fill=col)
    # isometric cube, 32px grid
    top = [(16, 3), (29, 10), (16, 17), (3, 10)]; left = [(3, 10), (16, 17), (16, 30), (3, 23)]; right = [(29, 10), (16, 17), (16, 30), (29, 23)]
    d.polygon(top, fill=(92, 168, 72)); d.polygon(left, fill=(120, 82, 56)); d.polygon(right, fill=(98, 66, 44))
    for _ in range(26):
        x, y = r.randint(4, 28), r.randint(6, 14)
        if abs(x - 16) / 13 + abs(y - 10) / 7 < 1: d.point((x, y), fill=r.choice(((70, 140, 58), (118, 196, 92))))
    d.polygon([(3, 10), (16, 17), (16, 21), (3, 14)], fill=(76, 150, 62)); d.polygon([(29, 10), (16, 17), (16, 21), (29, 14)], fill=(62, 124, 52))
    for _ in range(30):
        x, y = r.randint(4, 28), r.randint(14, 28)
        if (x < 16 and y > 10 + (16 - x) * .54 + 4) or (x >= 16 and y > 10 + (x - 16) * .54 + 4): d.point((x, y), fill=r.choice(((88, 58, 40), (136, 96, 66), (70, 46, 34))))
    return im
g = grass(); g.resize((128, 128), Image.NEAREST).save(f'{ND}/send_grass.png', optimize=True)

def logo(n=256):
    im = Image.new('RGBA', (n, n), (0, 0, 0, 0)); d = ImageDraw.Draw(im); s = n
    def Q(x, y): return (x * s, y * s)
    top = [Q(.5, .06), Q(.92, .28), Q(.5, .5), Q(.08, .28)]; left = [Q(.08, .28), Q(.5, .5), Q(.5, .94), Q(.08, .72)]; right = [Q(.92, .28), Q(.5, .5), Q(.5, .94), Q(.92, .72)]
    d.polygon(top, fill=(196, 181, 253)); d.polygon(left, fill=(124, 58, 237)); d.polygon(right, fill=(91, 33, 182))
    for poly in (top, left, right): d.line(poly + [poly[0]], fill=(237, 233, 254), width=max(2, n // 48))
    return im
logo().save(f'{ND}/send_logo.png', optimize=True)
print('art ok')
