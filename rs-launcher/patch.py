import re, sys, pathlib, shutil
from PIL import Image, ImageDraw, ImageFont
SRC = pathlib.Path(sys.argv[1]); HERE = pathlib.Path(__file__).parent
M = SRC / "ZalithLauncher/src/main"; J = M / "java/com/movtery/zalithlauncher"

def edit(p, fn):
    p = pathlib.Path(p); s = p.read_text(encoding="utf-8"); n = fn(s)
    if n == s: raise SystemExit(f"patch did not apply: {p}")
    p.write_text(n, encoding="utf-8")

def rep(a, b):
    def f(s):
        if a not in s: raise SystemExit(f"missing text: {a[:60]}")
        return s.replace(a, b, 1)
    return f

# name
edit(SRC / "ZalithLauncher/gradle.properties", lambda s: s.replace("launcher_app_name=Zalith Launcher", "launcher_app_name=RS Launcher"))
# application id and signing
g = SRC / "ZalithLauncher/build.gradle.kts"
edit(g, rep("applicationId = zalithPackageName", 'applicationId = "com.rslauncher.app"'))
edit(g, rep('        applicationIdSuffix = ".v2"\n', ""))
edit(g, rep('storeFile = file("zalith_launcher.jks")', 'storeFile = file(System.getenv("RS_KEYSTORE") ?: "zalith_launcher.jks")'))
edit(g, lambda s: re.sub(r'(create\("releaseBuild"\) \{.*?)storePassword = [^\n]*\n(\s*)keyAlias = "[^"]*"\n\s*keyPassword = [^\n]*',
    r'\1storePassword = "mojogames"\n\2keyAlias = "mojo"\n\2keyPassword = "mojogames"', s, count=1, flags=re.S))

# game directory: /storage/emulated/0/Games/RSLauncher
gp = J / "game/path/GamePathManager.kt"
edit(gp, rep('private val defaultGamePath = File(PathManager.DIR_FILES_EXTERNAL, ".minecraft").absolutePath',
'''private val defaultGamePath: String get() = rsDefaultPath()

    /** RS Launcher keeps its data in Games/RSLauncher once file access is granted. */
    private fun rsDefaultPath(): String {
        val ok = if (android.os.Build.VERSION.SDK_INT >= 30) android.os.Environment.isExternalStorageManager() else hasStoragePermission
        return if (ok) File(android.os.Environment.getExternalStorageDirectory(), "Games/RSLauncher").absolutePath
        else File(PathManager.DIR_FILES_EXTERNAL, ".minecraft").absolutePath
    }'''))
edit(gp, rep("fun getUserPath(): String = File(_currentPath.value).parentFile!!.absolutePath", "fun getUserPath(): String = File(_currentPath.value).absolutePath"))

# splash + permission prompt
sa = J / "ui/activities/SplashActivity.kt"
edit(sa, rep("import com.movtery.zalithlauncher.ui.screens.splash.SplashScreen\n", "import com.movtery.zalithlauncher.ui.screens.splash.SplashScreen\nimport com.movtery.zalithlauncher.ui.screens.splash.RsIntroHost\nimport com.movtery.zalithlauncher.utils.checkStoragePermissions\n"))
edit(sa, rep("                    SplashScreen(\n", "                    RsIntroHost {\n                    SplashScreen(\n"))
edit(sa, rep("                        screenViewModel = backStackViewModel\n                    )\n", "                        screenViewModel = backStackViewModel\n                    )\n                    }\n"))
edit(sa, rep("        initUnpackItems()\n", '''        initUnpackItems()

        window.decorView.post {
            checkStoragePermissions(
                this,
                message = "RS Launcher keeps all game data in Games/RSLauncher. Please allow file access, then restart the launcher."
            )
        }
'''))
shutil.copy(HERE / "RsIntro.kt", J / "ui/screens/splash/RsIntro.kt")

# icon: RS monogram on dark gold
S = 1296
im = Image.new("RGBA", (S, S), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
d.rounded_rectangle((260, 260, S - 260, S - 260), radius=170, fill=(255, 138, 31, 255))
font = None
for p in ["/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", "/usr/share/fonts/dejavu/DejaVuSans-Bold.ttf"]:
    try: font = ImageFont.truetype(p, 380); break
    except Exception: pass
d.text((S / 2, S / 2 + 12), "RS", font=font, fill=(20, 17, 10, 255), anchor="mm")
res = M / "res"
(res / "drawable/ic_launcher_foreground.xml").unlink()
(res / "drawable-nodpi").mkdir(exist_ok=True)
im.resize((432, 432), Image.LANCZOS).save(res / "drawable-nodpi/ic_launcher_foreground.png")
(res / "drawable/ic_launcher_monochrome.xml").write_text('<?xml version="1.0" encoding="utf-8"?>\n<bitmap xmlns:android="http://schemas.android.com/apk/res/android" android:src="@drawable/ic_launcher_foreground"/>\n')
(res / "values/ic_launcher_background.xml").write_text('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n    <color name="ic_launcher_background">#14110A</color>\n</resources>\n')
for dn, px in {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}.items():
    for nm in ("ic_launcher", "ic_launcher_round"):
        bg = Image.new("RGBA", (px, px), (20, 17, 10, 255)); g2 = im.resize((int(px * 1.45),) * 2, Image.LANCZOS); o = (px - g2.width) // 2
        bg.alpha_composite(g2.crop((-o, -o, -o + px, -o + px)), (0, 0))
        if nm.endswith("round"):
            m = Image.new("L", (px, px), 0); ImageDraw.Draw(m).ellipse((0, 0, px - 1, px - 1), fill=255); bg.putalpha(m)
        bg.save(res / f"mipmap-{dn}/{nm}.webp", "WEBP", lossless=True)
print("patched")
