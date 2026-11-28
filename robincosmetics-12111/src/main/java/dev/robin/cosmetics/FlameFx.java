package dev.robin.cosmetics;

import net.minecraft.client.MinecraftClient;
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

/** Blocky 3D pixel flames wrapped around each hand (a real model, drawn glowing), with a flickering animated texture. */
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
        final ModelPart root, base; final ModelPart[] tongues = new ModelPart[8]; final boolean isRight;

        Hand(boolean isRight) {
            this.isRight = isRight;
            ModelData md = new ModelData();
            ModelPartData r = md.getRoot();
            ModelPartBuilder cube = ModelPartBuilder.create().uv(0, 0).cuboid(-2f, -2f, -2f, 4f, 4f, 4f);
            r.addChild("base", cube, ModelTransform.NONE);
            for (int i = 0; i < tongues.length; i++) r.addChild("t" + i, ModelPartBuilder.create().uv(0, 0).cuboid(-2f, -2f, -2f, 4f, 4f, 4f), ModelTransform.NONE);
            root = TexturedModelData.of(md, 16, 8).createModel();
            base = root.getChild("base");
            for (int i = 0; i < tongues.length; i++) tongues[i] = root.getChild("t" + i);
        }

        void update(boolean slim, int t) {
            double time = t * 0.35;
            float cx = (isRight ? -1f : 1f) * (slim ? 0.5f : 1f);          // arm centre relative to the shoulder pivot
            float halfW = slim ? 1.5f : 2f;
            base.setOrigin(cx, 8.4f, 0f);
            base.xScale = (halfW + 0.55f) / 2f; base.zScale = 1.42f; base.yScale = 1.0f + 0.05f * (float) Math.sin(time * 1.3);
            for (int i = 0; i < tongues.length; i++) {
                double a = i * (Math.PI * 2 / tongues.length) + 0.4;
                float px = cx + (float) Math.cos(a) * (halfW + 0.55f), pz = (float) Math.sin(a) * 2.55f;
                double fl = 0.5 + 0.5 * Math.sin(time * 1.4 + i * 1.9) * Math.cos(time * 0.7 + i);
                float h = 0.38f + 0.42f * (float) fl;                      // tongue height factor, flickers
                ModelPart p = tongues[i];
                p.setOrigin(px, 6.4f - h * 1.2f, pz);
                p.xScale = 0.42f; p.zScale = 0.42f; p.yScale = h;
                p.pitch = (float) (Math.sin(a) * 0.25 * fl); p.roll = (float) (-Math.cos(a) * 0.25 * fl); p.yaw = (float) (i * 0.6);
            }
        }
    }

    // ------------------------------------------------------------------ texture (16x8, a 4x4 flame tile repeated on every face)
    private static void ensure() {
        if (tex != null || failed) return;
        try {
            tex = new NativeImageBackedTexture("robincosmetics_flame", 16, 8, true);
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
        int core = mix(base, 0xFFEEB9, 0.82), light = mix(base, 0xFFD23C, 0.55), rim = mix(base, 0x000000, 0.42);
        for (int y = 0; y < 8; y++) for (int x = 0; x < 16; x++) {
            int tx = x & 3, ty = y & 3;
            boolean centre = (tx == 1 || tx == 2) && (ty == 1 || ty == 2);
            boolean corner = (tx == 0 || tx == 3) && (ty == 0 || ty == 3);
            int c;
            if (centre) c = RND.nextInt(5) == 0 ? light : core;
            else if (corner) c = RND.nextInt(3) == 0 ? base : rim;
            else { int r = RND.nextInt(4); c = r == 0 ? light : r == 1 ? rim : base; }
            img.setColorArgb(x, y, 0xFF000000 | c);
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
        h.update(slim, tick);
        ms.push();
        arm.applyTransform(ms);
        queue.submitModelPart(h.root, ms, RenderLayers.entityTranslucentEmissive(ID), FULL_LIGHT, OverlayTexture.DEFAULT_UV, null);
        ms.pop();
    }
}
