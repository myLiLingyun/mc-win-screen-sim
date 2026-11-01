package com.endpoem.font;

/** Immutable subset of Minecraft's chat Style used by the poem files. */
public final class CharStyle {
    public static final int WHITE = 0xFFFFFF;

    private static final int[] LEGACY_COLORS = {
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
    };

    public static final CharStyle EMPTY = new CharStyle(null, false, false, false, false, false);

    public final Integer color;
    public final boolean bold;
    public final boolean italic;
    public final boolean underline;
    public final boolean strikethrough;
    public final boolean obfuscated;

    private CharStyle(Integer color, boolean bold, boolean italic, boolean underline, boolean strikethrough,
                      boolean obfuscated) {
        this.color = color;
        this.bold = bold;
        this.italic = italic;
        this.underline = underline;
        this.strikethrough = strikethrough;
        this.obfuscated = obfuscated;
    }

    public CharStyle withColor(int rgb) {
        return new CharStyle(rgb, bold, italic, underline, strikethrough, obfuscated);
    }

    public CharStyle withBold(boolean v) {
        return new CharStyle(color, v, italic, underline, strikethrough, obfuscated);
    }

    public CharStyle withItalic(boolean v) {
        return new CharStyle(color, bold, v, underline, strikethrough, obfuscated);
    }

    public CharStyle withUnderline(boolean v) {
        return new CharStyle(color, bold, italic, v, strikethrough, obfuscated);
    }

    public CharStyle withStrikethrough(boolean v) {
        return new CharStyle(color, bold, italic, underline, v, obfuscated);
    }

    public CharStyle withObfuscated(boolean v) {
        return new CharStyle(color, bold, italic, underline, strikethrough, v);
    }

    /**
     * Applies one § formatting code. Returns null for unknown codes (which Minecraft
     * silently swallows, including the § character).
     */
    public CharStyle applyLegacy(char code) {
        // Minecraft resets every other format when a colour code is applied.
        if (code >= '0' && code <= '9') {
            return new CharStyle(LEGACY_COLORS[code - '0'], false, false, false, false, false);
        }
        if (code >= 'a' && code <= 'f') {
            return new CharStyle(LEGACY_COLORS[10 + code - 'a'], false, false, false, false, false);
        }
        return switch (code) {
            case 'k' -> withObfuscated(true);
            case 'l' -> withBold(true);
            case 'm' -> withStrikethrough(true);
            case 'n' -> withUnderline(true);
            case 'o' -> withItalic(true);
            case 'r' -> EMPTY;
            default -> null;
        };
    }
}
