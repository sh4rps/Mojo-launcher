package dev.robin.v1;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.renderer.GlStateManager;

/** Small immediate-mode drawing helpers for the GUI and HUD. */
public final class Draw {
    public static float alpha = 1f;

    private Draw() {
    }

    public static int mul(int argb) {
        int a = (int) (((argb >>> 24) & 255) * alpha);
        if (a < 5) {
            a = 5;
        }
        return (a << 24) | (argb & 0xFFFFFF);
    }

    private static void begin(int argb) {
        argb = mul(argb);
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GL11.glColor4f(((argb >> 16) & 255) / 255f, ((argb >> 8) & 255) / 255f, (argb & 255) / 255f,
                ((argb >>> 24) & 255) / 255f);
    }

    private static void end() {
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    public static void rect(float x, float y, float w, float h, int argb) {
        begin(argb);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x, y);
        GL11.glVertex2f(x, y + h);
        GL11.glVertex2f(x + w, y + h);
        GL11.glVertex2f(x + w, y);
        GL11.glEnd();
        end();
    }

    public static void round(float x, float y, float w, float h, float r, int argb) {
        r = Math.min(r, Math.min(w, h) / 2f);
        begin(argb);
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex2f(x + w / 2f, y + h / 2f);
        float[][] corners = { { x + r, y + r, 180 }, { x + w - r, y + r, 270 }, { x + w - r, y + h - r, 0 },
                { x + r, y + h - r, 90 } };
        for (float[] c : corners) {
            for (int i = 0; i <= 6; i++) {
                double a = Math.toRadians(c[2] + i * 15.0);
                GL11.glVertex2d(c[0] + Math.cos(a) * r, c[1] + Math.sin(a) * r);
            }
        }
        double a = Math.toRadians(180);
        GL11.glVertex2d(x + r + Math.cos(a) * r, y + r + Math.sin(a) * r);
        GL11.glEnd();
        end();
    }

    public static int rgb(int r, int g, int b, int a) {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
