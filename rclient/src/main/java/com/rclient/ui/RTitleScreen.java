package com.rclient.ui;

import com.rclient.RClient;
import com.rclient.core.Config;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.LanguageOptionsScreen;
import net.minecraft.client.gui.screen.pack.PackScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.text.Text;

import java.util.UUID;

public class RTitleScreen extends Screen {
    private static final String[] LABELS = {"Singleplayer", "Multiplayer", "Mod Menu", "Cosmetics", "Settings", "Resource Packs", "Accounts", "Language", "Exit Game"};
    private final float[] hover = new float[LABELS.length];
    private final long born = System.currentTimeMillis();
    private static SkinTextures skin;
    private static boolean skinRequested;
    private int bx, by, bw, bh, gap;

    public RTitleScreen() { this(false); }
    public RTitleScreen(boolean fromLoading) { super(Text.literal("RClient")); }

    private static MinecraftClient mc() { return MinecraftClient.getInstance(); }

    @Override public boolean shouldCloseOnEsc() { return false; }
    @Override public boolean shouldPause() { return false; }
    @Override public void renderBackground(DrawContext g, int mx, int my, float d) { }

    @Override protected void init() {
        ServerStatus.refresh(false);
        if (!skinRequested) {
            skinRequested = true;
            try {
                MinecraftClient mc = mc();
                GameProfile gp = mc.getGameProfile();
                mc.getSkinProvider().fetchSkinTextures(gp).thenAccept(o -> o.ifPresent(s -> skin = s));
            } catch (Throwable t) { RClient.LOG.warn("skin fetch failed", t); }
        }
    }

    @Override public void tick() { ServerStatus.refresh(false); }

    private void layout() {
        bw = Math.min(150, width / 3);
        gap = 3;
        int avail = height - 70;
        bh = Math.max(11, Math.min(20, avail / LABELS.length - gap));
        bx = Math.max(14, width / 12);
        by = 46 + Math.max(0, (avail - (bh + gap) * LABELS.length) / 2);
    }

    private int bt(int i) { return by + i * (bh + gap); }

    @Override public void render(DrawContext g, int mx, int my, float delta) {
        layout();
        float t = Ui.now();
        float fade = Math.min(1f, (System.currentTimeMillis() - born) / 450f);
        Ui.backdrop(g, width, height, t);

        // header
        Ui.round(g, bx, 12, 26, 26, Ui.ACCENT);
        Ui.round(g, bx + 2, 14, 22, 22, 0xFF15121F);
        Ui.scaledText(g, "R", bx + 8, 19, 2f, 0xFFFFFFFF);
        Ui.scaledText(g, "RClient", bx + 32, 14, 1.6f, Ui.TEXT);
        Ui.text(g, "v" + RClient.VERSION + "  •  profile: " + Config.activeProfile, bx + 32, 28, Ui.MUTED);

        for (int i = 0; i < LABELS.length; i++) {
            boolean over = Ui.in(mx, my, bx, bt(i), bw, bh);
            hover[i] = Ui.anim(hover[i], over ? 1f : 0f, 0.25f);
            int off = (int) (hover[i] * 5) + (int) ((1 - fade) * -30 * (1 + i * 0.1f));
            int x = bx + off;
            boolean exit = i == LABELS.length - 1;
            int bg = Ui.lerp(Ui.CARD, exit ? 0xFF3A1D24 : Ui.CARD_HOVER, hover[i]);
            Ui.round(g, x, bt(i), bw, bh, Ui.alpha(bg, 0.5f + 0.5f * fade));
            Ui.rect(g, x, bt(i) + 2, 2, bh - 4, Ui.lerp(0x00000000 | (Ui.ACCENT & 0xFFFFFF), Ui.ACCENT, hover[i]));
            Ui.text(g, LABELS[i], x + 10, bt(i) + (bh - 8) / 2, Ui.lerp(Ui.MUTED, Ui.TEXT, hover[i]));
        }

        drawInfo(g, mx, my, fade);
    }

    private void drawInfo(DrawContext g, int mx, int my, float fade) {
        MinecraftClient mc = mc();
        int cw = Math.min(170, width / 3), ch = 112;
        int cx = width - cw - Math.max(14, width / 14), cy = Math.max(12, (height - ch) / 2);
        Ui.round(g, cx, cy, cw, ch, Ui.alpha(Ui.PANEL, 0.9f * fade));
        Ui.outline(g, cx, cy, cw, ch, 0xFF2A2A3C);
        int face = 40;
        int fx = cx + 12, fy = cy + 12;
        Ui.round(g, fx - 2, fy - 2, face + 4, face + 4, Ui.ACCENT);
        if (skin != null) PlayerSkinDrawer.draw(g, skin, fx, fy, face);
        else { Ui.rect(g, fx, fy, face, face, 0xFF2A2A3C); Ui.center(g, "?", fx + face / 2, fy + face / 2 - 4, Ui.MUTED); }
        String name = mc.getSession().getUsername();
        Ui.text(g, Ui.fit(name, cw - face - 32), fx + face + 8, fy + 6, Ui.TEXT);
        Ui.text(g, "Signed in", fx + face + 8, fy + 20, Ui.GOOD);
        int y = cy + 62;
        Ui.text(g, "Minecraft  " + mc.getGameVersion(), cx + 12, y, Ui.MUTED); y += 12;
        Ui.text(g, "FPS  " + mc.getCurrentFps(), cx + 12, y, Ui.MUTED); y += 12;
        Ui.text(g, "RClient  v" + RClient.VERSION, cx + 12, y, Ui.MUTED); y += 12;
        Ui.text(g, Ui.fit(ServerStatus.text, cw - 24), cx + 12, y, ServerStatus.color);
    }

    @Override public boolean mouseClicked(Click c, boolean doubled) {
        layout();
        if (c.button() != 0) return false;
        for (int i = 0; i < LABELS.length; i++) {
            if (Ui.in(c.x(), c.y(), bx, bt(i), bw, bh)) { act(i); return true; }
        }
        return false;
    }

    private void act(int i) {
        MinecraftClient mc = mc();
        switch (i) {
            case 0 -> mc.setScreen(new SelectWorldScreen(this));
            case 1 -> mc.setScreen(new MultiplayerScreen(this));
            case 2 -> mc.setScreen(new ModMenuScreen(this));
            case 3 -> mc.setScreen(new CosmeticsScreen(this));
            case 4 -> mc.setScreen(new SettingsHubScreen(this));
            case 5 -> mc.setScreen(new PackScreen(mc.getResourcePackManager(), m -> { mc.options.refreshResourcePacks(m); mc.setScreen(this); }, mc.getResourcePackDir(), Text.translatable("resourcePack.title")));
            case 6 -> mc.setScreen(new AccountsScreen(this));
            case 7 -> mc.setScreen(new LanguageOptionsScreen(this, mc.options, mc.getLanguageManager()));
            default -> mc.scheduleStop();
        }
    }
}
