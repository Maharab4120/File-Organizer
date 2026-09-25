package com.fileorganizer.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class RuleCache {

    private static final Path CACHE_FILE =
            Paths.get(System.getProperty("user.home"), ".cloudsort", "rules-cache.json");

    public static void save(String json) {
        try {
            Files.createDirectories(CACHE_FILE.getParent());
            Files.writeString(CACHE_FILE, json);
        } catch (IOException e) {
            System.err.println("Rule cache save failed: " + e.getMessage());
        }
    }

    public static String load() {
        try {
            if (!Files.exists(CACHE_FILE)) return null;
            return Files.readString(CACHE_FILE);
        } catch (IOException e) {
            System.err.println("Rule cache load failed: " + e.getMessage());
            return null;
        }
    }

    public static Path getPath() { return CACHE_FILE; }
}