package com.fileorganizer.core;

import java.util.List;

public class SortingRule {
    private String folder;
    private int priority = 999;
    private List<String> extensions;

    public SortingRule() {}

    public SortingRule(String folder, List<String> extensions, int priority) {
        this.folder = folder;
        this.extensions = extensions;
        this.priority = priority;
    }

    public String getFolder() { return folder; }
    public int getPriority() { return priority; }
    public List<String> getExtensions() { return extensions; }

    public boolean matches(String ext, boolean caseSensitive) {
        if (extensions == null || ext == null) return false;
        for (String e : extensions) {
            if (e == null) continue;
            if (caseSensitive ? e.equals(ext) : e.equalsIgnoreCase(ext)) return true;
        }
        return false;
    }
}