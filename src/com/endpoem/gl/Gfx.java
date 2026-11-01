package com.endpoem.gl;

import com.endpoem.Assets;
import org.lwjgl.BufferUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.file.Path;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL31.GL_UNIFORM_BUFFER;
import static org.lwjgl.opengl.GL31.glBindBufferBase;

/**
 * Rendering backend. All GUI geometry uses Minecraft's own GUI projection
 * (ortho 0..width, height..0, near 1000, far 11000, model-view translated by -11000),
 * so the original shaders can be used unmodified.
 */
public final class Gfx {
    public static final int UBO_PROJECTION = 0;
    public static final int UBO_TRANSFORMS = 1;
    public static final int UBO_GLOBALS = 2;
    public static final int UBO_FOG = 3;

    public enum Blend { NONE, TRANSLUCENT, VIGNETTE }

    private final Shader portalShader;
    private final Shader textShader;
    private final Shader guiShader;

    private final int projectionUbo;
    private final int transformsUbo;
    private final int globalsUbo;
    private final int fogUbo;

    public final Texture endSky;
    public final Texture endPortal;
    public final Texture vignette;
    public final Texture logo;
    public final Texture edition;
    public final Texture white;

    private final int portalVao;
    private final int portalVbo;
    private final int guiVao;
    private final int guiVbo;
    private final int textVao;
    private final int textVbo;

    private final FloatBuffer textScratch = BufferUtils.createFloatBuffer(9 * 6 * 48000);
    private float[] portalVertices = new float[18];

    private int guiW = 1;
    private int guiH = 1;
    private int fbW = 1;
    private int fbH = 1;
    private final ByteBuffer globalsData = ByteBuffer.allocateDirect(56).order(ByteOrder.nativeOrder());
    private float projW = 1;
    private float projH = 1;

    public Gfx(Assets assets) throws IOException {
        portalShader = new Shader(assets, "rendertype_end_portal.vsh", "rendertype_end_portal.fsh",
                "#define PORTAL_LAYERS 15");
        textShader = new Shader(assets, "text.vsh", "text.fsh", "#define IS_GUI");
        guiShader = new Shader(assets, "position_tex_color.vsh", "position_tex_color.fsh");

        endSky = Texture.load(assets.texture("end_sky.png"), false, true);
        endPortal = Texture.load(assets.texture("end_portal.png"), false, true);
        vignette = Texture.load(assets.texture("credits_vignette.png"), true, true);
        logo = Texture.load(assets.texture("minecraft.png"), false, true);
        edition = Texture.load(assets.texture("edition.png"), false, true);
        white = Texture.white();

        projectionUbo = createUbo(64, UBO_PROJECTION);
        transformsUbo = createUbo(160, UBO_TRANSFORMS);
        globalsUbo = createUbo(56, UBO_GLOBALS);
        fogUbo = createUbo(48, UBO_FOG);
        writeTransforms();
        writeFog();

        portalVao = glGenVertexArrays();
        portalVbo = glGenBuffers();
        glBindVertexArray(portalVao);
        glBindBuffer(GL_ARRAY_BUFFER, portalVbo);
        glBufferData(GL_ARRAY_BUFFER, (long) 6 * 3 * 4, GL_STREAM_DRAW);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * 4, 0);
        glBindVertexArray(0);

        guiVao = glGenVertexArrays();
        guiVbo = glGenBuffers();
        glBindVertexArray(guiVao);
        glBindBuffer(GL_ARRAY_BUFFER, guiVbo);
        glBufferData(GL_ARRAY_BUFFER, (long) textScratch.capacity() * 4, GL_STREAM_DRAW);
        setupGuiAttributes();
        glBindVertexArray(0);

        textVao = glGenVertexArrays();
        textVbo = glGenBuffers();
        glBindVertexArray(textVao);
        glBindBuffer(GL_ARRAY_BUFFER, textVbo);
        glBufferData(GL_ARRAY_BUFFER, (long) textScratch.capacity() * 4, GL_STREAM_DRAW);
        setupGuiAttributes();
        glBindVertexArray(0);
    }

    private static void setupGuiAttributes() {
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 9 * 4, 0);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 9 * 4, 3 * 4);
        glEnableVertexAttribArray(2);
        glVertexAttribPointer(2, 4, GL_FLOAT, false, 9 * 4, 5 * 4);
    }

    private int createUbo(int size, int binding) {
        int id = glGenBuffers();
        glBindBuffer(GL_UNIFORM_BUFFER, id);
        glBufferData(GL_UNIFORM_BUFFER, size, GL_DYNAMIC_DRAW);
        glBindBufferBase(GL_UNIFORM_BUFFER, binding, id);
        glBindBuffer(GL_UNIFORM_BUFFER, 0);
        return id;
    }

    private void writeTransforms() {
        ByteBuffer b = ByteBuffer.allocateDirect(160).order(ByteOrder.nativeOrder());
        // ModelViewMat = translate(0, 0, -11000), column major.
        b.putFloat(1).putFloat(0).putFloat(0).putFloat(0);
        b.putFloat(0).putFloat(1).putFloat(0).putFloat(0);
        b.putFloat(0).putFloat(0).putFloat(1).putFloat(0);
        b.putFloat(0).putFloat(0).putFloat(-11000f).putFloat(1);
        // ColorModulator
        b.putFloat(1).putFloat(1).putFloat(1).putFloat(1);
        // ModelOffset + padding
        b.putFloat(0).putFloat(0).putFloat(0).putFloat(0);
        // TextureMat = identity
        b.putFloat(1).putFloat(0).putFloat(0).putFloat(0);
        b.putFloat(0).putFloat(1).putFloat(0).putFloat(0);
        b.putFloat(0).putFloat(0).putFloat(1).putFloat(0);
        b.putFloat(0).putFloat(0).putFloat(0).putFloat(1);
        b.flip();
        glBindBuffer(GL_UNIFORM_BUFFER, transformsUbo);
        glBufferSubData(GL_UNIFORM_BUFFER, 0, b);
        glBindBuffer(GL_UNIFORM_BUFFER, 0);
    }

    private void writeFog() {
        ByteBuffer b = ByteBuffer.allocateDirect(48).order(ByteOrder.nativeOrder());
        for (int i = 0; i < 12; i++) {
            b.putFloat(0f);
        }
        b.flip();
        glBindBuffer(GL_UNIFORM_BUFFER, fogUbo);
        glBufferSubData(GL_UNIFORM_BUFFER, 0, b);
        glBindBuffer(GL_UNIFORM_BUFFER, 0);
    }

    public void setViewport(int framebufferW, int framebufferH) {
        this.fbW = framebufferW;
        this.fbH = framebufferH;
        glViewport(0, 0, framebufferW, framebufferH);
    }

    public void setGuiSize(int guiW, int guiH, float projW, float projH, int fbW, int fbH) {
        if (this.guiW == guiW && this.guiH == guiH && this.projW == projW && this.projH == projH
                && this.fbW == fbW && this.fbH == fbH) {
            return;
        }
        this.guiW = guiW;
        this.guiH = guiH;
        this.projW = projW;
        this.projH = projH;
        this.fbW = fbW;
        this.fbH = fbH;
        writeProjection(projW, projH);
        rebuildPortalQuad(guiW, guiH);
    }

    private void writeProjection(float w, float h) {
        // Matches Projection.setOrtho(0, w, h, 0, 1000, 11000) (OpenGL depth range).
        float[] m = new float[]{
                2f / w, 0, 0, 0,
                0, -2f / h, 0, 0,
                0, 0, -2f / 10000f, 0,
                -1f, 1f, -12000f / 10000f, 1f
        };
        ByteBuffer b = ByteBuffer.allocateDirect(64).order(ByteOrder.nativeOrder());
        for (float v : m) {
            b.putFloat(v);
        }
        b.flip();
        glBindBuffer(GL_UNIFORM_BUFFER, projectionUbo);
        glBufferSubData(GL_UNIFORM_BUFFER, 0, b);
        glBindBuffer(GL_UNIFORM_BUFFER, 0);
    }

    private void rebuildPortalQuad(int w, int h) {
        portalVertices = new float[]{
                0, 0, 0,
                w, 0, 0,
                w, h, 0,
                0, 0, 0,
                w, h, 0,
                0, h, 0
        };
        glBindBuffer(GL_ARRAY_BUFFER, portalVbo);
        glBufferSubData(GL_ARRAY_BUFFER, 0, portalVertices);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    public void beginFrame(float gameTime) {
        glViewport(0, 0, fbW, fbH);
        glClearColor(0f, 0f, 0f, 1f);
        glClear(GL_COLOR_BUFFER_BIT);
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        glDisable(GL_BLEND);
        glBindBufferBase(GL_UNIFORM_BUFFER, UBO_PROJECTION, projectionUbo);
        glBindBufferBase(GL_UNIFORM_BUFFER, UBO_TRANSFORMS, transformsUbo);
        glBindBufferBase(GL_UNIFORM_BUFFER, UBO_GLOBALS, globalsUbo);
        glBindBufferBase(GL_UNIFORM_BUFFER, UBO_FOG, fogUbo);
        writeGlobals(gameTime);
        glActiveTexture(GL_TEXTURE0);
    }

    private void writeGlobals(float gameTime) {
        globalsData.clear();
        globalsData.putInt(0).putInt(0).putInt(0).putInt(0);
        globalsData.putFloat(0).putFloat(0).putFloat(0).putFloat(0);
        globalsData.putFloat(fbW).putFloat(fbH);
        globalsData.putFloat(1f);
        globalsData.putFloat(gameTime);
        globalsData.putInt(0);
        globalsData.putInt(0);
        globalsData.flip();
        glBindBuffer(GL_UNIFORM_BUFFER, globalsUbo);
        glBufferSubData(GL_UNIFORM_BUFFER, 0, globalsData);
        glBindBuffer(GL_UNIFORM_BUFFER, 0);
    }

    private static void blend(Blend mode) {
        if (mode == Blend.NONE) {
            glDisable(GL_BLEND);
            return;
        }
        glEnable(GL_BLEND);
        if (mode == Blend.VIGNETTE) {
            // BlendFunction(ZERO, ONE_MINUS_SRC_COLOR, ZERO, ONE)
            glBlendFuncSeparate(GL_ZERO, GL_ONE_MINUS_SRC_COLOR, GL_ZERO, GL_ONE);
        } else {
            glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
        }
    }

    public void drawEndPortal() {
        blend(Blend.NONE);
        portalShader.use();
        endSky.bind(0);
        endPortal.bind(1);
        glBindVertexArray(portalVao);
        glBindBuffer(GL_ARRAY_BUFFER, portalVbo);
        glBufferSubData(GL_ARRAY_BUFFER, 0, portalVertices);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        glBindVertexArray(0);
        glActiveTexture(GL_TEXTURE0);
    }

    public void drawQuad(Texture texture, float x, float y, float w, float h,
                         float u0, float v0, float u1, float v1, int argb, Blend mode) {
        blend(mode);
        guiShader.use();
        texture.bind(0);
        float[] v = quadVertices(x, y, w, h, u0, v0, u1, v1, argb);
        uploadAndDraw(guiVao, guiVbo, v, v.length);
    }

    public void drawRect(float x, float y, float w, float h, int argb) {
        drawQuad(white, x, y, w, h, 0, 0, 1, 1, argb, Blend.TRANSLUCENT);
    }

    public void drawVignette() {
        drawQuad(vignette, 0, 0, guiW, guiH, 0, 0, 1, 1, 0xFFFFFFFF, Blend.VIGNETTE);
    }

    public void drawLogo(float x, float y) {
        drawQuad(logo, x, y, 256, 44, 0, 0, 1, 44f / 64f, 0xFFFFFFFF, Blend.TRANSLUCENT);
        drawQuad(edition, x + 64, y + 44 - 7, 128, 14, 0, 0, 1, 14f / 16f, 0xFFFFFFFF, Blend.TRANSLUCENT);
    }

    public void drawTextBatch(Texture fontAtlas, float[] data, int floatCount) {
        if (floatCount <= 0) {
            return;
        }
        blend(Blend.TRANSLUCENT);
        textShader.use();
        fontAtlas.bind(0);
        uploadAndDraw(textVao, textVbo, data, floatCount);
    }

    private void uploadAndDraw(int vao, int vbo, float[] data, int floatCount) {
        if (floatCount > textScratch.capacity()) {
            throw new IllegalStateException("GUI vertex scratch too small: " + floatCount);
        }
        textScratch.clear();
        textScratch.put(data, 0, floatCount);
        textScratch.flip();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferSubData(GL_ARRAY_BUFFER, 0, textScratch);
        glDrawArrays(GL_TRIANGLES, 0, floatCount / 9);
        glBindVertexArray(0);
    }

    private static float[] quadVertices(float x, float y, float w, float h,
                                        float u0, float v0, float u1, float v1, int argb) {
        float a = (argb >>> 24 & 0xFF) / 255f;
        float r = (argb >> 16 & 0xFF) / 255f;
        float g = (argb >> 8 & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;
        float x1 = x + w;
        float y1 = y + h;
        return new float[]{
                x, y, 0, u0, v0, r, g, b, a,
                x1, y, 0, u1, v0, r, g, b, a,
                x1, y1, 0, u1, v1, r, g, b, a,
                x, y, 0, u0, v0, r, g, b, a,
                x1, y1, 0, u1, v1, r, g, b, a,
                x, y1, 0, u0, v1, r, g, b, a
        };
    }

    public void screenshot(Path path) throws IOException {
        glFinish();
        int w = fbW;
        int h = fbH;
        ByteBuffer pixels = BufferUtils.createByteBuffer(w * h * 4);
        glReadPixels(0, 0, w, h, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            int src = (h - 1 - y) * w * 4;
            for (int x = 0; x < w; x++) {
                int i = src + x * 4;
                int r = pixels.get(i) & 0xFF;
                int g = pixels.get(i + 1) & 0xFF;
                int b = pixels.get(i + 2) & 0xFF;
                row[x] = 0xFF000000 | r << 16 | g << 8 | b;
            }
            image.setRGB(0, y, w, 1, row, 0, w);
        }
        ImageIO.write(image, "png", path.toFile());
    }

    public void close() {
        portalShader.delete();
        textShader.delete();
        guiShader.delete();
        glDeleteBuffers(projectionUbo);
        glDeleteBuffers(transformsUbo);
        glDeleteBuffers(globalsUbo);
        glDeleteBuffers(fogUbo);
        glDeleteBuffers(portalVbo);
        glDeleteBuffers(guiVbo);
        glDeleteBuffers(textVbo);
        glDeleteVertexArrays(portalVao);
        glDeleteVertexArrays(guiVao);
        glDeleteVertexArrays(textVao);
    }
}
