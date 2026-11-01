package com.endpoem.font;

import com.endpoem.Assets;
import com.endpoem.ObfuscatedMode;
import com.endpoem.gl.Texture;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Minecraft 26.2 font stack: bitmap providers (nonlatin/accented/ascii), the space
 * provider and GNU Unifont (unihex). Glyph metrics, advances and wrapping follow
 * BitmapProvider / UnihexProvider / StringSplitter.
 */
public final class Font {
    public static final int LINE_HEIGHT = 9;
    private static final int ATLAS = 2048;
    private static final int PAD = 2;

    private final Map<Integer, Raw> raw = new HashMap<>();
    private final Map<Integer, String> familyByCodepoint = new HashMap<>();
    private final Map<Integer, Glyph> glyphs = new HashMap<>();
    private final Map<Integer, int[]> byWidth;
    private final Map<String, Map<Integer, int[]>> byFamilyWidth = new HashMap<>();
    private final Map<String, Map<Integer, int[]>> byFamilyExact = new HashMap<>();
    private final int[] atlasPixels = new int[ATLAS * ATLAS];
    private final Texture atlas;
    private final VanillaRandom random = new VanillaRandom();
    private final Glyph missingGlyph;
    private ObfuscatedMode obfuscatedMode = ObfuscatedMode.VANILLA;

    private int nextX = 1;
    private int nextY = 1;
    private int rowHeight;
    private boolean dirty;

    public Font(Assets assets) throws IOException {
        long started = System.nanoTime();
        loadProviderFile(assets.fontFile("default.json"), assets);
        byWidth = buildWidthIndex();
        buildFamilyIndexes();
        atlas = Texture.fromArgb(ATLAS, ATLAS, atlasPixels, false, false, false);
        missingGlyph = makeMissingGlyph();
        dirty = true;
        System.out.printf("font: %d raw glyphs, %d width classes, %.0f ms%n",
                raw.size(), byWidth.size(), (System.nanoTime() - started) / 1e6);
    }

    // ---------------------------------------------------------------- providers

    private void loadProviderFile(Path path, Assets assets) throws IOException {
        JsonObject root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        JsonArray providers = root.getAsJsonArray("providers");
        if (providers == null) {
            return;
        }
        for (JsonElement element : providers) {
            loadProvider(element.getAsJsonObject(), assets);
        }
    }

    private void loadProvider(JsonObject provider, Assets assets) throws IOException {
        if (!passesFilter(provider)) {
            return;
        }
        String type = provider.get("type").getAsString();
        switch (type) {
            case "reference" -> {
                String id = provider.get("id").getAsString();
                if (id.startsWith("minecraft:")) {
                    id = id.substring("minecraft:".length());
                }
                loadProviderFile(assets.fontFile(id + ".json"), assets);
            }
            case "space" -> loadSpace(provider);
            case "bitmap" -> loadBitmap(provider, assets);
            case "unihex" -> loadUnihex(provider, assets);
            default -> System.err.println("font: unsupported provider " + type);
        }
    }

    /** We run with force-unicode and japanese-variants disabled. */
    private static boolean passesFilter(JsonObject provider) {
        if (!provider.has("filter")) {
            return true;
        }
        JsonObject filter = provider.getAsJsonObject("filter");
        if (filter.has("jp") && filter.get("jp").getAsBoolean()) {
            return false;
        }
        return !filter.has("uniform") || !filter.get("uniform").getAsBoolean();
    }

    private void loadSpace(JsonObject provider) {
        JsonObject advances = provider.getAsJsonObject("advances");
        if (advances == null) {
            return;
        }
        for (Map.Entry<String, JsonElement> e : advances.entrySet()) {
            String key = e.getKey();
            if (key.isEmpty()) {
                continue;
            }
            int cp = key.codePointAt(0);
            putRaw(cp, new RawSpace(e.getValue().getAsInt()), "space");
        }
    }

    private void loadBitmap(JsonObject provider, Assets assets) throws IOException {
        String file = provider.get("file").getAsString();
        if (file.startsWith("minecraft:font/")) {
            file = file.substring("minecraft:font/".length());
        } else if (file.startsWith("minecraft:")) {
            file = file.substring("minecraft:".length());
        }
        int height = provider.has("height") ? provider.get("height").getAsInt() : 8;
        int ascent = provider.get("ascent").getAsInt();
        JsonArray rowsJson = provider.getAsJsonArray("chars");
        BufferedImage image = ImageIO.read(assets.fontFile(file).toFile());
        if (image == null) {
            throw new IOException("cannot read font bitmap " + file);
        }
        int imgW = image.getWidth();
        int imgH = image.getHeight();
        int[] pixels = new int[imgW * imgH];
        image.getRGB(0, 0, imgW, imgH, pixels, 0, imgW);

        int rows = rowsJson.size();
        int cols = rowsJson.get(0).getAsString().codePointCount(0, rowsJson.get(0).getAsString().length());
        int glyphW = imgW / cols;
        int glyphH = imgH / rows;
        float pixelScale = height / (float) glyphH;

        for (int gy = 0; gy < rows; gy++) {
            int[] cps = rowsJson.get(gy).getAsString().codePoints().toArray();
            for (int gx = 0; gx < cps.length; gx++) {
                int cp = cps[gx];
                if (cp == 0) {
                    continue;
                }
                int sx = gx * glyphW;
                int sy = gy * glyphH;
                int actual = actualWidth(pixels, imgW, sx, sy, glyphW, glyphH);
                int advance = (int) (0.5 + actual * pixelScale) + 1;
                putRaw(cp, new RawBitmap(pixels, imgW, sx, sy, glyphW, glyphH, pixelScale, ascent, advance), "bitmap");
            }
        }
    }

    private static int actualWidth(int[] pixels, int imgW, int sx, int sy, int glyphW, int glyphH) {
        for (int x = glyphW - 1; x >= 0; x--) {
            for (int y = 0; y < glyphH; y++) {
                if ((pixels[(sy + y) * imgW + sx + x] >>> 24) != 0) {
                    return x + 1;
                }
            }
        }
        return 0;
    }

    private void loadUnihex(JsonObject provider, Assets assets) throws IOException {
        String hexFile = provider.get("hex_file").getAsString();
        if (hexFile.startsWith("minecraft:font/")) {
            hexFile = hexFile.substring("minecraft:font/".length());
        } else if (hexFile.startsWith("minecraft:")) {
            hexFile = hexFile.substring("minecraft:".length());
        }
        List<int[]> overrides = new ArrayList<>(); // from,to,left,right
        if (provider.has("size_overrides")) {
            for (JsonElement e : provider.getAsJsonArray("size_overrides")) {
                JsonObject o = e.getAsJsonObject();
                int from = o.get("from").getAsString().codePointAt(0);
                int to = o.get("to").getAsString().codePointAt(0);
                int left = o.get("left").getAsInt();
                int right = o.get("right").getAsInt();
                overrides.add(new int[]{from, to, left, right});
            }
        }

        Path zip = assets.fontFile(hexFile);
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.getName().endsWith(".hex")) {
                    continue;
                }
                BufferedReader reader = new BufferedReader(new InputStreamReader(zis, StandardCharsets.US_ASCII));
                String line;
                while ((line = reader.readLine()) != null) {
                    int colon = line.indexOf(':');
                    if (colon <= 0) {
                        continue;
                    }
                    int cp;
                    try {
                        cp = Integer.parseInt(line.substring(0, colon), 16);
                    } catch (NumberFormatException ex) {
                        continue;
                    }
                    String bits = line.substring(colon + 1).trim();
                    int digits = bits.length();
                    int bitWidth;
                    int rowDigits;
                    switch (digits) {
                        case 32 -> {
                            bitWidth = 8;
                            rowDigits = 2;
                        }
                        case 64 -> {
                            bitWidth = 16;
                            rowDigits = 4;
                        }
                        case 96 -> {
                            bitWidth = 24;
                            rowDigits = 6;
                        }
                        case 128 -> {
                            bitWidth = 32;
                            rowDigits = 8;
                        }
                        default -> {
                            continue;
                        }
                    }
                    int[] data = new int[16];
                    int mask = 0;
                    for (int y = 0; y < 16; y++) {
                        int v = Integer.parseInt(bits.substring(y * rowDigits, (y + 1) * rowDigits), 16);
                        int value = switch (bitWidth) {
                            case 8 -> v << 24;
                            case 16 -> v << 16;
                            case 24 -> v << 8;
                            default -> v;
                        };
                        data[y] = value;
                        mask |= value;
                    }
                    int left;
                    int right;
                    if (mask == 0) {
                        left = 0;
                        right = bitWidth;
                    } else {
                        left = Integer.numberOfLeadingZeros(mask);
                        right = 32 - Integer.numberOfTrailingZeros(mask) - 1;
                    }
                    for (int[] o : overrides) {
                        if (cp >= o[0] && cp <= o[1]) {
                            left = o[2];
                            right = o[3];
                        }
                    }
                    putRaw(cp, new RawUnihex(data, left, right), "unifont");
                }
            }
        }
    }

    // ------------------------------------------------------------------- atlas

    /**
     * Mirrors FontSet.selectProviders: the pool for a width class contains every
     * codepoint supported by any selected provider, using only the first provider
     * that supplies it (see the putIfAbsent calls in the loaders), and keyed by
     * Mth.ceil(glyph.info().getAdvance(false)).  Vanilla applies its fishy-advance
     * filter when a codepoint is resolved, not while building this pool; none of
     * the 26.2 default providers produce a fishy advance (all are <= 10), so the
     * first-provider glyph stored in {@code raw} is also the glyph vanilla renders.
     */
    private Map<Integer, int[]> buildWidthIndex() {
        Map<Integer, List<Integer>> tmp = new HashMap<>();
        for (Map.Entry<Integer, Raw> e : raw.entrySet()) {
            int width = (int) Math.ceil(rawAdvance(e.getValue()));
            tmp.computeIfAbsent(width, k -> new ArrayList<>()).add(e.getKey());
        }
        Map<Integer, int[]> result = new HashMap<>();
        for (Map.Entry<Integer, List<Integer>> e : tmp.entrySet()) {
            int[] arr = new int[e.getValue().size()];
            for (int i = 0; i < arr.length; i++) {
                arr[i] = e.getValue().get(i);
            }
            result.put(e.getKey(), arr);
        }
        return result;
    }

    private static float rawAdvance(Raw r) {
        if (r instanceof RawBitmap b) {
            return b.advance;
        }
        if (r instanceof RawUnihex u) {
            return (u.right - u.left + 1) / 2f + 1f;
        }
        return ((RawSpace) r).advance;
    }

    private void putRaw(int codepoint, Raw value, String family) {
        if (!raw.containsKey(codepoint)) {
            raw.put(codepoint, value);
            familyByCodepoint.put(codepoint, family);
        }
    }

    /**
     * Builds the two extra pools used by the same-font mode: one keyed by the
     * exact half-pixel advance, one by the usual ceil(advance) class. Both are
     * split by provider family (bitmap = Mojangles, unifont = Unifont), so the
     * random glyph never crosses the two font styles.
     */
    private void buildFamilyIndexes() {
        Map<String, Map<Integer, List<Integer>>> widthTmp = new HashMap<>();
        Map<String, Map<Integer, List<Integer>>> exactTmp = new HashMap<>();
        for (Map.Entry<Integer, Raw> e : raw.entrySet()) {
            String family = familyByCodepoint.getOrDefault(e.getKey(), "bitmap");
            float advance = rawAdvance(e.getValue());
            int width = (int) Math.ceil(advance);
            int exact = Math.round(advance * 2f);
            widthTmp.computeIfAbsent(family, k -> new HashMap<>())
                    .computeIfAbsent(width, k -> new ArrayList<>()).add(e.getKey());
            exactTmp.computeIfAbsent(family, k -> new HashMap<>())
                    .computeIfAbsent(exact, k -> new ArrayList<>()).add(e.getKey());
        }
        freezePools(widthTmp, byFamilyWidth);
        freezePools(exactTmp, byFamilyExact);
    }

    private static void freezePools(Map<String, Map<Integer, List<Integer>>> source,
                                    Map<String, Map<Integer, int[]>> target) {
        for (Map.Entry<String, Map<Integer, List<Integer>>> family : source.entrySet()) {
            Map<Integer, int[]> frozen = new HashMap<>();
            for (Map.Entry<Integer, List<Integer>> pool : family.getValue().entrySet()) {
                int[] values = new int[pool.getValue().size()];
                for (int i = 0; i < values.length; i++) {
                    values[i] = pool.getValue().get(i);
                }
                frozen.put(pool.getKey(), values);
            }
            target.put(family.getKey(), frozen);
        }
    }

    private int[] allocate(int w, int h) {
        if (nextX + w + PAD + 1 > ATLAS) {
            nextY += rowHeight + PAD;
            nextX = 1;
            rowHeight = 0;
        }
        if (nextY + h + PAD + 1 > ATLAS) {
            throw new IllegalStateException("font atlas full");
        }
        int[] pos = {nextX, nextY};
        nextX += w + PAD;
        rowHeight = Math.max(rowHeight, h);
        return pos;
    }

    private void putPixel(int x, int y, int alpha) {
        if (x < 0 || y < 0 || x >= ATLAS || y >= ATLAS) {
            return;
        }
        atlasPixels[y * ATLAS + x] = alpha << 24 | 0xFFFFFF;
    }

    private Glyph bake(int cp, Raw r) {
        if (r instanceof RawSpace space) {
            return new Glyph(space.advance, 1f, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, false);
        }
        int w;
        int h;
        if (r instanceof RawBitmap b) {
            w = b.w;
            h = b.h;
        } else {
            RawUnihex u = (RawUnihex) r;
            w = u.right - u.left + 1;
            h = 16;
        }
        int[] pos = allocate(w, h);
        int ax = pos[0] + 1;
        int ay = pos[1] + 1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int alpha = 0;
                if (r instanceof RawBitmap b) {
                    alpha = b.pixels[(b.sy + y) * b.imageW + b.sx + x] >>> 24;
                } else {
                    RawUnihex u = (RawUnihex) r;
                    int bit = 32 - u.left - 1 - x;
                    alpha = (u.lines[y] >>> bit & 1) != 0 ? 255 : 0;
                }
                if (alpha != 0) {
                    putPixel(ax + x, ay + y, alpha);
                }
            }
        }
        dirty = true;
        float drawW;
        float drawH;
        float top;
        float advance;
        float boldOffset;
        float shadowOffset;
        if (r instanceof RawBitmap b) {
            drawW = w * b.pixelScale;
            drawH = h * b.pixelScale;
            top = 7f - b.ascent;
            advance = b.advance;
            boldOffset = 1f;
            shadowOffset = 1f;
        } else {
            RawUnihex u = (RawUnihex) r;
            drawW = w / 2f;
            drawH = 8f;
            top = 0f;
            advance = w / 2f + 1f;
            boldOffset = 0.5f;
            shadowOffset = 0.5f;
        }
        float nudge = 0.01f;
        float u0 = (ax + nudge) / ATLAS;
        float u1 = (ax + w - nudge) / ATLAS;
        float v0 = (ay + nudge) / ATLAS;
        float v1 = (ay + h - nudge) / ATLAS;
        return new Glyph(advance, boldOffset, shadowOffset, top, drawW, drawH, u0, v0, u1, v1, true);
    }

    private Glyph makeMissingGlyph() {
        int[] pos = allocate(5, 8);
        int ax = pos[0] + 1;
        int ay = pos[1] + 1;
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 5; x++) {
                if (x == 0 || x == 4 || y == 0 || y == 7) {
                    putPixel(ax + x, ay + y, 255);
                }
            }
        }
        dirty = true;
        float nudge = 0.01f;
        return new Glyph(6f, 1f, 1f, 0f, 5f, 8f,
                (ax + nudge) / ATLAS, (ay + nudge) / ATLAS,
                (ax + 5 - nudge) / ATLAS, (ay + 8 - nudge) / ATLAS, true);
    }

    public Glyph glyph(int codepoint) {
        Glyph g = glyphs.get(codepoint);
        if (g != null) {
            return g;
        }
        Raw r = raw.get(codepoint);
        g = r == null ? missingGlyph : bake(codepoint, r);
        glyphs.put(codepoint, g);
        return g;
    }

    public void flush() {
        if (dirty) {
            atlas.uploadArgb(atlasPixels);
            dirty = false;
        }
    }

    public Texture texture() {
        return atlas;
    }

    public void setObfuscatedMode(ObfuscatedMode mode) {
        this.obfuscatedMode = mode == null ? ObfuscatedMode.VANILLA : mode;
    }

    private Glyph randomGlyph(int widthClass) {
        return randomGlyph(byWidth.get(widthClass));
    }

    private Glyph randomGlyph(int[] pool) {
        if (pool == null || pool.length == 0) {
            return null;
        }
        return glyph(pool[random.nextInt(pool.length)]);
    }

    /** Same provider family and exact advance; falls back to the family width class. */
    private Glyph randomGlyphSameFont(int codepoint, Glyph original) {
        String family = familyByCodepoint.get(codepoint);
        if (family == null) {
            return randomGlyph((int) Math.ceil(original.advance));
        }
        Map<Integer, int[]> exactPools = byFamilyExact.get(family);
        Glyph selected = null;
        if (exactPools != null) {
            selected = randomGlyph(exactPools.get(Math.round(original.advance * 2f)));
        }
        if (selected == null) {
            Map<Integer, int[]> widthPools = byFamilyWidth.get(family);
            if (widthPools != null) {
                selected = randomGlyph(widthPools.get((int) Math.ceil(original.advance)));
            }
        }
        return selected != null ? selected : randomGlyph((int) Math.ceil(original.advance));
    }

    // -------------------------------------------------------------- text model

    public List<StyledChar> parse(String text) {
        List<StyledChar> out = new ArrayList<>(text.length());
        CharStyle style = CharStyle.EMPTY;
        int i = 0;
        while (i < text.length()) {
            char ch = text.charAt(i);
            if (ch == 167) { // §
                if (i + 1 >= text.length()) {
                    break;
                }
                char code = Character.toLowerCase(text.charAt(i + 1));
                CharStyle next = style.applyLegacy(code);
                if (next != null) {
                    style = next;
                }
                i += 2;
                continue;
            }
            int cp;
            if (Character.isHighSurrogate(ch)) {
                if (i + 1 < text.length() && Character.isLowSurrogate(text.charAt(i + 1))) {
                    cp = Character.toCodePoint(ch, text.charAt(i + 1));
                    i += 2;
                } else {
                    cp = 0xFFFD;
                    i++;
                }
            } else {
                cp = ch;
                i++;
            }
            out.add(new StyledChar(cp, style));
        }
        return out;
    }

    public float advance(int codepoint, boolean bold) {
        Glyph g = glyph(codepoint);
        return g.advance + (bold ? g.boldOffset : 0f);
    }

    public int width(String text) {
        return width(parse(text));
    }

    public int width(List<StyledChar> text) {
        float w = 0;
        for (StyledChar c : text) {
            w += advance(c.codepoint(), c.style().bold);
        }
        return (int) Math.ceil(w);
    }

    public List<List<StyledChar>> wrap(String text, int maxWidth) {
        return wrap(parse(text), maxWidth);
    }

    /**
     * Port of StringSplitter.LineBreakFinder: break at the last space if possible,
     * otherwise before the character that no longer fits. Newlines and the space at
     * a soft break are consumed.
     */
    public List<List<StyledChar>> wrap(List<StyledChar> chars, int maxWidth) {
        List<List<StyledChar>> lines = new ArrayList<>();
        int size = chars.size();
        int start = 0;
        while (start < size) {
            float width = 0;
            boolean hadNonZero = false;
            int lastSpace = -1;
            int next = start;
            int lineBreak = -1;
            for (int i = start; i < size; i++) {
                StyledChar c = chars.get(i);
                int cp = c.codepoint();
                if (cp == '\n') {
                    lineBreak = i;
                    break;
                }
                if (cp == ' ') {
                    lastSpace = i;
                }
                float cw = advance(cp, c.style().bold);
                width += cw;
                if (hadNonZero && width > maxWidth) {
                    lineBreak = lastSpace != -1 ? lastSpace : i;
                    break;
                }
                hadNonZero |= cw != 0;
                next = i + 1;
            }
            if (lineBreak < 0) {
                lineBreak = next;
            }
            if (lineBreak < start) {
                lineBreak = start;
            }
            lines.add(new ArrayList<>(chars.subList(start, lineBreak)));
            int old = start;
            if (lineBreak < size) {
                int cp = chars.get(lineBreak).codepoint();
                start = (cp == ' ' || cp == '\n') ? lineBreak + 1 : lineBreak;
            } else {
                start = lineBreak;
            }
            if (start == old) {
                start++;
            }
        }
        return lines;
    }

    // ---------------------------------------------------------------- rendering

    public void draw(String text, float x, float y, int color, boolean shadow, TextBatch batch) {
        draw(parse(text), x, y, color, shadow, batch);
    }

    public void draw(List<StyledChar> text, float x, float y, int color, boolean shadow, TextBatch batch) {
        int alpha = color >>> 24;
        for (StyledChar c : text) {
            Glyph original = glyph(c.codepoint());
            Glyph drawn = original;
            boolean bold = c.style().bold;
            float advance = original.advance + (bold ? original.boldOffset : 0f);
            if (c.style().obfuscated && c.codepoint() != 32) {
                Glyph random = switch (obfuscatedMode) {
                    case FIXED, VANILLA -> randomGlyph((int) Math.ceil(original.advance));
                    case SAME_FONT -> randomGlyphSameFont(c.codepoint(), original);
                };
                if (random != null) {
                    drawn = random;
                    if (obfuscatedMode != ObfuscatedMode.FIXED) {
                        advance = random.advance + (bold ? random.boldOffset : 0f);
                    }
                }
            }
            int argb = c.style().color != null ? (alpha << 24) | c.style().color : color;
            int shadowColor = shadow ? scaleRgb(argb, 0.25f) : 0;
            drawGlyph(batch, drawn, x, y, argb, shadowColor, bold);
            x += advance;
        }
    }

    private static void drawGlyph(TextBatch batch, Glyph g, float x, float y, int color, int shadowColor, boolean bold) {
        if (!g.visible) {
            return;
        }
        // BakedSheetGlyph.extraThickness: a bold pass inflates the quad by 0.1 on
        // every side, in addition to the second, offset pass.
        float extra = bold ? 0.1f : 0f;
        if (shadowColor != 0) {
            drawGlyphQuad(batch, g, x + g.shadowOffset, y + g.shadowOffset, shadowColor, extra);
            if (bold) {
                drawGlyphQuad(batch, g, x + g.boldOffset + g.shadowOffset, y + g.shadowOffset, shadowColor, extra);
            }
        }
        drawGlyphQuad(batch, g, x, y, color, extra);
        if (bold) {
            drawGlyphQuad(batch, g, x + g.boldOffset, y, color, extra);
        }
    }

    private static void drawGlyphQuad(TextBatch batch, Glyph g, float x, float y, int color, float extra) {
        batch.quad(x - extra, y + g.top - extra, x + g.width + extra, y + g.top + g.height + extra,
                g.u0, g.v0, g.u1, g.v1, color);
    }

    private static int scaleRgb(int argb, float factor) {
        int a = argb >>> 24;
        int r = (int) ((argb >> 16 & 0xFF) * factor);
        int g = (int) ((argb >> 8 & 0xFF) * factor);
        int b = (int) ((argb & 0xFF) * factor);
        return a << 24 | r << 16 | g << 8 | b;
    }

    /**
     * Exact equivalent of Minecraft's LegacyRandomSource.next(int)/nextInt(int)
     * seeded through RandomSupport.generateUniqueSeed(). The seed is still
     * time-based, so two processes cannot show identical frames, but the RNG
     * algorithm, seed source and glyph distribution match vanilla.
     */
    private static final class VanillaRandom {
        private static final long MASK = 281474976710655L;
        private static final long MULTIPLIER = 25214903917L;
        private static final long INCREMENT = 11L;
        private static final AtomicLong SEED_UNIQUIFIER = new AtomicLong(8682522807148012L);

        private long seed;

        VanillaRandom() {
            this(SEED_UNIQUIFIER.updateAndGet(current -> current * 1181783497276652981L) ^ System.nanoTime());
        }

        VanillaRandom(long seed) {
            this.seed = (seed ^ MULTIPLIER) & MASK;
        }

        int next(int bits) {
            seed = seed * MULTIPLIER + INCREMENT & MASK;
            return (int) (seed >>> 48 - bits);
        }

        int nextInt(int bound) {
            if (bound <= 0) {
                throw new IllegalArgumentException("Bound must be positive");
            }
            if ((bound & bound - 1) == 0) {
                return (int) ((long) bound * next(31) >> 31);
            }
            int sample;
            int modulo;
            do {
                sample = next(31);
                modulo = sample % bound;
            } while (sample - modulo + (bound - 1) < 0);
            return modulo;
        }
    }

    // -------------------------------------------------------------------- data

    private sealed interface Raw permits RawBitmap, RawUnihex, RawSpace {
    }

    private static final class RawBitmap implements Raw {
        final int[] pixels;
        final int imageW;
        final int sx;
        final int sy;
        final int w;
        final int h;
        final float pixelScale;
        final int ascent;
        final int advance;

        RawBitmap(int[] pixels, int imageW, int sx, int sy, int w, int h, float pixelScale, int ascent, int advance) {
            this.pixels = pixels;
            this.imageW = imageW;
            this.sx = sx;
            this.sy = sy;
            this.w = w;
            this.h = h;
            this.pixelScale = pixelScale;
            this.ascent = ascent;
            this.advance = advance;
        }
    }

    private static final class RawUnihex implements Raw {
        final int[] lines;
        final int left;
        final int right;

        RawUnihex(int[] lines, int left, int right) {
            this.lines = lines;
            this.left = left;
            this.right = right;
        }
    }

    private static final class RawSpace implements Raw {
        final int advance;

        RawSpace(int advance) {
            this.advance = advance;
        }
    }

    public static final class Glyph {
        public final float advance;
        public final float boldOffset;
        public final float shadowOffset;
        public final float top;
        public final float width;
        public final float height;
        public final float u0;
        public final float v0;
        public final float u1;
        public final float v1;
        public final boolean visible;

        Glyph(float advance, float boldOffset, float shadowOffset, float top, float width, float height,
              float u0, float v0, float u1, float v1, boolean visible) {
            this.advance = advance;
            this.boldOffset = boldOffset;
            this.shadowOffset = shadowOffset;
            this.top = top;
            this.width = width;
            this.height = height;
            this.u0 = u0;
            this.v0 = v0;
            this.u1 = u1;
            this.v1 = v1;
            this.visible = visible;
        }
    }
}
