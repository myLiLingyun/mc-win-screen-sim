package com.endpoem.font;

import java.util.Arrays;

/** Growable interleaved vertex buffer: position(3), uv(2), color(4). */
public final class TextBatch {
    private float[] data = new float[9 * 6 * 1024];
    private int size;

    public void clear() {
        size = 0;
    }

    public int floatCount() {
        return size;
    }

    public float[] data() {
        return data;
    }

    public void quad(float x0, float y0, float x1, float y1,
                     float u0, float v0, float u1, float v1, int argb) {
        float a = (argb >>> 24 & 0xFF) / 255f;
        float r = (argb >> 16 & 0xFF) / 255f;
        float g = (argb >> 8 & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;
        ensure(9 * 6);
        put(x0, y0, u0, v0, r, g, b, a);
        put(x1, y0, u1, v0, r, g, b, a);
        put(x1, y1, u1, v1, r, g, b, a);
        put(x0, y0, u0, v0, r, g, b, a);
        put(x1, y1, u1, v1, r, g, b, a);
        put(x0, y1, u0, v1, r, g, b, a);
    }

    private void put(float x, float y, float u, float v, float r, float g, float b, float a) {
        data[size++] = x;
        data[size++] = y;
        data[size++] = 0f;
        data[size++] = u;
        data[size++] = v;
        data[size++] = r;
        data[size++] = g;
        data[size++] = b;
        data[size++] = a;
    }

    private void ensure(int extra) {
        if (size + extra > data.length) {
            data = Arrays.copyOf(data, Math.max(data.length * 2, size + extra));
        }
    }
}
