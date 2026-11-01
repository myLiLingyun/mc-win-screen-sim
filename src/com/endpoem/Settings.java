package com.endpoem;

import java.util.Set;

public record Settings(String name, int scaleOption, Set<PoemLanguage> languages, boolean music,
                       ObfuscatedMode obfuscatedMode) {
    public Settings {
        languages = languages == null || languages.isEmpty()
                ? Set.of(PoemLanguage.ENGLISH, PoemLanguage.CHINESE)
                : Set.copyOf(languages);
        if (obfuscatedMode == null) {
            obfuscatedMode = ObfuscatedMode.VANILLA;
        }
    }

    public boolean showAnyLanguage() {
        return !languages.isEmpty();
    }

    public boolean shows(PoemLanguage language) {
        return languages.contains(language);
    }
}
