package com.endpoem;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Resolves optional user-supplied assets first, then the small placeholder
 * fallback shipped with this repository. A full Minecraft asset tree dropped
 * into {@code assets/} automatically overrides every placeholder file that it
 * contains; no asset is downloaded or copied by the program.
 */
public final class Assets {
    public final Path root;
    public final Path fallback;

    public Assets(Path root, Path fallback) {
        this.root = root;
        this.fallback = fallback;
    }

    public Path path(String relative) {
        Path primary = root == null ? null : root.resolve(relative);
        if (isFile(primary)) {
            return primary;
        }
        Path alt = fallback == null ? null : fallback.resolve(relative);
        if (isFile(alt)) {
            return alt;
        }
        return primary != null ? primary : alt;
    }

    public boolean has(String relative) {
        return isFile(path(relative));
    }

    public List<PoemLanguage> availablePoemLanguages() {
        List<PoemLanguage> result = new ArrayList<>();
        for (PoemLanguage language : PoemLanguage.values()) {
            if (has(language.asset())) {
                result.add(language);
            }
        }
        return result;
    }

    public Path textEn() {
        return path("end_poem_en.txt");
    }

    public Path textZh() {
        return path("end_poem_zh.txt");
    }

    public Path poemText(PoemLanguage language) {
        return path(language.asset());
    }

    public Path fontFile(String name) {
        return path("font/" + name);
    }

    public Path shaderInclude(String name) {
        return path("shaders/include/" + name);
    }

    public Path shaderCore(String name) {
        return path("shaders/core/" + name);
    }

    public Path texture(String name) {
        return path("textures/" + name);
    }

    public Path music() {
        return path("sounds/alpha.ogg");
    }

    public boolean hasMusic() {
        return has("sounds/alpha.ogg");
    }

    private static boolean isFile(Path path) {
        try {
            return path != null && Files.isRegularFile(path) && Files.size(path) > 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static Assets resolve(String fromArgs) {
        Path primary = resolveRoot(fromArgs);
        Path fallback = findDirectory("assets-placeholder");
        if (primary == null) {
            primary = fallback;
        }
        return new Assets(primary, fallback);
    }

    private static Path resolveRoot(String fromArgs) {
        String env = System.getenv("END_POEM_ASSETS");
        if (env != null && !env.isBlank()) {
            Path candidate = Path.of(env);
            if (Files.isDirectory(candidate)) {
                return candidate.toAbsolutePath().normalize();
            }
        }
        Path explicit = Path.of(fromArgs == null ? "assets" : fromArgs);
        if (Files.isDirectory(explicit)) {
            return explicit.toAbsolutePath().normalize();
        }
        Path cwd = Path.of("assets");
        if (Files.isDirectory(cwd)) {
            return cwd.toAbsolutePath().normalize();
        }
        Path sibling = findDirectory("assets");
        return sibling;
    }

    private static Path findDirectory(String name) {
        Path cwd = Path.of(name);
        if (Files.isDirectory(cwd)) {
            return cwd.toAbsolutePath().normalize();
        }
        try {
            Path jar = Path.of(Assets.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path parent = jar.getParent();
            if (parent != null) {
                Path sibling = parent.resolve(name);
                if (Files.isDirectory(sibling)) {
                    return sibling.toAbsolutePath().normalize();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
