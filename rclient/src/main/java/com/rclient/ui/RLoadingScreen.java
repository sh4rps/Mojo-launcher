package com.rclient.ui;

import com.rclient.RClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** Startup screen. Progress reflects the real init steps in RClient.runStep. */
public class RLoadingScreen extends Screen {
    private final long start = System.currentTimeMillis();
    private int step = 0;
    private float shown = 0f;
    private long doneAt = 0;
    private String label = "Starting RClient";

    public RLoadingScreen() { super(Text.literal("RClient")); }

    @Override public boolean shouldCloseOnEsc() { return false; }
    @Override public boolean shouldPause() { return false; }
    @Override public void renderBackground(DrawContext g, int mx, int my, float d) { }

    @Override public void tick() {
        if (step < RClient.STEPS.length) {
            long minTime = (long) (step + 1) * 260;
            if (System.currentTimeMillis() - start >= minTime || RClient.ready) {
                label = RClient.STEPS[step];
                try { RClient.runStep(step); } catch (Throwable t) { RClient.LOG.error("init step {} failed", step, t); }
                step++;
            }
        } else if (doneAt == 0) {
            doneAt = System.currentTimeMillis();
            label = "Welcome";
        } else if (System.currentTimeMillis() - doneAt > 550 && shown > 0.99f) {
            mc().setScreen(new RTitleScreen(true));
        }
    }

    private static net.minecraft.client.MinecraftClient mc() { return net.minecraft.client.MinecraftClient.getInstance(); }

    @Override public void render(DrawContext g, int mx, int my, float delta) {
        float t = Ui.now();
        int w = width, h = height;
        Ui.backdrop(g, w, h, t);
        float target = Math.min(1f, step / (float) RClient.STEPS.length);
        shown = Ui.anim(shown, target, 0.12f);

        float intro = Math.min(1f, (System.currentTimeMillis() - start) / 500f);
        int cx = w / 2, cy = h / 2 - 18;
        // logo: rounded square with R, pulsing glow
        float pulse = 0.5f + 0.5f * (float) Math.sin(t * 2.4);
        for (int i = 4; i >= 1; i--) Ui.round(g, cx - 22 - i * 3, cy - 22 - i * 3, 44 + i * 6, 44 + i * 6, Ui.alpha(Ui.ACCENT, (0.03f + 0.03f * pulse) * intro));
        Ui.round(g, cx - 22, cy - 22, 44, 44, Ui.lerp(Ui.ACCENT, Ui.ACCENT2, 0.5f + 0.5f * (float) Math.sin(t)));
        Ui.round(g, cx - 20, cy - 20, 40, 40, 0xFF15121F);
        Ui.scaledText(g, "R", cx - 8, cy - 14, 3f, 0xFFFFFFFF);
        String name = "RClient";
        int nw = (int) (Ui.width(name) * 2.2f);
        Ui.scaledText(g, name, cx - nw / 2, cy + 30, 2.2f, Ui.alpha(Ui.TEXT, intro));
        Ui.center(g, "Minecraft " + mc().getGameVersion() + "  •  Fabric", cx, cy + 52, Ui.MUTED);

        int bw = Math.min(200, w - 60), bx = cx - bw / 2, by = cy + 70;
        Ui.round(g, bx, by, bw, 6, 0xFF1F1F2E);
        int fw = Math.max(0, (int) (bw * shown));
        if (fw > 2) {
            Ui.round(g, bx, by, fw, 6, Ui.ACCENT);
            int sx = bx + (int) ((t * 90) % Math.max(1, fw));
            g.fill(sx, by + 1, Math.min(bx + fw - 1, sx + 14), by + 5, 0x33FFFFFF);
        }
        int dots = (int) (t * 3) % 4;
        Ui.center(g, label + ".".repeat(step < RClient.STEPS.length ? dots : 0), cx, by + 12, Ui.MUTED);
        Ui.text(g, (int) (shown * 100) + "%", bx + bw - Ui.width((int) (shown * 100) + "%"), by - 11, Ui.MUTED);

        // fade out at the end
        if (doneAt > 0) {
            float f = Math.min(1f, (System.currentTimeMillis() - doneAt) / 500f);
            g.fill(0, 0, w, h, Ui.alpha(0xFF000000, f));
        }
    }
}
