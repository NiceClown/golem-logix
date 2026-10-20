package com.golemlogix.client.ghost;

import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * A vertex buffer wrapper: makes everything drawn translucent and tints it (for "ghosts").
 */
public final class GhostVertexConsumer implements VertexConsumer {
    private final VertexConsumer delegate;
    private final float alpha;
    private final float r;
    private final float g;
    private final float b;

    public GhostVertexConsumer(VertexConsumer delegate, float alpha, float r, float g, float b) {
        this.delegate = delegate;
        this.alpha = alpha;
        this.r = r;
        this.g = g;
        this.b = b;
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        delegate.addVertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer setColor(int red, int green, int blue, int alphaIn) {
        delegate.setColor(Math.min(255, (int) (red * r)), Math.min(255, (int) (green * g)), Math.min(255, (int) (blue * b)),
                (int) (alphaIn * alpha));
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        delegate.setUv(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        delegate.setUv1(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        delegate.setUv2(u, v);
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        delegate.setNormal(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer setColor(int argb) {
        return setColor(argb >> 16 & 0xFF, argb >> 8 & 0xFF, argb & 0xFF, argb >>> 24);
    }

    @Override
    public VertexConsumer setLineWidth(float width) {
        delegate.setLineWidth(width);
        return this;
    }
}
