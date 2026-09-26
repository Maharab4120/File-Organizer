package com.fileorganizer.core;

import com.fileorganizer.util.FileHasher;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FileOrganizer {
    private static final String DUPLICATES_FOLDER = "Duplicates";

    private final String sourcePath;
    private final List<File> filesToOrganize;
    private RuleEngine rules;
    private boolean dryRun;
    private boolean detectDuplicates;
    private ProgressListener progressListener;
    private DatabaseManager database;

    public FileOrganizer(String sourcePath, RuleEngine rules) {
        this.sourcePath = sourcePath;
        this.rules = rules;
        this.filesToOrganize = new ArrayList<>();
    }

    public void setRules(RuleEngine rules) { this.rules = rules; }
    public void setDryRun(boolean v) { this.dryRun = v; }
    public void setDetectDuplicates(boolean v) { this.detectDuplicates = v; }
    public void setProgressListener(ProgressListener l) { this.progressListener = l; }
    public void setDatabase(DatabaseManager db) { this.database = db; }

    private void notify(int current, int total, String message) {
        if (progressListener != null) progressListener.update(current, total, message);
    }

    public List<File> scanFolder() {
        filesToOrganize.clear();
        File folder = new File(sourcePath);
        if (!folder.exists() || !folder.isDirectory()) {
            System.err.println("Error: Path is not a valid directory!");
            return filesToOrganize;
        }
        File[] files = folder.listFiles();
        if (files == null) return filesToOrganize;

        for (File f : files) {
            if (Thread.currentThread().isInterrupted()) {
                System.out.println("Scan interrupted.");
                break;
            }
            if (f.isFile()) filesToOrganize.add(f);
        }
        System.out.println("Found " + filesToOrganize.size() + " files to organize");
        return filesToOrganize;
    }

    public String organizeFiles() {
        if (filesToOrganize.isEmpty()) return null;

        System.out.println(dryRun ? "DRY RUN" : "Organizing files...");
        int total = filesToOrganize.size();
        int moved = 0, skipped = 0, errors = 0, duplicates = 0;
        long duplicateBytes = 0;

        String batchId = null;
        if (database != null && !dryRun) {
            batchId = database.newBatchId();
            try {
                database.createBatch(batchId, sourcePath, rules != null ? rules.getName() : "Default");
            } catch (SQLException ex) {
                System.err.println("Failed to create batch record: " + ex.getMessage());
            }
        }

        Map<String, File> hashToFirst = detectDuplicates ? new HashMap<>() : null;

        for (int i = 0; i < total; i++) {
            if (Thread.currentThread().isInterrupted()) {
                System.out.println("Organization cancelled by user.");
                notify(i, total, "Task cancelled.");
                break;
            }

            File file = filesToOrganize.get(i);
            try {
                String fileName = file.getName();
                String category = rules != null ? rules.findCategory(fileName) : "Other";
                boolean isDuplicate = false;

                if (detectDuplicates && hashToFirst != null) {
                    notify(i + 1, total, "Hashing: " + fileName);
                    String hash = FileHasher.sha256(file);
                    if (hashToFirst.containsKey(hash)) {
                        isDuplicate = true;
                        category = DUPLICATES_FOLDER;
                        duplicates++;
                        duplicateBytes += file.length();
                        System.out.println("Duplicate: " + fileName
                                + " (matches " + hashToFirst.get(hash).getName() + ")");
                    } else {
                        hashToFirst.put(hash, file);
                    }
                }

                Path destFolder = Paths.get(sourcePath, category);
                if (!dryRun) Files.createDirectories(destFolder);
                Path destPath = destFolder.resolve(fileName);

                if (!dryRun && Files.exists(destPath)) {
                    System.out.println("Skipping: " + fileName + " - already exists");
                    notify(i + 1, total, "Skipped: " + fileName);
                    skipped++;
                    continue;
                }

                if (dryRun) {
                    String tag = isDuplicate ? "[DUP] " : "";
                    System.out.println("Would move: " + tag + fileName + " -> " + category);
                    notify(i + 1, total, "Would move: " + tag + fileName);
                } else {
                    Files.move(file.toPath(), destPath, StandardCopyOption.REPLACE_EXISTING);
                    System.out.println("Moved: " + fileName + " -> " + category);
                    notify(i + 1, total, "Moved: " + fileName);
                    moved++;

                    if (database != null && batchId != null) {
                        try {
                            database.logMove(batchId, file.getAbsolutePath(),
                                    destPath.toAbsolutePath().toString(), category);
                        } catch (SQLException ex) {
                            System.err.println("DB log failed: " + ex.getMessage());
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println("Error: " + file.getName() + " - " + e.getMessage());
                notify(i + 1, total, "Error: " + file.getName());
                errors++;
            }
        }

        String dupSummary = duplicates > 0
                ? " • " + duplicates + " duplicates (" + formatSize(duplicateBytes) + ")"
                : "";
        String summary = "Done: " + moved + " moved, " + skipped + " skipped" + dupSummary;
        System.out.println("\n" + summary);
        notify(total, total, summary);
        return batchId;
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    public List<File> getFilesToOrganize() { return filesToOrganize; }
}