package dev.robin.cosmetics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

import java.util.Random;

/** Flat (2D pixel-art) animated flames painted around each hand: a thin shell hugging the hand whose texture is transparent where there is no flame. */
public final class FlameFx {
    private static final Identifier ID = Identifier.of("robincosmetics", "flame_hands");
    private static final int FULL_LIGHT = 15728880;
    private static NativeImageBackedTexture tex;
    private static boolean failed;
    private static int tick;
    private static final Random RND = new Random();
    private static Hand left, right;

    private FlameFx() { }

    // ------------------------------------------------------------------ one hand's model (separate instance per hand:
    // the render queue reads the part later, so two hands must not share one set of part values)
    private static final class Hand {
        final ModelPart root, shell; final boolean isRight;

        Hand(boolean isRight) {
            this.isRight = isRight;
            ModelData md = new ModelData();
            md.getRoot().addChild("shell", ModelPartBuilder.create().uv(0, 0).cuboid(-2f, -2.5f, -2f, 4f, 5f, 4f, new Dilation(0.42f)), ModelTransform.NONE);
            root = TexturedModelData.of(md, 16, 9).createModel();
            shell = root.getChild("shell");
        }

        void update(boolean slim) {
            float cx = (isRight ? -1f : 1f) * (slim ? 0.5f : 1f);      // arm centre relative to the shoulder pivot
            shell.setOrigin(cx, 7.9f, 0f);
            shell.xScale = slim ? 0.8f : 1f;                              // the shell is modelled 4 wide; slim arms are 3 wide
        }
    }

    // ------------------------------------------------------------------ texture (16x9 box unwrap of the 4x5x4 shell)
    private static void ensure() {
        if (tex != null || failed) return;
        try {
            tex = new NativeImageBackedTexture("robincosmetics_flame", 16, 9, true);
            MinecraftClient.getInstance().getTextureManager().registerTexture(ID, tex);
            left = new Hand(false); right = new Hand(true);
            paint(); tex.upload();
        } catch (Throwable t) { failed = true; t.printStackTrace(); }
    }

    public static void tick() {
        if ((!Cfg.fireLeft && !Cfg.fireRight) || tex == null || failed) return;
        tick++;
        if (tick % 3 != 0) return;
        try { paint(); tex.upload(); } catch (Throwable t) { failed = true; t.printStackTrace(); }
    }

    private static void paint() {
        NativeImage img = tex.getImage();
        if (img == null) return;
        int base = Fire.baseColour(tick);
        int core = mix(base, 0xFFEEB9, 0.82), light = mix(base, 0xFFD23C, 0.55), rim = mix(base, 0x000000, 0.35);
        for (int y = 0; y < 9; y++) for (int x = 0; x < 16; x++) img.setColorArgb(x, y, 0);        // transparent
        double time = tick * 0.35;
        // the four side faces sit at u = 0,4,8,12 and v = 4..8 (4 wide, 5 tall); row 4 of a face is the bottom (fingers)
        for (int u = 0; u < 16; u++) {
            int fx = u & 3;
            double h = 2.2 + 1.7 * Math.sin(time * 1.5 + u * 1.35) * Math.cos(time * 0.8 + u * 0.7) + (RND.nextInt(3) - 1) * 0.55;
            h = Math.max(1.2, Math.min(5.4, h));
            for (int r = 0; r < 5; r++) {                       // r = rows up from the bottom of the shell
                if (r >= h) break;
                double q = r / h;
                int c = q < 0.28 ? core : q < 0.55 ? light : q < 0.82 ? base : rim;
                if (q >= 0.55 && RND.nextInt(4) == 0) c = light;
                img.setColorArgb(u, 8 - r, 0xFF000000 | c);
            }
            // glowing cream spot on the middle of every face, like the bright centre in a flame glove
            if (fx == 1 || fx == 2) { img.setColorArgb(u, 8, 0xFF000000 | core); img.setColorArgb(u, 7, 0xFF000000 | (RND.nextInt(4) == 0 ? light : core)); }
        }
        // underside of the fist (u 8..11, v 0..3): cream centre, flickering rim
        for (int yy = 0; yy < 4; yy++) for (int xx = 0; xx < 4; xx++) {
            boolean mid = (xx == 1 || xx == 2) && (yy == 1 || yy == 2);
            img.setColorArgb(8 + xx, yy, 0xFF000000 | (mid ? core : RND.nextInt(3) == 0 ? light : base));
        }
    }

    private static int mix(int a, int b, double k) {
        int r = (int) (((a >> 16) & 255) * (1 - k) + ((b >> 16) & 255) * k), g = (int) (((a >> 8) & 255) * (1 - k) + ((b >> 8) & 255) * k),
                bl = (int) ((a & 255) * (1 - k) + (b & 255) * k);
        return (r << 16) | (g << 8) | bl;
    }

    /** Draw the flames of one hand. {@code arm} is the player model's arm part, already posed. Called from both the 3rd-person feature and the 1st-person arm hook. */
    public static void draw(MatrixStack ms, OrderedRenderCommandQueue queue, ModelPart arm, boolean isRight, boolean slim) {
        if (!(isRight ? Cfg.fireRight : Cfg.fireLeft)) return;
        ensure();
        if (failed || tex == null) return;
        Hand h = isRight ? right : left;
        h.update(slim);
        ms.push();
        arm.applyTransform(ms);
        queue.submitModelPart(h.root, ms, RenderLayers.entityTranslucentEmissive(ID), FULL_LIGHT, OverlayTexture.DEFAULT_UV, null);
        ms.pop();
    }
}
