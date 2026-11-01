package com.endpoem;

import java.util.Locale;

/** The languages that can be shown in the poem, in display order. */
public enum PoemLanguage {
    ENGLISH("English", "en", "end_poem_en.txt"),
    CHINESE("中文", "zh", "end_poem_zh.txt"),
    LZH_SHANG("文言·上宮", "lzh-shang", "end_poem_lzh_shang.txt"),
    LZH_WANG("文言·望海", "lzh-wang", "end_poem_lzh_wang.txt"),
    LZH_FANG("文言·方矩", "lzh-fang", "end_poem_lzh_fang.txt"),
    LZH_ICE("文言·IceWars", "lzh-ice", "end_poem_lzh_ice.txt");

    private final String label;
    private final String id;
    private final String asset;

    PoemLanguage(String label, String id, String asset) {
        this.label = label;
        this.id = id;
        this.asset = asset;
    }

    public String label() {
        return label;
    }

    public String id() {
        return id;
    }

    public String asset() {
        return asset;
    }

    /** Parses one --lang token (en/zh/lzh-shang/...); returns null if unknown. */
    public static PoemLanguage parse(String token) {
        if (token == null) {
            return null;
        }
        String s = token.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        return switch (s) {
            case "en", "eng", "english", "英文" -> ENGLISH;
            case "zh", "cn", "chinese", "中文", "汉语", "漢語" -> CHINESE;
            case "lzh-shang", "lzhshang", "shang", "shangong", "上宮", "上宫" -> LZH_SHANG;
            case "lzh-wang", "lzhwang", "wang", "wanghai", "望海" -> LZH_WANG;
            case "lzh-fang", "lzhfang", "fang", "fangju", "方矩" -> LZH_FANG;
            case "lzh-ice", "lzhice", "ice", "icewars", "icewars1o1" -> LZH_ICE;
            default -> null;
        };
    }

    public static boolean isClassicalChinese(PoemLanguage language) {
        return language == LZH_SHANG || language == LZH_WANG || language == LZH_FANG || language == LZH_ICE;
    }
}
