package dev.robin.v1.gui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import dev.robin.v1.Category;
import dev.robin.v1.Config;
import dev.robin.v1.Draw;
import dev.robin.v1.Module;
import dev.robin.v1.ModuleManager;
import dev.robin.v1.Setting;
import dev.robin.v1.module.GuiModule;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;

/** ColorOS-style dark GUI: category sidebar, searchable module list, per-module settings. */
public class RobinGui extends GuiScreen {
    private static final int BG = 0xFF14141A, SIDE = 0xFF1B1B23, CARD = 0xFF22222C, CARD_HOT = 0xFF2A2A36,
            OFF = 0xFF3A3A48, TEXT = 0xFFEDEDF2, MUTED = 0xFF8D8D9C;
    private static final int ROW_MOD = 28, ROW_SET = 22, GAP = 4, SIDE_W = 104;

    private static Category selected = Category.COMBAT;
    private static Module expanded;

    private final GuiModule style = ModuleManager.get(GuiModule.class);
    private String search = "";
    private float scroll, openAnim;
    private Setting dragging;
    private float dragX, dragW;
    private int wx, wy, ww, wh;

    private static class Row {
        Module m;
        Setting s; // null for module card
        float y, h;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        openAnim = style.animations.on() ? 0f : 1f;
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        Config.save();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void layoutWindow() {
        ww = Math.min(width - 16, 500);
        wh = Math.min(height - 16, 320);
        wx = (width - ww) / 2;
        wy = (height - wh) / 2;
    }

    private List<Module> visible() {
        List<Module> out = new ArrayList<Module>();
        String q = search.toLowerCase();
        for (Module m : ModuleManager.all()) {
            if (q.isEmpty() ? m.category == selected : m.name.toLowerCase().contains(q)) {
                out.add(m);
            }
        }
        return out;
    }

    private List<Row> rows() {
        List<Row> rows = new ArrayList<Row>();
        float y = 0;
        for (Module m : visible()) {
            Row r = new Row();
            r.m = m;
            r.y = y;
            r.h = ROW_MOD;
            rows.add(r);
            y += ROW_MOD + GAP;
            if (m == expanded) {
                for (Setting s : m.settings) {
                    Row sr = new Row();
                    sr.m = m;
                    sr.s = s;
                    sr.y = y;
                    sr.h = ROW_SET;
                    rows.add(sr);
                    y += ROW_SET + 2;
                }
                y += GAP;
            }
        }
        return rows;
    }

    private float contentHeight(List<Row> rows) {
        if (rows.isEmpty()) {
            return 0;
        }
        Row l = rows.get(rows.size() - 1);
        return l.y + l.h;
    }

    // content area
    private int cx() { return wx + SIDE_W + 10; }
    private int cy() { return wy + 44; }
    private int cw() { return ww - SIDE_W - 20; }
    private int ch() { return wh - 54; }

    @Override
    public void drawScreen(int mx, int my, float pt) {
        layoutWindow();
        openAnim += (1f - openAnim) * 0.22f;
        if (openAnim > 0.995f) {
            openAnim = 1f;
        }
        Draw.alpha = openAnim;
        int accent = style.accent();
        int bgA = (int) (style.background.num() / 100.0 * 255);

        Gui.drawRect(0, 0, width, height, Draw.mul(0x88000000));
        GlStateManager.pushMatrix();
        float sc = 0.92f + 0.08f * openAnim;
        GlStateManager.translate(width / 2f, height / 2f, 0);
        GlStateManager.scale(sc, sc, 1);
        GlStateManager.translate(-width / 2f, -height / 2f, 0);

        Draw.round(wx, wy, ww, wh, 12, (bgA << 24) | (BG & 0xFFFFFF));
        Draw.round(wx, wy, SIDE_W, wh, 12, (bgA << 24) | (SIDE & 0xFFFFFF));
        Draw.rect(wx + SIDE_W - 12, wy, 12, wh, (bgA << 24) | (BG & 0xFFFFFF));

        fontRendererObj.drawStringWithShadow("ROBIN", wx + 14, wy + 14, Draw.mul(TEXT));
        fontRendererObj.drawStringWithShadow("V1", wx + 14 + fontRendererObj.getStringWidth("ROBIN "), wy + 14,
                Draw.mul(accent));

        int y = wy + 40;
        for (Category c : Category.values()) {
            boolean sel = search.isEmpty() && c == selected;
            boolean hover = mx >= wx + 8 && mx <= wx + SIDE_W - 8 && my >= y && my <= y + 24;
            if (sel) {
                Draw.round(wx + 8, y, SIDE_W - 16, 24, 8, Draw.mul((accent & 0xFFFFFF) | 0x33000000));
                Draw.round(wx + 8, y + 6, 3, 12, 1.5f, accent);
            } else if (hover) {
                Draw.round(wx + 8, y, SIDE_W - 16, 24, 8, Draw.mul(0x22FFFFFF));
            }
            fontRendererObj.drawString(c.label, wx + 20, y + 8, Draw.mul(sel ? accent : MUTED));
            y += 28;
        }

        // search bar
        int sx = cx(), sw = cw();
        Draw.round(sx, wy + 12, sw, 22, 11, Draw.mul(CARD));
        String shown = search.isEmpty() ? "Search modules..." : search + (System.currentTimeMillis() / 500 % 2 == 0 ? "_" : "");
        fontRendererObj.drawString(shown, sx + 10, wy + 19, Draw.mul(search.isEmpty() ? MUTED : TEXT));

        // module list (clipped)
        List<Row> rows = rows();
        float maxScroll = Math.max(0, contentHeight(rows) - ch());
        scroll = Math.max(0, Math.min(maxScroll, scroll));
        ScaledResolution sr = new ScaledResolution(mc);
        int f = sr.getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        float ssc = sc;
        // scissor ignores the matrix, so convert the (scaled) window rect by hand
        float ox = width / 2f + (cx() - width / 2f) * ssc;
        float oy = height / 2f + (cy() - height / 2f) * ssc;
        GL11.glScissor((int) (ox * f), (int) ((height - (oy + ch() * ssc)) * f), (int) (cw() * ssc * f),
                (int) (ch() * ssc * f));
        for (Row r : rows) {
            float ry = cy() + r.y - scroll;
            if (ry + r.h < cy() || ry > cy() + ch()) {
                continue;
            }
            if (r.s == null) {
                drawModule(r.m, cx(), ry, cw(), mx, my, accent);
            } else {
                drawSetting(r.s, cx() + 8, ry, cw() - 16, mx, my, accent);
            }
        }
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        if (rows.isEmpty()) {
            fontRendererObj.drawString("No modules found", cx() + 10, cy() + 10, Draw.mul(MUTED));
        }
        GlStateManager.popMatrix();
        Draw.alpha = 1f;
    }

    private void drawModule(Module m, float x, float y, float w, int mx, int my, int accent) {
        boolean hover = mx >= x && mx <= x + w && my >= y && my <= y + ROW_MOD;
        Draw.round(x, y, w, ROW_MOD, 8, Draw.mul(hover ? CARD_HOT : CARD));
        fontRendererObj.drawString(m.name, (int) x + 10, (int) y + 6, Draw.mul(TEXT));
        String d = m.description;
        while (fontRendererObj.getStringWidth(d) > w - 80 && d.length() > 4) {
            d = d.substring(0, d.length() - 4) + "...";
        }
        fontRendererObj.drawString(d, (int) x + 10, (int) y + 17, Draw.mul(MUTED));
        if (!m.alwaysOn) {
            float target = m.isEnabled() ? 1f : 0f;
            m.anim += (target - m.anim) * (style.animations.on() ? 0.25f : 1f);
            drawSwitch(x + w - 38, y + 7, m.anim, accent);
        } else {
            fontRendererObj.drawString(">", (int) (x + w - 14), (int) y + 10, Draw.mul(MUTED));
        }
    }

    private void drawSwitch(float x, float y, float t, int accent) {
        int col = blend(OFF, accent, t);
        Draw.round(x, y, 28, 14, 7, Draw.mul(col));
        Draw.round(x + 2 + 14 * t, y + 2, 10, 10, 5, Draw.mul(0xFFFFFFFF));
    }

    private static int blend(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private void drawSetting(Setting s, float x, float y, float w, int mx, int my, int accent) {
        Draw.round(x, y, w, ROW_SET, 6, Draw.mul(0xFF1C1C25));
        fontRendererObj.drawString(s.name, (int) x + 8, (int) y + 7, Draw.mul(TEXT));
        switch (s.type) {
            case BOOL:
                drawSwitch(x + w - 36, y + 4, s.bool ? 1f : 0f, accent);
                break;
            case MODE: {
                String v = s.display();
                fontRendererObj.drawString(v, (int) (x + w - 8 - fontRendererObj.getStringWidth(v)), (int) y + 7,
                        Draw.mul(accent));
                break;
            }
            default: {
                float tw = Math.min(110, w * 0.42f);
                float tx = x + w - tw - 40;
                float frac = (float) ((s.value - s.min) / (s.max - s.min));
                Draw.round(tx, y + 9, tw, 4, 2, Draw.mul(OFF));
                Draw.round(tx, y + 9, Math.max(4, tw * frac), 4, 2, Draw.mul(accent));
                Draw.round(tx + tw * frac - 4, y + 7, 8, 8, 4, Draw.mul(0xFFFFFFFF));
                String v = s.display();
                fontRendererObj.drawString(v, (int) (x + w - 8 - fontRendererObj.getStringWidth(v)), (int) y + 7,
                        Draw.mul(MUTED));
            }
        }
    }

    /** Slider track geometry for a numeric setting row at screen position x,w. */
    private float[] track(float x, float w) {
        float tw = Math.min(110, w * 0.42f);
        return new float[] { x + w - tw - 40, tw };
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) throws IOException {
        // map the click through the open-animation scale (only differs while opening)
        float sc = 0.92f + 0.08f * openAnim;
        mx = (int) ((mx - width / 2f) / sc + width / 2f);
        my = (int) ((my - height / 2f) / sc + height / 2f);

        int y = wy + 40;
        for (Category c : Category.values()) {
            if (mx >= wx + 8 && mx <= wx + SIDE_W - 8 && my >= y && my <= y + 24) {
                selected = c;
                search = "";
                scroll = 0;
                return;
            }
            y += 28;
        }
        if (mx < cx() || mx > cx() + cw() || my < cy() || my > cy() + ch()) {
            return;
        }
        for (Row r : rows()) {
            float ry = cy() + r.y - scroll;
            if (my < ry || my > ry + r.h) {
                continue;
            }
            if (r.s == null) {
                if (!r.m.alwaysOn && mx >= cx() + cw() - 42 && button == 0) {
                    r.m.toggle();
                    Config.save();
                } else {
                    expanded = expanded == r.m ? null : r.m;
                }
            } else {
                clickSetting(r.s, cx() + 8, cw() - 16, mx);
            }
            return;
        }
    }

    private void clickSetting(Setting s, float x, float w, int mx) {
        switch (s.type) {
            case BOOL:
                s.bool = !s.bool;
                Config.save();
                break;
            case MODE:
                s.cycle();
                Config.save();
                break;
            default: {
                float[] t = track(x, w);
                dragging = s;
                dragX = t[0];
                dragW = t[1];
                slide(mx);
            }
        }
    }

    private void slide(int mx) {
        if (dragging != null) {
            float sc = 0.92f + 0.08f * openAnim;
            float m = (mx - width / 2f) / sc + width / 2f;
            double frac = Math.max(0, Math.min(1, (m - dragX) / dragW));
            dragging.setNum(dragging.min + frac * (dragging.max - dragging.min));
        }
    }

    @Override
    protected void mouseClickMove(int mx, int my, int button, long time) {
        slide(mx);
    }

    @Override
    protected void mouseReleased(int mx, int my, int state) {
        if (dragging != null) {
            dragging = null;
            Config.save();
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int d = Mouse.getEventDWheel();
        if (d != 0) {
            scroll -= Math.signum(d) * 24;
        }
    }

    @Override
    protected void keyTyped(char c, int key) throws IOException {
        if (key == Keyboard.KEY_ESCAPE || key == Keyboard.KEY_RSHIFT) {
            mc.displayGuiScreen(null);
            return;
        }
        if (key == Keyboard.KEY_BACK) {
            if (!search.isEmpty()) {
                search = search.substring(0, search.length() - 1);
                scroll = 0;
            }
        } else if (c >= 32 && c != 127 && search.length() < 24) {
            search += c;
            scroll = 0;
        }
    }
}
