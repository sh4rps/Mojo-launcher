package dev.robin.motionblur;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;

import java.nio.ByteBuffer;

/** Exponential frame blending with a tiny GL pass; every piece of GL state it touches is saved and restored. */
final class BlurRenderer {
    private static int prog, vao, sampler, histTex, histFbo, fbA, fbB, locTex, locAlpha, hw, hh;
    private static boolean histValid;

    private static final String VS = "#version 150\nout vec2 uv;\nvoid main(){ vec2 p = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2)); uv = p; gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0); }\n";
    private static final String FS = "#version 150\nuniform sampler2D tex;\nuniform float alpha;\nin vec2 uv;\nout vec4 o;\nvoid main(){ o = vec4(texture(tex, uv).rgb, alpha); }\n";

    static void invalidate() { histValid = false; }

    private static int compile(int type, String src) {
        int s = GL20.glCreateShader(type);
        GL20.glShaderSource(s, src);
        GL20.glCompileShader(s);
        if (GL20.glGetShaderi(s, GL20.GL_COMPILE_STATUS) == 0) throw new IllegalStateException("shader: " + GL20.glGetShaderInfoLog(s));
        return s;
    }

    private static void init() {
        int vs = compile(GL20.GL_VERTEX_SHADER, VS), fs = compile(GL20.GL_FRAGMENT_SHADER, FS);
        prog = GL20.glCreateProgram();
        GL20.glAttachShader(prog, vs); GL20.glAttachShader(prog, fs);
        GL20.glLinkProgram(prog);
        if (GL20.glGetProgrami(prog, GL20.GL_LINK_STATUS) == 0) throw new IllegalStateException("link: " + GL20.glGetProgramInfoLog(prog));
        GL20.glDeleteShader(vs); GL20.glDeleteShader(fs);
        locTex = GL20.glGetUniformLocation(prog, "tex");
        locAlpha = GL20.glGetUniformLocation(prog, "alpha");
        vao = GL30.glGenVertexArrays();
        sampler = GL33.glGenSamplers();
        GL33.glSamplerParameteri(sampler, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL33.glSamplerParameteri(sampler, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL33.glSamplerParameteri(sampler, GL11.GL_TEXTURE_WRAP_S, GL13.GL_CLAMP_TO_EDGE);
        GL33.glSamplerParameteri(sampler, GL11.GL_TEXTURE_WRAP_T, GL13.GL_CLAMP_TO_EDGE);
        histTex = GL11.glGenTextures();
        histFbo = GL30.glGenFramebuffers(); fbA = GL30.glGenFramebuffers(); fbB = GL30.glGenFramebuffers();
    }

    private static void blit(int srcTex, int dstTex, int w, int h) {
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, fbA);
        GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, srcTex, 0);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, fbB);
        GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, dstTex, 0);
        GL30.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        // detach so the game's textures are never kept alive/bound by our FBOs
        GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, 0, 0);
        GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, 0, 0);
    }

    /** keepPer60 = how much of the old image survives one 1/60 s step (0..0.95). */
    static void process(int mainTex, int w, int h, float keepPer60, double dt) {
        if (prog == 0) init();

        // ---- save state
        int oldProg = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int oldVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int oldRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int oldDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int[] vp = new int[4]; GL11.glGetIntegerv(GL11.GL_VIEWPORT, vp);
        boolean oldBlend = GL11.glIsEnabled(GL11.GL_BLEND), oldDepth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                oldCull = GL11.glIsEnabled(GL11.GL_CULL_FACE), oldScissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        int bSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), bDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),
                bSrcA = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), bDstA = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        int oldActive = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int oldTex = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int oldSampler = GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
        try {
            if (w != hw || h != hh) {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, histTex);
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, histFbo);
                GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, histTex, 0);
                hw = w; hh = h; histValid = false;
            }
            if (!histValid) {            // first frame after (re)enabling: just remember it
                blit(mainTex, histTex, w, h);
                histValid = true;
                return;
            }
            double keep = Math.pow(keepPer60, Math.max(0.001, Math.min(0.1, dt)) * 60.0);
            float alpha = (float) Math.max(0.03, Math.min(1.0, 1.0 - keep));

            // history = current * alpha + history * (1 - alpha)
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, histFbo);
            GL11.glViewport(0, 0, w, h);
            GL11.glDisable(GL11.GL_DEPTH_TEST); GL11.glDisable(GL11.GL_CULL_FACE); GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glEnable(GL11.GL_BLEND);
            GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL20.glUseProgram(prog);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, mainTex);
            GL33.glBindSampler(0, sampler);
            GL20.glUniform1i(locTex, 0);
            GL20.glUniform1f(locAlpha, alpha);
            GL30.glBindVertexArray(vao);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);

            // show the blended image instead of the raw one
            blit(histTex, mainTex, w, h);
        } finally {
            // ---- restore state
            GL30.glBindVertexArray(oldVao);
            GL20.glUseProgram(oldProg);
            GL33.glBindSampler(0, oldSampler);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, oldTex);
            GL13.glActiveTexture(oldActive);
            GL14.glBlendFuncSeparate(bSrcRgb, bDstRgb, bSrcA, bDstA);
            if (oldBlend) GL11.glEnable(GL11.GL_BLEND); else GL11.glDisable(GL11.GL_BLEND);
            if (oldDepth) GL11.glEnable(GL11.GL_DEPTH_TEST); else GL11.glDisable(GL11.GL_DEPTH_TEST);
            if (oldCull) GL11.glEnable(GL11.GL_CULL_FACE); else GL11.glDisable(GL11.GL_CULL_FACE);
            if (oldScissor) GL11.glEnable(GL11.GL_SCISSOR_TEST); else GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glViewport(vp[0], vp[1], vp[2], vp[3]);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, oldRead);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, oldDraw);
        }
    }
}
