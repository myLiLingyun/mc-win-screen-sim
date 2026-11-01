package com.endpoem.screen;

import com.endpoem.App;
import com.endpoem.ObfuscatedMode;
import com.endpoem.PoemLanguage;
import com.endpoem.Settings;
import com.endpoem.font.Font;
import com.endpoem.font.TextBatch;
import com.endpoem.gl.Gfx;
import org.lwjgl.glfw.GLFW;

import java.util.EnumSet;
import java.util.List;


import static org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_TAB;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_V;
import static org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.GLFW_REPEAT;

/** Launch screen: player name, languages, GUI scale and music. */
public final class SetupScreen implements Screen {
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GRAY = 0xFFAAAAAA;
    private static final int DARK_GRAY = 0xFF777777;
    private static final int YELLOW = 0xFFFFFF55;
    private static final int RED = 0xFFFF5555;
    private static final int CYAN = 0xFF55FFFF;

    private final App app;
    private final Font font;
    private final StringBuilder name = new StringBuilder();
    private int scaleOption;
    private final EnumSet<PoemLanguage> languages = EnumSet.noneOf(PoemLanguage.class);
    private final List<PoemLanguage> availableLanguages;
    private boolean music = true;
    private ObfuscatedMode obfuscatedMode = ObfuscatedMode.VANILLA;
    private boolean focusName = true;
    private String message = "";
    private double hoverX = -1;
    private double hoverY = -1;
    private double blinkStart = System.nanoTime() / 1e9;

    private Layout layout;
    private final TextBatch batch = new TextBatch();

    public SetupScreen(App app, Settings initial) {
        this.app = app;
        this.font = app.font();
        this.availableLanguages = app.assets().availablePoemLanguages();
        this.scaleOption = initial.scaleOption();
        this.languages.addAll(initial.languages());
        this.languages.retainAll(availableLanguages);
        if (languages.isEmpty() && !availableLanguages.isEmpty()) {
            languages.add(availableLanguages.get(0));
        }
        this.music = initial.music() && app.assets().hasMusic();
        this.obfuscatedMode = initial.obfuscatedMode();
        this.name.append(initial.name());
        app.setScaleOption(scaleOption);
    }

    @Override
    public void update(double dt) {
        clampScale();
    }

    @Override
    public void render() {
        Gfx g = app.gfx();
        int guiW = app.guiW();
        int guiH = app.guiH();
        clampScale();
        layout = computeLayout(guiW, guiH);

        g.drawEndPortal();
        g.drawVignette();
        g.drawRect(0, 0, guiW, guiH, 0x99000000);
        panel(g, layout);
        drawWidgets(g, layout);

        batch.clear();
        drawText(batch, layout);
        font.flush();
        g.drawTextBatch(font.texture(), batch.data(), batch.floatCount());
    }

    private Layout computeLayout(int guiW, int guiH) {
        int w = Math.min(360, Math.max(240, guiW - 16));
        int h = 236;
        int x = (guiW - w) / 2;
        int y = (guiH - h) / 2;
        Rect nameField = new Rect(x + 112, y + 57, w - 128, 16);
        Rect start = new Rect(x + (w - 180) / 2, y + 190, 180, 20);
        Rect scaleLeft = new Rect(x + 112, y + 116, 16, 16);
        Rect scaleRight = new Rect(x + w - 32, y + 116, 16, 16);
        Rect scaleValue = new Rect(x + 128, y + 116, w - 144, 16);
        Rect[] languageBoxes = new Rect[availableLanguages.size()];
        for (int i = 0; i < languageBoxes.length; i++) {
            languageBoxes[i] = new Rect(x + 112 + (i % 3) * 76, y + 80 + (i / 3) * 20, 11, 11);
        }
        Rect musicBox = new Rect(x + 112, y + 140, 11, 11);
        Rect obfLeft = new Rect(x + 112, y + 160, 16, 16);
        Rect obfRight = new Rect(x + w - 32, y + 160, 16, 16);
        Rect obfValue = new Rect(x + 128, y + 160, w - 144, 16);
        return new Layout(x, y, w, h, nameField, start, scaleLeft, scaleRight, scaleValue,
                languageBoxes, musicBox, obfLeft, obfRight, obfValue);
    }

    private void panel(Gfx g, Layout l) {
        g.drawRect(l.x, l.y, l.w, l.h, 0xC8101010);
        g.drawRect(l.x, l.y, l.w, 1, 0x70FFFFFF);
        g.drawRect(l.x, l.y + l.h - 1, l.w, 1, 0x70FFFFFF);
        g.drawRect(l.x, l.y, 1, l.h, 0x70FFFFFF);
        g.drawRect(l.x + l.w - 1, l.y, 1, l.h, 0x70FFFFFF);
    }

    private void drawWidgets(Gfx g, Layout l) {
        boolean nameHover = l.nameField.contains(hoverX, hoverY);
        g.drawRect(l.nameField.x, l.nameField.y, l.nameField.w, l.nameField.h, 0xD0000000);
        int nameBorder = focusName ? 0xFFFFFFAA : (nameHover ? 0xFFDDDDDD : 0xFF777777);
        border(g, l.nameField, nameBorder);

        for (int i = 0; i < l.languageBoxes.length; i++) {
            checkbox(g, l.languageBoxes[i], languages.contains(availableLanguages.get(i)));
        }
        checkbox(g, l.musicBox, music);

        boolean startHover = l.start.contains(hoverX, hoverY);
        g.drawRect(l.start.x, l.start.y, l.start.w, l.start.h, startHover ? 0xE0404040 : 0xD0202020);
        border(g, l.start, startHover ? YELLOW : 0xFFAAAAAA);

        boolean leftHover = l.scaleLeft.contains(hoverX, hoverY);
        boolean rightHover = l.scaleRight.contains(hoverX, hoverY);
        if (leftHover) {
            g.drawRect(l.scaleLeft.x, l.scaleLeft.y, l.scaleLeft.w, l.scaleLeft.h, 0x50FFFFFF);
        }
        if (rightHover) {
            g.drawRect(l.scaleRight.x, l.scaleRight.y, l.scaleRight.w, l.scaleRight.h, 0x50FFFFFF);
        }
        boolean obfLeftHover = l.obfLeft.contains(hoverX, hoverY);
        boolean obfRightHover = l.obfRight.contains(hoverX, hoverY);
        if (obfLeftHover) {
            g.drawRect(l.obfLeft.x, l.obfLeft.y, l.obfLeft.w, l.obfLeft.h, 0x50FFFFFF);
        }
        if (obfRightHover) {
            g.drawRect(l.obfRight.x, l.obfRight.y, l.obfRight.w, l.obfRight.h, 0x50FFFFFF);
        }

        // blinking caret
        double now = System.nanoTime() / 1e9;
        if (focusName && ((now - blinkStart) % 1.0) < 0.55) {
            int caret = l.nameField.x + 3 + font.width(name.toString());
            int max = l.nameField.x + l.nameField.w - 3;
            caret = Math.min(caret, max);
            g.drawRect(caret, l.nameField.y + 4, 1, 8, WHITE);
        }
    }

    private void checkbox(Gfx g, Rect r, boolean checked) {
        boolean hover = r.contains(hoverX, hoverY);
        g.drawRect(r.x, r.y, r.w, r.h, 0xD0000000);
        border(g, r, hover ? YELLOW : 0xFFA0A0A0);
        if (checked) {
            g.drawRect(r.x + 3, r.y + 3, 5, 5, 0xFFFFFFFF);
        }
    }

    private static void border(Gfx g, Rect r, int color) {
        g.drawRect(r.x, r.y, r.w, 1, color);
        g.drawRect(r.x, r.y + r.h - 1, r.w, 1, color);
        g.drawRect(r.x, r.y, 1, r.h, color);
        g.drawRect(r.x + r.w - 1, r.y, 1, r.h, color);
    }

    private void drawText(TextBatch batch, Layout l) {
        int cx = l.x + l.w / 2;
        centered(batch, "============", cx, l.y + 10, DARK_GRAY);
        centered(batch, "End Poem", cx, l.y + 24, WHITE);
        centered(batch, "THE END POEM", cx, l.y + 36, GRAY);
        centered(batch, "Minecraft 26.2 - asset-aware player", cx, l.y + 47, 0xFF808080);

        font.draw("Player name", l.x + 16, l.y + 61, GRAY, true, batch);
        font.draw(name.toString(), l.nameField.x + 3, l.nameField.y + 4, WHITE, true, batch);

        font.draw("Languages", l.x + 16, l.y + 82, GRAY, true, batch);
        for (int i = 0; i < l.languageBoxes.length; i++) {
            PoemLanguage language = availableLanguages.get(i);
            Rect box = l.languageBoxes[i];
            font.draw(language.label(), box.x + 15, box.y + 2,
                    languages.contains(language) ? WHITE : DARK_GRAY, true, batch);
        }

        font.draw("GUI scale", l.x + 16, l.y + 119, GRAY, true, batch);
        font.draw("<", l.scaleLeft.x + 5, l.scaleLeft.y + 4, WHITE, true, batch);
        font.draw(">", l.scaleRight.x + 5, l.scaleRight.y + 4, WHITE, true, batch);
        String scale = scaleOption == 0 ? "auto (" + app.autoScale() + "x)" : scaleOption + "x";
        centeredIn(batch, scale, l.scaleValue);

        font.draw("Music", l.x + 16, l.y + 143, GRAY, true, batch);
        String musicLabel = app.assets().hasMusic() ? "Play credits music" : "Music asset not found";
        font.draw(musicLabel, l.musicBox.x + 15, l.musicBox.y + 2, music ? WHITE : DARK_GRAY, true, batch);

        font.draw("Obfuscation", l.x + 16, l.y + 163, GRAY, true, batch);
        font.draw("<", l.obfLeft.x + 5, l.obfLeft.y + 4, WHITE, true, batch);
        font.draw(">", l.obfRight.x + 5, l.obfRight.y + 4, WHITE, true, batch);
        centeredIn(batch, obfuscatedMode.displayName(), l.obfValue);

        centeredIn(batch, "Start  (Enter)", l.start);
        if (!message.isEmpty()) {
            centered(batch, message, cx, l.y + 216, RED);
        } else {
            centered(batch, "Space/Ctrl: faster | Up: reverse | Esc: back", cx, l.y + 216, GRAY);
        }
    }

    private void centered(TextBatch batch, String text, int centerX, int y, int color) {
        font.draw(text, centerX - font.width(text) / 2f, y, color, true, batch);
    }

    private void centeredIn(TextBatch batch, String text, Rect r) {
        float x = r.x + (r.w - font.width(text)) / 2f;
        float y = r.y + (r.h - 8) / 2f;
        font.draw(text, x, Math.round(y), WHITE, true, batch);
    }

    // -------------------------------------------------------------------- input

    @Override
    public void mouseMoved(double mx, double my) {
        hoverX = mx;
        hoverY = my;
    }

    @Override
    public void mouseButton(int button, int action, double mx, double my) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || action != GLFW_PRESS) {
            return;
        }
        hoverX = mx;
        hoverY = my;
        Layout l = layout != null ? layout : computeLayout(app.guiW(), app.guiH());
        layout = l;
        if (l.nameField.contains(mx, my)) {
            focusName = true;
            message = "";
            return;
        }
        focusName = false;
        for (int i = 0; i < l.languageBoxes.length; i++) {
            if (l.languageBoxes[i].contains(mx, my)) {
                toggleLanguage(availableLanguages.get(i));
                return;
            }
        }
        if (l.start.contains(mx, my)) {
            start();
        } else if (l.musicBox.contains(mx, my)) {
            if (app.assets().hasMusic()) {
                music = !music;
            }
        } else if (l.scaleLeft.contains(mx, my)) {
            cycleScale(-1);
        } else if (l.scaleRight.contains(mx, my)) {
            cycleScale(1);
        } else if (l.obfLeft.contains(mx, my)) {
            cycleObfuscated(-1);
        } else if (l.obfRight.contains(mx, my)) {
            cycleObfuscated(1);
        }
    }

    private void cycleScale(int direction) {
        int max = app.autoScale();
        if (direction < 0) {
            scaleOption = scaleOption <= 0 ? max : scaleOption - 1;
        } else {
            scaleOption = scaleOption >= max ? 0 : scaleOption + 1;
        }
        if (scaleOption > max) {
            scaleOption = max;
        }
        app.setScaleOption(scaleOption);
    }

    private void toggleLanguage(PoemLanguage language) {
        if (languages.contains(language)) {
            if (languages.size() == 1) {
                message = "Select at least one language";
                return;
            }
            languages.remove(language);
        } else {
            languages.add(language);
        }
        message = "";
    }

    private void cycleObfuscated(int direction) {
        obfuscatedMode = obfuscatedMode.offset(direction);
        message = "";
    }

    private void clampScale() {
        int max = app.autoScale();
        if (scaleOption > max) {
            scaleOption = max;
            app.setScaleOption(scaleOption);
        }
    }

    @Override
    public void key(int key, int action, int mods) {
        if (action != GLFW_PRESS && action != GLFW_REPEAT) {
            return;
        }
        if (key == GLFW_KEY_ESCAPE) {
            app.quit();
            return;
        }
        if (key == GLFW_KEY_ENTER || key == GLFW_KEY_KP_ENTER) {
            start();
            return;
        }
        if (key == GLFW_KEY_TAB) {
            focusName = !focusName;
            return;
        }
        if (focusName) {
            if (key == GLFW_KEY_BACKSPACE) {
                if (name.length() > 0) {
                    int cut = name.offsetByCodePoints(name.length(), -1);
                    name.setLength(cut);
                }
                message = "";
                return;
            }
            if ((mods & GLFW_MOD_CONTROL) != 0 && key == GLFW_KEY_V) {
                String clip = GLFW.glfwGetClipboardString(app.window());
                if (clip != null) {
                    String clean = clip.replace("\r", "").replace("\n", "");
                    for (int i = 0; i < clean.codePointCount(0, clean.length()) && codePointCount(name) < 16; i++) {
                        int cp = clean.codePointAt(clean.offsetByCodePoints(0, i));
                        if (cp >= 32 && cp != 167) {
                            name.appendCodePoint(cp);
                        }
                    }
                }
                return;
            }
        }
        if (key == GLFW_KEY_LEFT) {
            cycleScale(-1);
        } else if (key == GLFW_KEY_RIGHT) {
            cycleScale(1);
        }
    }

    @Override
    public void charTyped(int codepoint) {
        if (!focusName || codepoint < 32 || codepoint == 167 || codepoint == 127) {
            return;
        }
        if (codePointCount(name) >= 16) {
            return;
        }
        name.appendCodePoint(codepoint);
        message = "";
    }

    private static int codePointCount(CharSequence s) {
        return Character.codePointCount(s, 0, s.length());
    }

    private void start() {
        String n = name.toString().trim();
        if (n.isEmpty()) {
            message = "Enter a player name";
            focusName = true;
            return;
        }
        if (languages.isEmpty()) {
            message = "Select at least one language";
            return;
        }
        message = "";
        app.startPoem(new Settings(n, scaleOption, languages, music, obfuscatedMode));
    }

    @Override
    public boolean showCursor() {
        return true;
    }

    private record Rect(int x, int y, int w, int h) {
        boolean contains(double px, double py) {
            return px >= x && px < x + w && py >= y && py < y + h;
        }
    }

    private record Layout(int x, int y, int w, int h, Rect nameField, Rect start, Rect scaleLeft, Rect scaleRight,
                          Rect scaleValue, Rect[] languageBoxes, Rect musicBox,
                          Rect obfLeft, Rect obfRight, Rect obfValue) {
    }
}
