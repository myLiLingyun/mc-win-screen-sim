package com.endpoem.screen;

import com.endpoem.App;
import com.endpoem.Settings;
import com.endpoem.font.Font;
import com.endpoem.font.TextBatch;
import com.endpoem.gl.Gfx;
import com.endpoem.poem.Poem;

import java.util.HashSet;
import java.util.Set;

import static org.lwjgl.glfw.GLFW.*;

/** The End Poem itself, ported from WinScreen. */
public final class PoemScreen implements Screen {
    private static final int WHITE = 0xFFFFFFFF;
    private static final float BASE_SPEED = 0.5f;
    private static final float SPEEDUP_FACTOR = 5.0f;
    private static final float SPEEDUP_FACTOR_FAST = 15.0f;

    private final App app;
    private final Font font;
    private final Poem poem;
    private final Settings settings;
    private final Set<Integer> speedupModifiers = new HashSet<>();
    private final TextBatch batch = new TextBatch();

    private float scroll;
    private int direction = 1;
    private boolean speedupActive;
    private boolean finished;

    public PoemScreen(App app, Poem poem, Settings settings) {
        this.app = app;
        this.font = app.font();
        this.font.setObfuscatedMode(settings.obfuscatedMode());
        this.poem = poem;
        this.settings = settings;
    }

    public void setScroll(float scroll) {
        this.scroll = Math.max(0f, scroll);
    }

    public float scroll() {
        return scroll;
    }

    @Override
    public void update(double dt) {
        double step = Math.min(dt, 0.1);
        float speed = BASE_SPEED
                * (speedupActive ? SPEEDUP_FACTOR + speedupModifiers.size() * SPEEDUP_FACTOR_FAST : 1f)
                * direction;
        scroll = Math.max(0f, scroll + (float) (step * 20.0) * speed);
        int guiH = app.guiH();
        if (scroll > poem.totalScrollLength + guiH + guiH + 24) {
            finish();
        }
    }

    @Override
    public void render() {
        Gfx g = app.gfx();
        int guiW = app.guiW();
        int guiH = app.guiH();

        g.drawEndPortal();
        g.drawVignette();

        int logoX = guiW / 2 - 128;
        int logoY = guiH + 50;
        float yOffs = -scroll;
        g.drawLogo(logoX, logoY + yOffs);

        int yPos = logoY + 100;
        batch.clear();
        int last = poem.lines.size() - 1;
        for (int i = 0; i < poem.lines.size(); i++) {
            float rawY = yPos + yOffs;
            float drawY = rawY;
            if (i == last) {
                float diff = rawY - (guiH / 2f - 6f);
                if (diff < 0f) {
                    drawY -= diff;
                }
            }
            if (rawY + 12f + 8f > 0f && rawY < guiH) {
                Poem.Line line = poem.lines.get(i);
                if (!line.empty()) {
                    font.draw(line.chars(), logoX, drawY, WHITE, true, batch);
                }
            }
            yPos += 12;
        }
        font.flush();
        g.drawTextBatch(font.texture(), batch.data(), batch.floatCount());
    }

    @Override
    public void key(int key, int action, int mods) {
        boolean down = action == GLFW_PRESS || action == GLFW_REPEAT;
        boolean up = action == GLFW_RELEASE;

        if (key == GLFW_KEY_ESCAPE && down) {
            finish();
            return;
        }
        if (key == GLFW_KEY_UP) {
            if (down) {
                direction = -1;
            } else if (up) {
                direction = 1;
            }
            return;
        }
        if (key == GLFW_KEY_SPACE) {
            if (down) {
                speedupActive = true;
            } else if (up) {
                speedupActive = false;
            }
            return;
        }
        boolean modifier = key == GLFW_KEY_LEFT_CONTROL || key == GLFW_KEY_RIGHT_CONTROL
                || key == GLFW_KEY_LEFT_SHIFT || key == GLFW_KEY_RIGHT_SHIFT;
        if (modifier) {
            if (down) {
                speedupModifiers.add(key);
            } else if (up) {
                speedupModifiers.remove(key);
            }
        }
    }

    private void finish() {
        if (finished) {
            return;
        }
        finished = true;
        app.finishPoem(settings);
    }

    @Override
    public boolean showCursor() {
        return false;
    }
}
