import sys, re, json, glob, ast, copy, uuid, pathlib, operator
from PIL import Image
ROOT = pathlib.Path(sys.argv[1]); MOJO = pathlib.Path(sys.argv[2]); LOGO = pathlib.Path(sys.argv[3])
NAME = "Robin Launcher V2"

def rw(path, fn):
    p = ROOT / path; s = p.read_text(encoding="utf-8"); n = fn(s)
    if n == s: raise SystemExit(f"no change: {path}")
    p.write_text(n, encoding="utf-8")

# ---------- 1. visible names in every language ----------
pat = re.compile(r"Fold Craft Launcher|\bFCL\b(?!-Team)")
whole = ("notification_title", "game_service_notification_title")
changed = 0
for f in sorted(ROOT.glob("res/values*/strings.xml")):
    s = f.read_text(encoding="utf-8"); o = s
    for key in whole:
        s = re.sub(r'(<string name="%s"[^>]*>)[^<]*(</string>)' % key, r"\g<1>%s\g<2>" % NAME, s)
    s = s.replace('name="terracotta_notification_title">Terracotta is running', 'name="terracotta_notification_title">%s: Terracotta is running' % NAME)
    s = pat.sub(NAME, s)
    if s != o: f.write_text(s, encoding="utf-8"); changed += 1
print("strings files changed:", changed)
# the English game notification text keeps working as a second line
rw("res/values/strings.xml", lambda s: s.replace('name="game_service_notification_text">Tap to return to the game', 'name="game_service_notification_text">Game running. Tap to return'))

# ---------- 2. literals in code ----------
rw("smali_classes4/com/tungsten/fclcore/download/ProcessService.smali", lambda s: s.replace('const-string v1, "FCL Process"', 'const-string v1, "%s"' % NAME))
hit = [p for p in ROOT.glob("smali*/**/*.smali") if "Welcome to use Fold Craft Launcher!" in p.read_text(encoding="utf-8")]
for p in hit:
    p.write_text(p.read_text(encoding="utf-8").replace("Welcome to use Fold Craft Launcher!", "Welcome to %s!" % NAME), encoding="utf-8")
print("welcome literal files:", len(hit))

# ---------- 3. remove About tab ----------
rw("res/layout/ui_setting.xml", lambda s: re.sub(r'\n\s*<com\.google\.android\.material\.tabs\.TabItem android:icon="@drawable/ic_baseline_info_24"[^>]*/>', "", s))
U = "smali_classes4/com/tungsten/fcl/ui/setting/SettingUI.smali"
rw(U, lambda s: s.replace(".method public getPageCount()I\n    .locals 0\n\n    const/4 p0, 0x4", ".method public getPageCount()I\n    .locals 0\n\n    const/4 p0, 0x3"))
def titles(s):
    a = s.index("    .line 62\n")
    b = s.index("    filled-new-array {v0, v1, v2, p0}, [Ljava/lang/String;")
    s = s[:a] + s[b:]
    return s.replace("    filled-new-array {v0, v1, v2, p0}, [Ljava/lang/String;", "    filled-new-array {v0, v1, v2}, [Ljava/lang/String;")
rw(U, titles)
# getTabTitles used p0 (this) as the last result register; make sure the move-result/return still use p0
def fixret(s):
    i = s.index("filled-new-array {v0, v1, v2}, [Ljava/lang/String;")
    return s  # move-result-object p0 / return-object p0 remain valid (p0 is free after getContext calls)
rw(U, lambda s: s + "\n") if False else None

# ---------- 4. icon: Robin logo on a dark background ----------
fg = Image.open(LOGO).convert("RGBA")
for d, px in {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}.items():
    fg.resize((px, px), Image.LANCZOS).save(ROOT / f"res/mipmap-{d}/ic_launcher_foreground.webp", "WEBP", lossless=True)
for d, px in {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}.items():
    bg = Image.new("RGBA", (px, px), (20, 17, 10, 255))
    big = fg.resize((int(px * 1.5), int(px * 1.5)), Image.LANCZOS)
    o = (px - big.width) // 2
    bg.alpha_composite(big, (o, o)) if o >= 0 else bg.alpha_composite(big.crop((-o, -o, -o + px, -o + px)), (0, 0))
    m = Image.new("L", (px, px), 0)
    from PIL import ImageDraw
    ImageDraw.Draw(m).ellipse((0, 0, px - 1, px - 1), fill=255)
    bg.putalpha(m); bg.save(ROOT / f"res/mipmap-{d}/ic_launcher_round.png")
for f in ("res/values/colors.xml", "res/values-v31/colors.xml"):
    rw(f, lambda s: re.sub(r'(<color name="icon_background_color">)[^<]*(</color>)', r"\g<1>#ff14110a\g<2>", s))

# ---------- 5. default touch controls laid out like Mojo's ----------
mojo = json.load(open(MOJO))
SW, SH, MARGIN = 880.0, 400.0, 4.0
ops = {ast.Add: operator.add, ast.Sub: operator.sub, ast.Mult: operator.mul}
def ev(expr, w, h):
    e = expr
    for k, v in {"margin": MARGIN, "width": w, "height": h, "screen_width": SW, "screen_height": SH, "right": SW - w, "bottom": SH - h}.items():
        e = e.replace("${%s}" % k, "(%s)" % v)
    def go(n):
        if isinstance(n, ast.Expression): return go(n.body)
        if isinstance(n, ast.Constant): return float(n.value)
        if isinstance(n, ast.BinOp) and type(n.op) in ops: return ops[type(n.op)](go(n.left), go(n.right))
        if isinstance(n, ast.UnaryOp) and isinstance(n.op, ast.USub): return -go(n.operand)
        raise ValueError(expr)
    return go(ast.parse(e, mode="eval"))
KEY = {292: 61, 84: 20, 258: 15, 294: 63, 87: 17, 65: 30, 83: 31, 68: 32, 69: 18, 340: 42, 32: 57}
path = ROOT / "assets/controllers/00000000.json"
fcl = json.load(open(path)); grp = fcl["viewGroups"][0]; tmpl = copy.deepcopy(grp["viewData"]["buttonList"][0])
def blank_event():
    return {"autoKeep": False, "autoClick": False, "openMenu": False, "switchTouchMode": False, "input": False, "quickInput": False, "outputText": "", "outputKeycodes": [], "bindViewGroup": []}
buttons = []
for c in mojo["mControlDataList"]:
    w, h = float(c["width"]), float(c["height"]); x = ev(c["dynamicX"], w, h); y = ev(c["dynamicY"], w, h)
    code = c["keycodes"][0]; b = copy.deepcopy(tmpl); b["id"] = str(uuid.uuid4())
    name = c["name"]; vis = "IN_GAME"; follow = False
    ev_press, ev_click = blank_event(), blank_event()
    if code == -1: name, vis = "Keyboard", "ALWAYS"; ev_click["input"] = True
    elif code == -2: name, vis = "Menu", "ALWAYS"; ev_click["openMenu"] = True
    elif code == -3: ev_press["outputKeycodes"] = [1000]; follow = True; name = "PRI"
    elif code == -4: ev_press["outputKeycodes"] = [1002]; follow = True; name = "SEC"
    elif code == -5: name, vis = "Mouse", "ALWAYS"; ev_click["switchTouchMode"] = True
    elif code in KEY:
        if c.get("isToggle"): ev_click["outputKeycodes"] = [KEY[code]]; ev_click["autoKeep"] = True
        else: ev_press["outputKeycodes"] = [KEY[code]]
        if name in ("Debug", "Chat", "Tab", "3rd"): vis = "ALWAYS"
    else: continue
    b["text"] = name
    b["baseInfo"].update({"visibilityType": vis, "xPosition": round(x / SW * 1000), "yPosition": round(y / SH * 1000), "sizeType": "PERCENTAGE",
        "percentageWidth": {"reference": "SCREEN_HEIGHT", "size": round(w / SH * 1000)}, "percentageHeight": {"reference": "SCREEN_HEIGHT", "size": round(h / SH * 1000)}})
    b["event"]["pointerFollow"] = follow; b["event"]["Movable"] = False
    b["event"]["pressEvent"] = ev_press; b["event"]["clickEvent"] = ev_click
    b["event"]["longPressEvent"] = blank_event(); b["event"]["doubleClickEvent"] = blank_event()
    buttons.append(b)
grp["viewData"]["buttonList"] = buttons; grp["viewData"]["directionList"] = []
st = fcl["buttonStyles"][0]
st.update({"strokeWidth": 0, "cornerRadius": 0, "fillColor": 1291845632, "strokeColorPressed": 0, "strokeWidthPressed": 0, "cornerRadiusPressed": 0, "fillColorPressed": -2130706433, "textSize": 12, "textSizePressed": 12})
fcl["name"] = "Robin Default"; fcl["author"] = "Robin"; fcl["description"] = "Default controls of %s, laid out like Mojo-style touch controls." % NAME
path.write_text(json.dumps(fcl, indent=2), encoding="utf-8")
print("buttons:", len(buttons))
