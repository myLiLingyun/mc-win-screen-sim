package com.endpoem.poem;

import com.endpoem.Assets;
import com.endpoem.PoemLanguage;
import com.endpoem.Settings;
import com.endpoem.font.Font;
import com.endpoem.font.StyledChar;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Builds the scrolling line list exactly like WinScreen, with bilingual paragraphs. */
public final class Poem {
    public static final String OBFUSCATE_TOKEN = "\u00a7f\u00a7k\u00a7a\u00a7b";
    private static final long POEM_SEED = 8124371L;

    public record Line(List<StyledChar> chars) {
        public boolean empty() {
            return chars.isEmpty();
        }
    }

    public final List<Line> lines;
    public final int totalScrollLength;

    private Poem(List<Line> lines) {
        this.lines = List.copyOf(lines);
        this.totalScrollLength = lines.size() * 12;
    }

    public static Poem load(Font font, Assets assets, Settings settings) throws IOException {
        Map<PoemLanguage, List<String>> texts = new EnumMap<>(PoemLanguage.class);
        int count = 0;
        Random random = new Random(POEM_SEED);
        for (PoemLanguage language : PoemLanguage.values()) {
            if (!settings.shows(language) || !assets.has(language.asset())) {
                continue;
            }
            List<String> lines = readLines(assets.poemText(language));
            count = Math.max(count, lines.size());
            for (int i = 0; i < lines.size(); i++) {
                lines.set(i, obfuscate(lines.get(i).replace("PLAYERNAME", settings.name()), random));
            }
            texts.put(language, lines);
        }
        if (texts.isEmpty()) {
            throw new IOException("no poem text available; put end_poem_en.txt in assets/");
        }

        List<Line> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            for (PoemLanguage language : PoemLanguage.values()) {
                if (!settings.shows(language)) {
                    continue;
                }
                List<String> lines = texts.get(language);
                if (lines != null && i < lines.size() && !lines.get(i).isEmpty()) {
                    addWrapped(font, out, lines.get(i));
                }
            }
            out.add(emptyLine());
        }
        for (int i = 0; i < 8; i++) {
            out.add(emptyLine());
        }
        System.out.printf("poem: %d display lines, scroll length %d, languages=%s%n",
                out.size(), out.size() * 12, settings.languages());
        return new Poem(out);
    }

    private static List<String> readLines(Path path) throws IOException {
        List<String> lines = new ArrayList<>(Files.readAllLines(path, StandardCharsets.UTF_8));
        if (!lines.isEmpty() && lines.get(0).startsWith("\ufeff")) {
            lines.set(0, lines.get(0).substring(1));
        }
        return lines;
    }

    private static void addWrapped(Font font, List<Line> out, String text) {
        for (List<StyledChar> line : font.wrap(text, 256)) {
            out.add(new Line(List.copyOf(line)));
        }
    }

    private static Line emptyLine() {
        return new Line(List.of());
    }

    /** Vanilla replaces each §f§k§a§b with §f§k + 3..6 random X characters. */
    private static String obfuscate(String line, Random random) {
        int pos;
        while ((pos = line.indexOf(OBFUSCATE_TOKEN)) != -1) {
            String before = line.substring(0, pos);
            String after = line.substring(pos + OBFUSCATE_TOKEN.length());
            int length = random.nextInt(4) + 3;
            line = before + "\u00a7f\u00a7k" + "X".repeat(length) + after;
        }
        return line;
    }
}
