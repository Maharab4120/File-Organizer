package com.fileorganizer.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class RuleEngine {

    // --- Top-level fields (subset we care about) ---
    private String name = "Built-in";
    private String version;
    private String description;
    private Settings settings = new Settings();
    private List<SortingRule> categories = new ArrayList<>();
    private SpecialRules specialRules = new SpecialRules();

    public static class Settings {
        private boolean caseSensitive = false;
        private String fallbackFolder = "Others";
        private boolean ignoreHiddenFiles = true;
        private boolean ignoreSystemFiles = true;

        public boolean isCaseSensitive() { return caseSensitive; }
        public String getFallbackFolder() { return fallbackFolder; }
        public boolean isIgnoreHiddenFiles() { return ignoreHiddenFiles; }
        public boolean isIgnoreSystemFiles() { return ignoreSystemFiles; }
    }

    public static class SpecialRules {
        private List<CompoundExtension> compoundExtensions = new ArrayList<>();
        public List<CompoundExtension> getCompoundExtensions() { return compoundExtensions; }
    }

    public static class CompoundExtension {
        private String extension;   // e.g. "tar.gz"
        private String folder;      // e.g. "Archives"
        public String getExtension() { return extension; }
        public String getFolder() { return folder; }
    }

    // --- API ---

    public String getName() { return name; }
    public String getVersion() { return version; }
    public String getDescription() { return description; }
    public List<SortingRule> getCategories() { return categories; }
    public Settings getSettings() { return settings; }
    public SpecialRules getSpecialRules() { return specialRules; }

    public String getFallbackFolder() {
        return settings != null && settings.getFallbackFolder() != null
                ? settings.getFallbackFolder()
                : "Others";
    }

    /** Resolve a filename to a folder name. */
    public String findCategory(String fileName) {
        if (fileName == null || fileName.isEmpty()) return getFallbackFolder();

        boolean caseSensitive = settings != null && settings.isCaseSensitive();
        String needle = caseSensitive ? fileName : fileName.toLowerCase();

        // 1) Compound extensions first (tar.gz beats .gz)
        for (CompoundExtension ce : specialRules.getCompoundExtensions()) {
            if (ce.getExtension() == null) continue;
            String ext = caseSensitive ? ce.getExtension() : ce.getExtension().toLowerCase();
            if (needle.endsWith("." + ext)) {
                return ce.getFolder();
            }
        }

        // 2) Single extension
        String ext = extractExt(fileName);
        if (!ext.isEmpty()) {
            if (!caseSensitive) ext = ext.toLowerCase();
            for (SortingRule rule : sortedByPriority()) {
                if (rule.matches(ext, caseSensitive)) return rule.getFolder();
            }
        }

        return getFallbackFolder();
    }

    /** Categories ordered by priority (lowest number = checked first). Missing priority → 999. */
    private List<SortingRule> sortedByPriority() {
        List<SortingRule> copy = new ArrayList<>(categories);
        copy.sort(Comparator.comparingInt(SortingRule::getPriority));
        return copy;
    }

    public List<String> allFolderNames() {
        List<String> out = new ArrayList<>();
        for (SortingRule r : categories) out.add(r.getFolder());
        String fb = getFallbackFolder();
        if (!out.contains(fb)) out.add(fb);
        return out;
    }

    private static String extractExt(String name) {
        int i = name.lastIndexOf('.');
        return (i == -1 || i == name.length() - 1) ? "" : name.substring(i + 1);
    }

    /** Fallback if there is no internet AND no cache. */
    public static RuleEngine builtInDefaults() {
        RuleEngine r = new RuleEngine();
        r.name = "Built-in (offline)";
        r.settings = new Settings();
        r.categories = List.of(
                new SortingRule("Images",      List.of("jpg","jpeg","png","gif","bmp","svg","webp","ico","heic"), 10),
                new SortingRule("PDFs",        List.of("pdf"), 20),
                new SortingRule("Documents",   List.of("doc","docx","txt","rtf","odt","md","srt"), 30),
                new SortingRule("Videos",      List.of("mp4","avi","mkv","mov","wmv","flv","webm","mpeg","m4v"), 60),
                new SortingRule("Music",       List.of("mp3","wav","flac","aac","ogg","wma","m4a"), 70),
                new SortingRule("Archives",    List.of("zip","rar","7z","tar","gz","bz2","xz"), 80),
                new SortingRule("Executables", List.of("exe","msi","sh","bat","cmd","jar","appimage","deb","rpm"), 90)
        );
        return r;
    }
}