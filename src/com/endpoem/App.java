package com.endpoem;

import com.endpoem.audio.Music;
import com.endpoem.font.Font;
import com.endpoem.gl.Gfx;
import com.endpoem.poem.Poem;
import com.endpoem.screen.PoemScreen;
import com.endpoem.screen.Screen;
import com.endpoem.screen.SetupScreen;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.GL_RENDERER;
import static org.lwjgl.opengl.GL11.GL_VERSION;
import static org.lwjgl.opengl.GL11.glGetString;
import static org.lwjgl.system.MemoryUtil.NULL;

public final class App {
    private static final String TITLE = "Minecraft 26.2 · End Poem Player";

    private final Args args;
    private final Assets assets;
    private final Music music = new Music();

    private long window;
    private Gfx gfx;
    private Font font;
    private Screen screen;
    private Settings settings;

    private int fbW;
    private int fbH;
    private int winW;
    private int winH;
    private int guiW = 1;
    private int guiH = 1;
    private int scale = 1;
    private int autoScale = 1;
    private int previewScaleOption = -1;

    private boolean running = true;
    private boolean fullscreen;
    private int savedX;
    private int savedY;
    private int savedW = 1280;
    private int savedH = 720;

    private double lastTime;
    private double startTime;
    private double gameTicks;
    private float pendingScroll = -1;
    private int renderedFrames;

    private final Path screenshotPath;
    private final double screenshotDelay;
    private final double exitAfter;

    public App(Args args) {
        this.args = args;
        this.assets = Assets.resolve(args.get("assets", "assets"));
        this.screenshotPath = args.get("screenshot", null) == null ? null : Path.of(args.get("screenshot", null));
        this.screenshotDelay = args.getFloat("shot-delay", 1.0f);
        this.exitAfter = args.getFloat("exit-after", 0f);
    }

    public void run() {
        if (args.flag("help")) {
            printHelp();
            return;
        }
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) {
            throw new IllegalStateException("GLFW init failed");
        }
        try {
            createWindow();
            GL.createCapabilities();
            System.out.println("GL " + glGetString(GL_VERSION) + " / " + glGetString(GL_RENDERER));
            setIcon();
            if (args.flag("fullscreen")) {
                toggleFullscreen();
            }
            try {
                gfx = new Gfx(assets);
                font = new Font(assets);
            } catch (IOException e) {
                throw new IllegalStateException("cannot load assets", e);
            }

            settings = initialSettings();
            pendingScroll = args.getFloat("scroll", -1f);

            if (args.flag("skip-setup")) {
                startPoem(settings);
            } else {
                setScreen(new SetupScreen(this, settings));
            }
            installCallbacks();
            updateSizes();

            lastTime = glfwGetTime();
            startTime = lastTime;
            boolean shotTaken = false;
            while (running && !glfwWindowShouldClose(window)) {
                glfwPollEvents();
                double now = glfwGetTime();
                double dt = Math.min(now - lastTime, 0.25);
                lastTime = now;
                gameTicks += dt * 20.0;
                updateSizes();
                if (screen != null) {
                    screen.update(dt);
                }
                if (screen != null) {
                    gfx.beginFrame((float) ((gameTicks % 24000.0) / 24000.0));
                    screen.render();
                    renderedFrames++;
                }
                glfwSwapBuffers(window);

                if (screenshotPath != null && !shotTaken && now - startTime >= screenshotDelay && renderedFrames >= 2) {
                    try {
                        gfx.screenshot(screenshotPath);
                        System.out.println("screenshot: " + screenshotPath.toAbsolutePath());
                    } catch (IOException e) {
                        System.err.println("screenshot failed: " + e);
                    }
                    shotTaken = true;
                    if (exitAfter <= 0) {
                        break;
                    }
                }
                if (exitAfter > 0 && now - startTime >= exitAfter) {
                    break;
                }
            }
        } finally {
            music.stop();
            if (gfx != null) {
                gfx.close();
            }
            if (window != NULL) {
                glfwDestroyWindow(window);
            }
            glfwTerminate();
            GLFWErrorCallback cb = glfwSetErrorCallback(null);
            if (cb != null) {
                cb.free();
            }
        }
    }

    private void createWindow() {
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);

        int w = args.getInt("width", 1280);
        int h = args.getInt("height", 720);
        window = glfwCreateWindow(w, h, TITLE, NULL, NULL);
        if (window == NULL) {
            throw new IllegalStateException("cannot create GLFW window");
        }
        glfwSetWindowSizeLimits(window, 480, 320, GLFW_DONT_CARE, GLFW_DONT_CARE);
        glfwMakeContextCurrent(window);
        glfwSwapInterval(args.getBool("vsync", true) ? 1 : 0);
        glfwShowWindow(window);
    }

    private void setIcon() {
        try {
            BufferedImage image = ImageIO.read(assets.texture("pack.png").toFile());
            if (image == null) {
                return;
            }
            int w = image.getWidth();
            int h = image.getHeight();
            int[] pixels = new int[w * h];
            image.getRGB(0, 0, w, h, pixels, 0, w);
            ByteBuffer data = BufferUtils.createByteBuffer(w * h * 4);
            for (int p : pixels) {
                data.put((byte) (p >> 16 & 0xFF));
                data.put((byte) (p >> 8 & 0xFF));
                data.put((byte) (p & 0xFF));
                data.put((byte) (p >>> 24));
            }
            data.flip();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                GLFWImage.Buffer images = GLFWImage.malloc(1, stack);
                images.width(w);
                images.height(h);
                images.pixels(data);
                glfwSetWindowIcon(window, images);
            }
        } catch (Throwable t) {
            System.err.println("icon: " + t.getMessage());
        }
    }

    private Settings initialSettings() {
        String name = sanitizeName(args.get("name", "Steve"));
        String scaleArg = args.get("scale", "auto").toLowerCase(Locale.ROOT);
        int scaleOption = scaleArg.equals("auto") ? 0 : parseScale(scaleArg);
        Set<PoemLanguage> languages = parseLanguages(args.get("lang", "both"));
        languages.retainAll(assets.availablePoemLanguages());
        if (languages.isEmpty()) {
            languages = EnumSet.of(PoemLanguage.ENGLISH);
        }
        boolean sound = !args.flag("mute") && args.getBool("music", true) && assets.hasMusic();
        String obfArg = args.get("obf", args.get("obfuscated-mode", "vanilla"));
        ObfuscatedMode obf = ObfuscatedMode.parse(obfArg);
        return new Settings(name, scaleOption, languages, sound, obf);
    }

    private static Set<PoemLanguage> parseLanguages(String spec) {
        Set<PoemLanguage> defaults = EnumSet.of(PoemLanguage.ENGLISH, PoemLanguage.CHINESE);
        if (spec == null || spec.isBlank()) {
            return defaults;
        }
        String normalized = spec.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        if (normalized.equals("both") || normalized.equals("default")) {
            return defaults;
        }
        if (normalized.equals("all") || normalized.equals("everything")) {
            return EnumSet.allOf(PoemLanguage.class);
        }
        EnumSet<PoemLanguage> result = EnumSet.noneOf(PoemLanguage.class);
        for (String token : normalized.split("[,+\\s]+")) {
            if (token.isBlank()) {
                continue;
            }
            if (token.equals("lzh") || token.equals("wenyan") || token.equals("文言")) {
                result.add(PoemLanguage.LZH_SHANG);
                result.add(PoemLanguage.LZH_WANG);
                result.add(PoemLanguage.LZH_FANG);
                result.add(PoemLanguage.LZH_ICE);
                continue;
            }
            PoemLanguage language = PoemLanguage.parse(token);
            if (language != null) {
                result.add(language);
            }
        }
        return result.isEmpty() ? defaults : result;
    }

    private static String sanitizeName(String input) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < input.length() && out.codePointCount(0, out.length()) < 16; ) {
            int cp = input.codePointAt(i);
            i += Character.charCount(cp);
            if (cp >= 32 && cp != 167 && cp != 127) {
                out.appendCodePoint(cp);
            }
        }
        String name = out.toString().trim();
        return name.isEmpty() ? "Steve" : name;
    }

    private static int parseScale(String s) {
        try {
            return Math.max(1, Integer.parseInt(s.replace("x", "")));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void installCallbacks() {
        glfwSetKeyCallback(window, (win, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_F11 && action == GLFW_PRESS) {
                toggleFullscreen();
                return;
            }
            if (screen != null) {
                screen.key(key, action, mods);
            }
        });
        glfwSetCharCallback(window, (win, codepoint) -> {
            if (screen != null) {
                screen.charTyped(codepoint);
            }
        });
        glfwSetMouseButtonCallback(window, (win, button, action, mods) -> {
            if (screen != null) {
                double[] x = new double[1];
                double[] y = new double[1];
                glfwGetCursorPos(window, x, y);
                screen.mouseButton(button, action, toGuiX(x[0]), toGuiY(y[0]));
            }
        });
        glfwSetCursorPosCallback(window, (win, x, y) -> {
            if (screen != null) {
                screen.mouseMoved(toGuiX(x), toGuiY(y));
            }
        });
        glfwSetScrollCallback(window, (win, dx, dy) -> {
            if (screen != null) {
                screen.scroll(dy);
            }
        });
        glfwSetWindowCloseCallback(window, win -> running = false);
    }

    private double toGuiX(double windowX) {
        int w = Math.max(1, winW);
        return windowX * guiW / w;
    }

    private double toGuiY(double windowY) {
        int h = Math.max(1, winH);
        return windowY * guiH / h;
    }

    private void updateSizes() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            glfwGetFramebufferSize(window, w, h);
            fbW = Math.max(1, w.get(0));
            fbH = Math.max(1, h.get(0));
            glfwGetWindowSize(window, w, h);
            winW = Math.max(1, w.get(0));
            winH = Math.max(1, h.get(0));
        }
        autoScale = calculateScale(fbW, fbH);
        int wanted = previewScaleOption >= 0 ? previewScaleOption : (settings == null ? 0 : settings.scaleOption());
        scale = wanted == 0 ? autoScale : Math.min(wanted, autoScale);
        int newGuiW = (int) Math.ceil(fbW / (double) scale);
        int newGuiH = (int) Math.ceil(fbH / (double) scale);
        guiW = Math.max(1, newGuiW);
        guiH = Math.max(1, newGuiH);
        if (gfx != null) {
            gfx.setViewport(fbW, fbH);
            gfx.setGuiSize(guiW, guiH, (float) (fbW / (double) scale), (float) (fbH / (double) scale), fbW, fbH);
        }
    }

    /** Projection.matches Window.calculateScale(0, false). */
    private static int calculateScale(int fbWidth, int fbHeight) {
        int s = 1;
        while (fbWidth / (s + 1) >= 320 && fbHeight / (s + 1) >= 240 && s < 64) {
            s++;
        }
        return s;
    }

    public void startPoem(Settings s) {
        this.settings = s;
        this.previewScaleOption = -1;
        if (s.music() && assets.hasMusic()) {
            this.music.start(assets.music());
        } else {
            this.music.stop();
        }
        try {
            Poem poem = Poem.load(font, assets, s);
            PoemScreen p = new PoemScreen(this, poem, s);
            if (pendingScroll >= 0) {
                p.setScroll(pendingScroll);
                pendingScroll = -1;
            }
            setScreen(p);
        } catch (IOException e) {
            System.err.println("cannot load poem: " + e);
            setScreen(new SetupScreen(this, s));
        }
    }

    public void finishPoem(Settings s) {
        music.stop();
        setScreen(new SetupScreen(this, s));
    }

    private void setScreen(Screen next) {
        if (screen != null) {
            screen.onHide();
        }
        screen = next;
        screen.onShow();
        glfwSetInputMode(window, GLFW_CURSOR, next.showCursor() ? GLFW_CURSOR_NORMAL : GLFW_CURSOR_HIDDEN);
    }

    private void toggleFullscreen() {
        if (!fullscreen) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer x = stack.mallocInt(1);
                IntBuffer y = stack.mallocInt(1);
                IntBuffer w = stack.mallocInt(1);
                IntBuffer h = stack.mallocInt(1);
                glfwGetWindowPos(window, x, y);
                glfwGetWindowSize(window, w, h);
                savedX = x.get(0);
                savedY = y.get(0);
                savedW = w.get(0);
                savedH = h.get(0);
            }
            long monitor = glfwGetPrimaryMonitor();
            org.lwjgl.glfw.GLFWVidMode mode = glfwGetVideoMode(monitor);
            if (mode != null) {
                glfwSetWindowMonitor(window, monitor, 0, 0, mode.width(), mode.height(), mode.refreshRate());
                fullscreen = true;
            }
        } else {
            glfwSetWindowMonitor(window, NULL, savedX, savedY, savedW, savedH, 0);
            fullscreen = false;
        }
        updateSizes();
    }

    public void quit() {
        if (window != NULL) {
            glfwSetWindowShouldClose(window, true);
        }
        running = false;
    }

    public void printHelp() {
        System.out.println("""
                End Poem player (Minecraft 26.2)
                  --name=<player>        player name (default Steve)
                  --scale=auto|1|2|...   GUI scale (default auto)
                  --lang=both|en|zh|all  languages to show (default both)
                                         also: lzh, lzh-shang, lzh-wang, lzh-fang, lzh-ice
                                         (missing text files are skipped)
                  --obf=vanilla|fixed|same-font
                                         §k width mode (default vanilla)
                  --mute                 no music
                  --assets=<dir>         full user asset tree (default ./assets)
                  --fullscreen           start fullscreen
                  --width/--height       initial window size
                  --skip-setup           jump straight to the poem
                  --scroll=<pixels>      initial scroll offset (debug)
                  --screenshot=<png>     save a screenshot and exit
                  --shot-delay=<seconds> screenshot delay (default 1)
                  --exit-after=<seconds> exit after N seconds
                """);
    }

    public Gfx gfx() {
        return gfx;
    }

    public Font font() {
        return font;
    }

    public Assets assets() {
        return assets;
    }

    public int guiW() {
        return guiW;
    }

    public int guiH() {
        return guiH;
    }

    public int autoScale() {
        return autoScale;
    }

    public int effectiveScale() {
        return scale;
    }

    public long window() {
        return window;
    }

    public Settings lastSettings() {
        return settings;
    }


    public void setScaleOption(int option) {
        this.previewScaleOption = Math.max(0, option);
    }
}
