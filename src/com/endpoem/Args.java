package com.endpoem;

import java.util.HashMap;
import java.util.Map;

/** Tiny command line parser. Unknown options are ignored with a warning. */
public final class Args {
    private final Map<String, String> values = new HashMap<>();
    private final Map<String, Boolean> flags = new HashMap<>();

    public static Args parse(String[] argv) {
        Args a = new Args();
        for (int i = 0; i < argv.length; i++) {
            String s = argv[i];
            if (!s.startsWith("--")) {
                System.err.println("ignoring argument: " + s);
                continue;
            }
            String key = s.substring(2);
            String value = null;
            int eq = key.indexOf('=');
            if (eq >= 0) {
                value = key.substring(eq + 1);
                key = key.substring(0, eq);
            }
            if (value == null && i + 1 < argv.length && !argv[i + 1].startsWith("--")) {
                value = argv[++i];
            }
            if (value == null) {
                a.flags.put(key, true);
            } else {
                a.values.put(key, value);
            }
        }
        return a;
    }

    public String get(String key, String def) {
        return values.getOrDefault(key, def);
    }

    public int getInt(String key, int def) {
        String v = values.get(key);
        if (v == null) {
            return def;
        }
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public float getFloat(String key, float def) {
        String v = values.get(key);
        if (v == null) {
            return def;
        }
        try {
            return Float.parseFloat(v);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public boolean flag(String key) {
        if (flags.containsKey(key)) {
            return true;
        }
        String v = values.get(key);
        if (v == null) {
            return false;
        }
        return !v.equalsIgnoreCase("false") && !v.equals("0") && !v.equalsIgnoreCase("no");
    }

    public boolean getBool(String key, boolean def) {
        if (flags.containsKey(key)) {
            return true;
        }
        String v = values.get(key);
        if (v == null) {
            return def;
        }
        return switch (v.toLowerCase()) {
            case "1", "true", "yes", "on" -> true;
            case "0", "false", "no", "off" -> false;
            default -> def;
        };
    }
}
