package com.robin.rustlod.client;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;

/** Opaque flat-colour layer without fog (we fade to the sky colour ourselves). Extends RenderLayer for phase access. */
public final class Layers extends RenderLayer {
    private Layers(String n, VertexFormat f, VertexFormat.DrawMode m, int s, boolean a, boolean b, Runnable x, Runnable y) {
        super(n, f, m, s, a, b, x, y);
    }

    public static final RenderLayer LOD = RenderLayer.of("rustlod_terrain", VertexFormats.POSITION_COLOR,
            VertexFormat.DrawMode.QUADS, 1048576,
            RenderLayer.MultiPhaseParameters.builder()
                    .program(POSITION_COLOR_PROGRAM)
                    .depthTest(LEQUAL_DEPTH_TEST)
                    .cull(DISABLE_CULLING)
                    .build(false));
}
