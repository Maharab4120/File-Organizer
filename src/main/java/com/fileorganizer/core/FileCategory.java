package com.fileorganizer.core;

public enum FileCategory {
    IMAGES("Images", new String[]{"jpg", "jpeg", "png", "gif", "bmp", "svg", "webp", "ico"}),
    PDFS("PDFs", new String[]{"pdf"}),
    DOCUMENTS("Documents", new String[]{"doc", "docx", "txt", "rtf", "odt", "md", "srt"}),
    VIDEOS("Videos", new String[]{"mp4", "avi", "mkv", "mov", "wmv", "flv", "webm", "mpeg"}),
    MUSIC("Music", new String[]{"mp3", "wav", "flac", "aac", "ogg", "wma"}),
    ARCHIVES("Archives", new String[]{"zip", "rar", "7z", "tar", "gz", "bz2"}),
    EXECUTABLES("Executables", new String[]{"exe", "msi", "sh", "bat", "cmd", "jar"}),
    OTHERS("Others", new String[]{});

    private final String folderName;
    private final String[] extensions;

    FileCategory(String folderName, String[] extensions) {
        this.folderName = folderName;
        this.extensions = extensions;
    }

    public String getFolderName() {
        return folderName;
    }

    public String[] getExtensions() {
        return extensions;
    }

    public static FileCategory fromExtension(String extension) {
        if (extension == null || extension.isEmpty()) {
            return OTHERS;
        }

        String ext = extension.toLowerCase();

        for (FileCategory category : values()) {
            for (String catExt : category.getExtensions()) {
                if (catExt.equals(ext)) {
                    return category;
                }
            }
        }

        return OTHERS;
    }
}