package com.fileorganizer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

// File Category Enum
enum FileCategory {
    IMAGES("Images", new String[]{"jpg", "jpeg", "png", "gif", "bmp", "svg", "webp", "ico"}),
    PDFS("PDFs", new String[]{"pdf"}),
    DOCUMENTS("Documents", new String[]{"doc", "docx", "txt", "rtf", "odt", "md"}),
    VIDEOS("Videos", new String[]{"mp4", "avi", "mkv", "mov", "wmv", "flv", "webm"}),
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

// File Organizer Class
class FileOrganizer {
    private String sourcePath;
    private List<File> filesToOrganize;
    private boolean dryRun;

    public FileOrganizer(String sourcePath) {
        this.sourcePath = sourcePath;
        this.filesToOrganize = new ArrayList<>();
        this.dryRun = false;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    public List<File> scanFolder() {
        filesToOrganize.clear();
        File folder = new File(sourcePath);

        if (!folder.exists() || !folder.isDirectory()) {
            System.err.println("Error: Path is not a valid directory!");
            return filesToOrganize;
        }

        File[] files = folder.listFiles();
        if (files == null) {
            return filesToOrganize;
        }

        for (File file : files) {
            if (file.isFile()) {
                filesToOrganize.add(file);
            }
        }

        System.out.println("Found " + filesToOrganize.size() + " files to organize");
        return filesToOrganize;
    }

    public void organizeFiles() {
        if (filesToOrganize.isEmpty()) {
            System.out.println("No files to organize. Run scanFolder() first.");
            return;
        }

        System.out.println(dryRun ? "DRY RUN - No files will be moved" : "Organizing files...");

        int organized = 0;
        int errors = 0;

        for (File file : filesToOrganize) {
            try {
                String fileName = file.getName();
                String extension = getFileExtension(fileName);
                FileCategory category = FileCategory.fromExtension(extension);

                // Create destination folder
                Path destFolder = Paths.get(sourcePath, category.getFolderName());
                if (!dryRun) {
                    Files.createDirectories(destFolder);
                }

                // Prepare destination file path
                Path destPath = Paths.get(destFolder.toString(), fileName);

                // Check if file already exists in destination
                if (!dryRun && Files.exists(destPath)) {
                    System.out.println("Skipping: " + fileName + " - already exists in " + category.getFolderName());
                    continue;
                }

                if (dryRun) {
                    System.out.println("Would move: " + fileName + " -> " + category.getFolderName());
                } else {
                    // Move the file
                    Path source = file.toPath();
                    Files.move(source, destPath, StandardCopyOption.REPLACE_EXISTING);
                    System.out.println("Moved: " + fileName + " -> " + category.getFolderName());
                    organized++;
                }

            } catch (IOException e) {
                System.err.println("Error moving file: " + file.getName());
                System.err.println("  " + e.getMessage());
                errors++;
            }
        }

        System.out.println("\nOrganization complete!");
        System.out.println("Files organized: " + organized);
        System.out.println("Errors: " + errors);
        if (dryRun) {
            System.out.println("(This was a dry run - no files were actually moved)");
        }
    }

    private String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(lastDotIndex + 1);
    }

    public List<File> getFilesToOrganize() {
        return filesToOrganize;
    }
}

// Main Application
public class Main {
    public static void main(String[] args) {
        System.out.println("===== FILE ORGANIZER =====");
        System.out.println("Java Desktop Application v1.0");
        System.out.println();

        Scanner scanner = new Scanner(System.in);

        System.out.print("Enter folder path to organize: ");
        String folderPath = scanner.nextLine().trim();

        // Validate input
        if (folderPath.isEmpty()) {
            System.out.println("No folder specified. Exiting.");
            scanner.close();
            return;
        }

        // Ask for dry run first
        System.out.print("Perform dry run first? (y/n): ");
        String dryRunInput = scanner.nextLine().trim().toLowerCase();
        boolean dryRun = dryRunInput.equals("y") || dryRunInput.equals("yes");

        FileOrganizer organizer = new FileOrganizer(folderPath);
        organizer.setDryRun(dryRun);

        System.out.println();
        System.out.println("Scanning folder: " + folderPath);
        organizer.scanFolder();

        if (organizer.getFilesToOrganize().isEmpty()) {
            System.out.println("No files found to organize.");
            scanner.close();
            return;
        }

        if (!dryRun) {
            System.out.print("\nProceed with organization? (y/n): ");
            String confirm = scanner.nextLine().trim().toLowerCase();
            if (!confirm.equals("y") && !confirm.equals("yes")) {
                System.out.println("Operation cancelled.");
                scanner.close();
                return;
            }
        }

        System.out.println();
        organizer.organizeFiles();

        scanner.close();
    }
}