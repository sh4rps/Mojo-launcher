package com.rclient.ui;

import com.rclient.core.*;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.gui.screen.pack.PackScreen;
import net.minecraft.client.gui.screen.option.SkinOptionsScreen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

public class ModMenuScreen extends RScreen {
    private static final String[] TABS = {"All", "Favorites", "Recent", "PvP", "HUD", "Visual", "Movement", "World", "Utility", "Performance", "Cosmetics", "Profiles", "System"};
    private static int tab = 0;

    private record Region(int x, int y, int w, int h, boolean clip, Consumer<Integer> click, DoubleConsumer drag) { }
    private final List<Region> regions = new ArrayList<>();
    private Region dragging;

    private String search = "";
    private boolean searchFocus = true;
    private Module selected;
    private int scroll, contentH, sbScroll;
    private Object listening;          // Setting or the String "toggle"
    private Setting editing;
    private String newProfile = "";
    private boolean nameFocus;
    private String status = "";
    private long statusAt;
    private final Map<String, Float> anim = new HashMap<>();
    private int ax, ay, aw, ah;         // content area

    public ModMenuScreen(Screen parent) { super("Mod Menu", parent); }

    private void say(String s) { status = s; statusAt = System.currentTimeMillis(); }

    @Override public void removed() { Config.markDirty(); }

    // -------------------------------------------------------------- paint
    @Override protected void paint(DrawContext g, int mx, int my, float t) {
        regions.clear();
        Ui.backdrop(g, width, height, t);
        int m = Math.max(5, width / 40);
        int x0 = m, y0 = m, pw = width - 2 * m, ph = height - 2 * m;
        float o = open;
        Ui.round(g, x0, y0, pw, ph, Ui.alpha(0xFF101018, 0.96f * o));
        Ui.outline(g, x0, y0, pw, ph, 0xFF2A2A3C);

        // header
        Ui.round(g, x0 + 8, y0 + 7, 14, 14, Ui.ACCENT);
        Ui.scaledText(g, "R", x0 + 12, y0 + 10, 1.1f, 0xFFFFFFFF);
        Ui.text(g, "RClient", x0 + 27, y0 + 10, Ui.TEXT);
        Ui.text(g, Config.activeProfile, x0 + 27 + Ui.width("RClient") + 6, y0 + 10, Ui.ACCENT);
        int sx = x0 + pw - 8 - 130;
        Ui.round(g, sx, y0 + 6, 130, 16, 0xFF1B1B27);
        if (searchFocus) Ui.outline(g, sx, y0 + 6, 130, 16, Ui.ACCENT);
        String shown = search.isEmpty() && !searchFocus ? "Search modules..." : search + (searchFocus && (System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "");
        Ui.text(g, Ui.fit(shown, 120), sx + 5, y0 + 10, search.isEmpty() && !searchFocus ? Ui.MUTED : Ui.TEXT);
        region(sx, y0 + 6, 130, 16, false, b -> { searchFocus = true; editing = null; nameFocus = false; selected = selected; }, null);

        // sidebar
        int sbw = Math.min(88, pw / 4), sy = y0 + 28, sbh = ph - 28 - 26;
        int itemH = 13;
        int totalSb = TABS.length * itemH;
        sbScroll = Math.max(0, Math.min(Math.max(0, totalSb - sbh), sbScroll));
        g.enableScissor(x0 + 6, sy, x0 + 6 + sbw, sy + sbh);
        for (int i = 0; i < TABS.length; i++) {
            int iy = sy + i * itemH - sbScroll;
            boolean act = i == tab, over = Ui.in(mx, my, x0 + 6, iy, sbw, itemH - 1) && my >= sy && my < sy + sbh;
            if (act) Ui.round(g, x0 + 6, iy, sbw, itemH - 1, Ui.alpha(Ui.ACCENT, 0.25f));
            else if (over) Ui.round(g, x0 + 6, iy, sbw, itemH - 1, 0xFF1B1B27);
            if (act) Ui.rect(g, x0 + 6, iy + 2, 2, itemH - 5, Ui.ACCENT);
            Ui.text(g, TABS[i], x0 + 14, iy + 3, act ? Ui.TEXT : Ui.MUTED);
            final int idx = i;
            region(x0 + 6, iy, sbw, itemH - 1, false, b -> { if (my >= sy) { tab = idx; selected = null; scroll = 0; listening = null; editing = null; searchFocus = idx <= 10; nameFocus = false; } }, null);
        }
        g.disableScissor();

        // content area
        ax = x0 + sbw + 12; ay = sy; aw = pw - sbw - 20; ah = sbh;
        g.enableScissor(ax, ay, ax + aw, ay + ah);
        String tn = TABS[tab];
        if (selected != null) drawSettings(g, mx, my);
        else if (tn.equals("Profiles")) drawProfiles(g, mx, my);
        else if (tn.equals("System")) drawSystem(g, mx, my);
        else if (tn.equals("Cosmetics")) drawCosmetics(g, mx, my);
        else drawGrid(g, mx, my);
        g.disableScissor();

        // scroll bar
        if (contentH > ah) {
            int bh = Math.max(14, ah * ah / contentH);
            int by = ay + (int) ((ah - bh) * (scroll / (float) (contentH - ah)));
            Ui.round(g, ax + aw - 3, by, 3, bh, 0xFF3A3A4D);
        }

        // footer
        int fy = y0 + ph - 22;
        if (System.currentTimeMillis() - statusAt < 3500) Ui.text(g, Ui.fit(status, pw - 280), x0 + 10, fy + 6, Ui.GOOD);
        String[] fb = {"HUD Editor", "Export", "Import", "Close"};
        int bw = 56, fx = x0 + pw - 8 - (bw + 4) * fb.length + 4;
        for (int i = 0; i < fb.length; i++) {
            int bx = fx + i * (bw + 4);
            Ui.button(g, bx, fy, bw, 16, fb[i], Ui.in(mx, my, bx, fy, bw, 16), i == 0);
            final int k = i;
            region(bx, fy, bw, 16, false, b -> footer(k), null);
        }
    }

    private void footer(int i) {
        long h = mc.getWindow().getHandle();
        switch (i) {
            case 0 -> mc.setScreen(new HudEditorScreen(this));
            case 1 -> { GLFW.glfwSetClipboardString(h, Config.export()); say("Configuration copied to clipboard"); }
            case 2 -> {
                String s = GLFW.glfwGetClipboardString(h);
                if (s != null && Config.importJson(s)) say("Configuration imported into this profile"); else say("Clipboard has no valid RClient config");
            }
            default -> close();
        }
    }

    private void region(int x, int y, int w, int h, boolean clip, Consumer<Integer> click, DoubleConsumer drag) {
        regions.add(new Region(x, y, w, h, clip, click, drag));
    }

    // --------------------------------------------------------------- grid
    private List<Module> filtered() {
        String q = search.toLowerCase(Locale.ROOT).trim();
        List<Module> out = new ArrayList<>();
        String tn = TABS[tab];
        if (tn.equals("Recent")) { for (String id : Config.recents) { Module m = ModuleManager.get(id); if (m != null) out.add(m); } }
        else for (Module m : ModuleManager.all()) {
            if (tn.equals("Favorites") && !Config.favorites.contains(m.id)) continue;
            if (tab >= 3 && tab <= 9 && !m.category.label.equals(tn)) continue;
            out.add(m);
        }
        if (!q.isEmpty()) out.removeIf(m -> !(m.name.toLowerCase(Locale.ROOT).contains(q) || m.desc.toLowerCase(Locale.ROOT).contains(q) || m.category.label.toLowerCase(Locale.ROOT).contains(q)));
        return out;
    }

    private void drawGrid(DrawContext g, int mx, int my) {
        List<Module> list = filtered();
        int gap = 6, minW = 118;
        int cols = Math.max(1, (aw - 6 + gap) / (minW + gap));
        int cw = (aw - 8 - (cols - 1) * gap) / cols, chh = 46;
        int rows = (list.size() + cols - 1) / cols;
        contentH = rows * (chh + gap);
        scroll = Math.max(0, Math.min(Math.max(0, contentH - ah), scroll));
        if (list.isEmpty()) {
            Ui.text(g, TABS[tab].equals("Favorites") ? "No favorites yet - click the star on a module." : TABS[tab].equals("Recent") ? "Nothing used yet." : "No modules match.", ax + 4, ay + 6, Ui.MUTED);
            return;
        }
        for (int i = 0; i < list.size(); i++) {
            Module md = list.get(i);
            int cx = ax + (i % cols) * (cw + gap), cy = ay + (i / cols) * (chh + gap) - scroll;
            if (cy + chh < ay || cy > ay + ah) continue;
            boolean over = Ui.in(mx, my, cx, cy, cw, chh) && Ui.in(mx, my, ax, ay, aw, ah);
            Ui.round(g, cx, cy, cw, chh, over ? Ui.CARD_HOVER : Ui.CARD);
            if (md.isEnabled()) Ui.rect(g, cx + 1, cy + 4, 2, chh - 8, Ui.ACCENT);
            Ui.text(g, Ui.fit(md.name, cw - 26), cx + 8, cy + 6, Ui.TEXT);
            Ui.text(g, Ui.fit(md.desc, cw - 14), cx + 8, cy + 18, Ui.MUTED);
            // toggle
            float a = anim.getOrDefault(md.id, md.isEnabled() ? 1f : 0f);
            a = Ui.anim(a, md.isEnabled() ? 1f : 0f, 0.3f);
            anim.put(md.id, a);
            Ui.toggle(g, cx + 8, cy + chh - 15, a);
            Ui.text(g, md.category.label, cx + 36, cy + chh - 14, 0xFF6B7080);
            Ui.text(g, "Options >", cx + cw - 8 - Ui.width("Options >"), cy + chh - 14, over ? Ui.ACCENT : Ui.MUTED);
            // favorite star
            boolean fav = Config.favorites.contains(md.id);
            Ui.text(g, "*", cx + cw - 12, cy + 5, fav ? 0xFFFACC15 : 0xFF4A4A5C);
            region(cx + cw - 16, cy + 2, 14, 12, true, b -> { if (fav) Config.favorites.remove(md.id); else Config.favorites.add(md.id); Config.markDirty(); }, null);
            region(cx + 6, cy + chh - 17, 28, 14, true, b -> { md.toggle(); Config.touchRecent(md.id); }, null);
            region(cx, cy, cw, chh, true, b -> { selected = md; scroll = 0; searchFocus = false; Config.touchRecent(md.id); }, null);
        }
    }

    // ----------------------------------------------------------- settings
    private void drawSettings(DrawContext g, int mx, int my) {
        Module md = selected;
        int y = ay - scroll;
        // back + title + toggle
        boolean ob = Ui.in(mx, my, ax, y, 40, 14);
        Ui.button(g, ax, y, 40, 14, "< Back", ob, false);
        region(ax, y, 40, 14, true, b -> { selected = null; scroll = 0; listening = null; editing = null; searchFocus = true; }, null);
        Ui.text(g, md.name, ax + 48, y + 3, Ui.TEXT);
        float a = Ui.anim(anim.getOrDefault(md.id, md.isEnabled() ? 1f : 0f), md.isEnabled() ? 1f : 0f, 0.3f);
        anim.put(md.id, a);
        Ui.toggle(g, ax + aw - 30, y + 2, a);
        region(ax + aw - 32, y, 26, 14, true, b -> { md.toggle(); Config.touchRecent(md.id); }, null);
        y += 18;
        Ui.text(g, Ui.fit(md.desc, aw - 8), ax, y, Ui.MUTED);
        y += 14;

        // toggle key row
        y = rowKey(g, mx, my, y, "Toggle key", md.toggleKey, "toggle", () -> { md.toggleKey = -1; Config.markDirty(); });
        for (Setting s : md.settings) y = row(g, mx, my, y, s);
        if (md instanceof HudModule hm) {
            Ui.button(g, ax, y, 110, 16, "Edit position...", Ui.in(mx, my, ax, y, 110, 16), true);
            region(ax, y, 110, 16, true, b -> { if (!hm.isEnabled()) hm.setEnabled(true); mc.setScreen(new HudEditorScreen(this)); }, null);
            y += 22;
        }
        Ui.button(g, ax, y, 110, 16, "Reset to defaults", Ui.in(mx, my, ax, y, 110, 16), false);
        region(ax, y, 110, 16, true, b -> { for (Setting s : md.settings) s.reset(); md.toggleKey = -1; if (md instanceof HudModule h) { h.fx = h.dfx; h.fy = h.dfy; } Config.markDirty(); say("Reset " + md.name); }, null);
        y += 22;
        contentH = y + scroll - ay;
        scroll = Math.max(0, Math.min(Math.max(0, contentH - ah), scroll));
    }

    private int rowKey(DrawContext g, int mx, int my, int y, String label, int key, Object tag, Runnable clear) {
        int cx = ax + aw - 120;
        Ui.round(g, ax, y, aw - 8, 18, Ui.CARD);
        Ui.text(g, label, ax + 6, y + 5, Ui.TEXT);
        boolean on = listening == tag;
        Ui.button(g, cx, y + 2, 100, 14, on ? "Press a key..." : Keys.name(key), Ui.in(mx, my, cx, y + 2, 100, 14), on);
        region(cx, y + 2, 100, 14, true, b -> { if (b == 1) clear.run(); else { listening = tag; editing = null; } }, null);
        return y + 21;
    }

    private int row(DrawContext g, int mx, int my, int y, Setting s) {
        int cx = ax + aw - 120, rw = 100;
        Ui.round(g, ax, y, aw - 8, 18, Ui.CARD);
        Ui.text(g, Ui.fit(s.label, aw - 140), ax + 6, y + 5, Ui.TEXT);
        switch (s.type) {
            case BOOL -> {
                String id = "s:" + System.identityHashCode(s);
                float a = Ui.anim(anim.getOrDefault(id, s.bool() ? 1f : 0f), s.bool() ? 1f : 0f, 0.3f);
                anim.put(id, a);
                Ui.toggle(g, cx + rw - 22, y + 3, a);
                region(cx, y, rw, 18, true, b -> s.set(!s.bool()), null);
            }
            case NUM -> {
                int tx = cx, tw = rw - 30;
                Ui.round(g, tx, y + 7, tw, 4, 0xFF2A2A3C);
                float f = (float) ((s.num() - s.min) / (s.max - s.min));
                Ui.round(g, tx, y + 7, Math.max(3, (int) (tw * f)), 4, Ui.ACCENT);
                Ui.round(g, tx + (int) (tw * f) - 2, y + 4, 5, 10, 0xFFFFFFFF);
                String v = s.step >= 1 ? String.valueOf((int) s.num()) : String.format("%.2f", s.num());
                Ui.text(g, v, cx + rw - Ui.width(v), y + 5, Ui.MUTED);
                DoubleConsumer d = px -> s.setNum(s.min + (s.max - s.min) * Math.max(0, Math.min(1, (px - tx) / (double) tw)));
                region(tx - 2, y, tw + 4, 18, true, b -> { }, d);
            }
            case ENUM -> {
                Ui.button(g, cx, y + 2, rw, 14, s.str(), Ui.in(mx, my, cx, y + 2, rw, 14), false);
                region(cx, y + 2, rw, 14, true, b -> s.cycle(b == 1 ? -1 : 1), null);
            }
            case COLOR -> {
                int sw = 10;
                for (int i = 0; i < Ui.SWATCHES.length; i++) {
                    int sx = cx - 8 + i * (sw + 1) + 6;
                    int col = Ui.SWATCHES[i];
                    Ui.rect(g, sx, y + 4, sw, 10, col);
                    if (s.color() == col) Ui.outline(g, sx - 1, y + 3, sw + 2, 12, 0xFFFFFFFF);
                    region(sx, y + 3, sw, 12, true, b -> s.set(col), null);
                }
            }
            case KEY -> { return rowKey(g, mx, my, y, s.label, s.key(), s, () -> s.set(-1)); }
            case STRING -> {
                boolean on = editing == s;
                Ui.round(g, cx - 20, y + 2, rw + 20, 14, 0xFF0C0C12);
                if (on) Ui.outline(g, cx - 20, y + 2, rw + 20, 14, Ui.ACCENT);
                String show = s.str() + (on && (System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "");
                int maxW = rw + 14;
                while (Ui.width(show) > maxW && show.length() > 1) show = show.substring(1);
                Ui.text(g, show, cx - 17, y + 5, Ui.TEXT);
                region(cx - 20, y + 2, rw + 20, 14, true, b -> { editing = s; listening = null; searchFocus = false; }, null);
            }
        }
        return y + 21;
    }

    // ------------------------------------------------------------ profiles
    private void drawProfiles(DrawContext g, int mx, int my) {
        int y = ay - scroll;
        Ui.text(g, "Profiles keep their own module states, settings, keybinds and HUD positions.", ax, y, Ui.MUTED);
        y += 14;
        for (String p : Config.profiles()) {
            boolean active = p.equals(Config.activeProfile);
            Ui.round(g, ax, y, aw - 8, 20, active ? Ui.alpha(Ui.ACCENT, 0.2f) : Ui.CARD);
            Ui.text(g, p, ax + 8, y + 6, Ui.TEXT);
            int bx = ax + aw - 8 - 52;
            if (active) Ui.text(g, "Active", bx + 10, y + 6, Ui.GOOD);
            else {
                Ui.button(g, bx, y + 3, 50, 14, "Load", Ui.in(mx, my, bx, y + 3, 50, 14), true);
                region(bx, y + 3, 50, 14, true, b -> { Config.switchProfile(p); say("Loaded profile " + p); }, null);
                Ui.button(g, bx - 54, y + 3, 50, 14, "Delete", Ui.in(mx, my, bx - 54, y + 3, 50, 14), false);
                region(bx - 54, y + 3, 50, 14, true, b -> say(Config.deleteProfile(p) ? "Deleted " + p : "Could not delete " + p), null);
            }
            y += 23;
        }
        y += 6;
        Ui.text(g, "New profile", ax, y, Ui.MUTED); y += 12;
        Ui.round(g, ax, y, 140, 16, 0xFF0C0C12);
        if (nameFocus) Ui.outline(g, ax, y, 140, 16, Ui.ACCENT);
        Ui.text(g, newProfile + (nameFocus && (System.currentTimeMillis() / 500) % 2 == 0 ? "_" : ""), ax + 5, y + 4, Ui.TEXT);
        region(ax, y, 140, 16, true, b -> { nameFocus = true; searchFocus = false; editing = null; }, null);
        Ui.button(g, ax + 146, y, 80, 16, "Copy current", Ui.in(mx, my, ax + 146, y, 80, 16), true);
        region(ax + 146, y, 80, 16, true, b -> { say(Config.createProfile(newProfile, true) ? "Created " + newProfile : "Invalid or existing name"); newProfile = ""; }, null);
        if (aw > 330) {
            Ui.button(g, ax + 230, y, 60, 16, "Blank", Ui.in(mx, my, ax + 230, y, 60, 16), false);
            region(ax + 230, y, 60, 16, true, b -> { say(Config.createProfile(newProfile, false) ? "Created " + newProfile : "Invalid or existing name"); newProfile = ""; }, null);
        }
        y += 22;
        contentH = y + scroll - ay;
    }

    // -------------------------------------------------------------- system
    private void drawSystem(DrawContext g, int mx, int my) {
        int y = ay - scroll;
        Ui.text(g, "Compatibility (optional mods - RClient never bundles them)", ax, y, Ui.MUTED); y += 13;
        for (Compat.Entry e : Compat.scan()) {
            Ui.round(g, ax, y, aw - 8, 20, Ui.CARD);
            Ui.text(g, e.name(), ax + 8, y + 3, Ui.TEXT);
            Ui.text(g, Ui.fit(e.what(), aw - 130), ax + 8, y + 12 - 1, 0xFF6B7080);
            String st = e.loaded() ? "installed " + e.version() : "not installed";
            Ui.text(g, Ui.fit(st, 110), ax + aw - 16 - Math.min(110, Ui.width(st)), y + 6, e.loaded() ? Ui.GOOD : Ui.MUTED);
            y += 23;
        }
        y += 4;
        Ui.text(g, "Install the mods above (Fabric 1.21.11 builds) into your mods folder for", ax, y, Ui.MUTED); y += 10;
        Ui.text(g, "their optimizations. Use the Performance Engine module for presets.", ax, y, Ui.MUTED); y += 16;
        Ui.button(g, ax, y, 120, 16, "Resource Packs", Ui.in(mx, my, ax, y, 120, 16), true);
        region(ax, y, 120, 16, true, b -> mc.setScreen(new PackScreen(mc.getResourcePackManager(), m -> { mc.options.refreshResourcePacks(m); mc.setScreen(this); }, mc.getResourcePackDir(), Text.translatable("resourcePack.title"))), null);
        Ui.button(g, ax + 126, y, 120, 16, "Free memory (GC)", Ui.in(mx, my, ax + 126, y, 120, 16), false);
        region(ax + 126, y, 120, 16, true, b -> { Runtime r = Runtime.getRuntime(); long before = r.totalMemory() - r.freeMemory(); System.gc(); say("Freed ~" + Math.max(0, (before - (r.totalMemory() - r.freeMemory())) >> 20) + " MB"); }, null);
        y += 22;
        Ui.text(g, "Shader packs: install Iris. Modpack/ZIP import: drop files on the", ax, y, Ui.MUTED); y += 10;
        Ui.text(g, "Resource Packs screen (vanilla importer with compatibility labels).", ax, y, Ui.MUTED); y += 12;
        contentH = y + scroll - ay;
    }

    private void drawCosmetics(DrawContext g, int mx, int my) {
        int y = ay;
        String[] l = {"Skin layers, cape and main-hand settings use Minecraft's own", "skin screen (works with your account's real skin and cape).", "", "Custom capes, wings, hats, bandanas, emotes and a 3D model", "preview require a texture server and a model renderer that", "RClient 1.0 does not include, so they are not offered here."};
        for (String s : l) { Ui.text(g, s, ax, y, Ui.MUTED); y += 11; }
        y += 8;
        Ui.button(g, ax, y, 130, 16, "Skin Customization", Ui.in(mx, my, ax, y, 130, 16), true);
        region(ax, y, 130, 16, true, b -> mc.setScreen(new SkinOptionsScreen(this, mc.options)), null);
        contentH = 0;
    }

    // --------------------------------------------------------------- input
    @Override public boolean mouseClicked(Click c, boolean d) {
        for (int i = regions.size() - 1; i >= 0; i--) {
            Region r = regions.get(i);
            if (!Ui.in(c.x(), c.y(), r.x, r.y, r.w, r.h)) continue;
            if (r.clip && !Ui.in(c.x(), c.y(), ax, ay, aw, ah)) continue;
            if (r.drag != null) { dragging = r; r.drag.accept(c.x()); }
            r.click.accept(c.button());
            return true;
        }
        searchFocus = false; editing = null; nameFocus = false; listening = null;
        return false;
    }

    @Override public boolean mouseDragged(Click c, double dx, double dy) {
        if (dragging != null) { dragging.drag.accept(c.x()); return true; }
        return false;
    }

    @Override public boolean mouseReleased(Click c) { dragging = null; return false; }

    @Override public boolean mouseScrolled(double mx, double my, double h, double v) {
        int step = (int) (-v * 14);
        if (mx < ax - 4) sbScroll += step; else scroll += step;
        return true;
    }

    @Override public boolean keyPressed(KeyInput in) {
        int k = in.key();
        if (listening != null) {
            if (k != GLFW.GLFW_KEY_ESCAPE) {
                int v = (k == GLFW.GLFW_KEY_BACKSPACE || k == GLFW.GLFW_KEY_DELETE) ? -1 : k;
                if (listening instanceof Setting s) s.set(v);
                else if (selected != null) { selected.toggleKey = v; Config.markDirty(); }
            }
            listening = null;
            return true;
        }
        if (editing != null) {
            if (k == GLFW.GLFW_KEY_ENTER || k == GLFW.GLFW_KEY_ESCAPE) { editing = null; return true; }
            if (k == GLFW.GLFW_KEY_BACKSPACE && !editing.str().isEmpty()) editing.set(editing.str().substring(0, editing.str().length() - 1));
            return true;
        }
        if (nameFocus) {
            if (k == GLFW.GLFW_KEY_ENTER) { say(Config.createProfile(newProfile, true) ? "Created " + newProfile : "Invalid or existing name"); newProfile = ""; return true; }
            if (k == GLFW.GLFW_KEY_ESCAPE) { nameFocus = false; return true; }
            if (k == GLFW.GLFW_KEY_BACKSPACE && !newProfile.isEmpty()) newProfile = newProfile.substring(0, newProfile.length() - 1);
            return true;
        }
        if (searchFocus && k == GLFW.GLFW_KEY_BACKSPACE) { if (!search.isEmpty()) search = search.substring(0, search.length() - 1); return true; }
        if (searchFocus && k == GLFW.GLFW_KEY_ESCAPE && !search.isEmpty()) { search = ""; return true; }
        if (k == GLFW.GLFW_KEY_ESCAPE) {
            if (selected != null) { selected = null; scroll = 0; return true; }
            close(); return true;
        }
        return super.keyPressed(in);
    }

    @Override public boolean charTyped(CharInput in) {
        String s = in.asString();
        if (s.isEmpty() || s.charAt(0) < 32) return false;
        if (listening != null) return true;
        if (editing != null) { if (editing.str().length() < editing.maxLen) editing.set(editing.str() + s); return true; }
        if (nameFocus) { if (newProfile.length() < 20) newProfile += s; return true; }
        if (searchFocus && search.length() < 24) { search += s; scroll = 0; return true; }
        return false;
    }
}
