package com.endpoem.gl;

import org.lwjgl.BufferUtils;

import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.nio.IntBuffer;
import java.nio.ByteBuffer;
import java.nio.file.Path;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;

/**
 * A plain RGBA8 texture. Rows are uploaded top-to-bottom, matching Minecraft:
 * v=0 addresses the first (top) row of the PNG.
 */
public final class Texture {
    public final int id;
    public final int width;
    public final int height;

    private Texture(int id, int width, int height) {
        this.id = id;
        this.width = width;
        this.height = height;
    }

    /**
     * Loads the PNG's raw RGBA bytes through stb_image. Java's ImageIO treats
     * 8-bit grayscale PNGs as linear gray and applies a gamma conversion in
     * getRGB(); that made end_portal.png about 2.8x too bright. stb matches
     * Minecraft's raw texture data.
     */
    public static Texture load(Path path, boolean linearFilter, boolean repeat) throws IOException {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            IntBuffer channels = stack.mallocInt(1);
            ByteBuffer pixels = STBImage.stbi_load(path.toString(), w, h, channels, 4);
            if (pixels == null) {
                throw new IOException("cannot read image " + path + ": " + STBImage.stbi_failure_reason());
            }
            int width = w.get(0);
            int height = h.get(0);
            int tex = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, tex);
            glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
            int filter = linearFilter ? GL_LINEAR : GL_NEAREST;
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, filter);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, filter);
            int wrap = repeat ? GL_REPEAT : GL_CLAMP_TO_EDGE;
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, wrap);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, wrap);
            glBindTexture(GL_TEXTURE_2D, 0);
            STBImage.stbi_image_free(pixels);
            return new Texture(tex, width, height);
        }
    }

    public static Texture fromArgb(int w, int h, int[] argb, boolean linearMagnification, boolean repeat, boolean linearMin) {
        int tex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, tex);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        ByteBuffer data = toRgba(argb, w * h);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, data);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, linearMin ? GL_LINEAR : GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, linearMagnification ? GL_LINEAR : GL_NEAREST);
        int wrap = repeat ? GL_REPEAT : GL_CLAMP_TO_EDGE;
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, wrap);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, wrap);
        glBindTexture(GL_TEXTURE_2D, 0);
        return new Texture(tex, w, h);
    }

    public static Texture white() {
        int tex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, tex);
        ByteBuffer data = BufferUtils.createByteBuffer(4);
        data.put((byte) 0xFF).put((byte) 0xFF).put((byte) 0xFF).put((byte) 0xFF).flip();
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, 1, 1, 0, GL_RGBA, GL_UNSIGNED_BYTE, data);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glBindTexture(GL_TEXTURE_2D, 0);
        return new Texture(tex, 1, 1);
    }

    public void uploadArgb(int[] argb) {
        glBindTexture(GL_TEXTURE_2D, id);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        ByteBuffer data = toRgba(argb, width * height);
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, data);
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    public void bind(int unit) {
        glActiveTexture(GL_TEXTURE0 + unit);
        glBindTexture(GL_TEXTURE_2D, id);
    }

    public void delete() {
        glDeleteTextures(id);
    }

    private static ByteBuffer toRgba(int[] argb, int pixels) {
        ByteBuffer buf = BufferUtils.createByteBuffer(pixels * 4);
        for (int i = 0; i < pixels; i++) {
            int c = argb[i];
            buf.put((byte) (c >> 16 & 0xFF));
            buf.put((byte) (c >> 8 & 0xFF));
            buf.put((byte) (c & 0xFF));
            buf.put((byte) (c >>> 24));
        }
        buf.flip();
        return buf;
    }

    public static void unbindAll() {
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, 0);
    }
}
