package com.endpoem;

import java.util.Locale;

/**
 * How §k picks and advances its random glyph.
 *
 * <p>VANILLA is the actual 26.2 behaviour: pick from every provider using the
 * original glyph's {@code ceil(advance)} width class, then advance by the
 * selected glyph's own advance. FIXED keeps the original advance; SAME_FONT
 * also restricts the pool to the original glyph's provider family and exact
 * advance, which removes the Mojangles/Unifont mix.</p>
 */
public enum ObfuscatedMode {
    VANILLA("C Vanilla", "vanilla"),
    FIXED("A Fixed width", "fixed"),
    SAME_FONT("B Same font", "same-font");

    private final String displayName;
    private final String id;

    ObfuscatedMode(String displayName, String id) {
        this.displayName = displayName;
        this.id = id;
    }

    public String displayName() {
        return displayName;
    }

    public String id() {
        return id;
    }

    public ObfuscatedMode offset(int direction) {
        ObfuscatedMode[] all = values();
        int i = ordinal() + (direction < 0 ? -1 : 1);
        if (i < 0) {
            i = all.length - 1;
        } else if (i >= all.length) {
            i = 0;
        }
        return all[i];
    }

    public static ObfuscatedMode parse(String value) {
        if (value == null) {
            return VANILLA;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT).replace('_', '-')) {
            case "a", "fixed", "fixed-width", "original", "original-width" -> FIXED;
            case "b", "same-font", "samefont", "font", "same" -> SAME_FONT;
            default -> VANILLA;
        };
    }
}
