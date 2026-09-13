package com.fileorganizer.core;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public class FileOrganizer {

    private final String sourcePath;
    private final List<File> filesToOrganize;
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

        System.out.println(
                "Found " + filesToOrganize.size() + " files to organize"
        );

        return filesToOrganize;
    }

    public void organizeFiles() {

        if (filesToOrganize.isEmpty()) {
            System.out.println(
                    "No files to organize. Run scanFolder() first."
            );
            return;
        }

        System.out.println(
                dryRun
                        ? "DRY RUN - No files will be moved"
                        : "Organizing files..."
        );

        int organized = 0;
        int errors = 0;

        for (File file : filesToOrganize) {

            try {
                String fileName = file.getName();

                FileCategory category =
                        FileCategory.fromExtension(
                                getFileExtension(fileName)
                        );

                Path destFolder =
                        Paths.get(
                                sourcePath,
                                category.getFolderName()
                        );

                if (!dryRun) {
                    Files.createDirectories(destFolder);
                }

                Path destPath =
                        Paths.get(
                                destFolder.toString(),
                                fileName
                        );

                if (!dryRun && Files.exists(destPath)) {
                    System.out.println(
                            "Skipping: " + fileName +
                                    " - already exists"
                    );
                    continue;
                }

                if (dryRun) {

                    System.out.println(
                            "Would move: " + fileName +
                                    " -> " + category.getFolderName()
                    );

                } else {

                    Files.move(
                            file.toPath(),
                            destPath,
                            StandardCopyOption.REPLACE_EXISTING
                    );

                    System.out.println(
                            "Moved: " + fileName +
                                    " -> " + category.getFolderName()
                    );

                    organized++;
                }

            } catch (IOException e) {

                System.err.println(
                        "Error moving file: " +
                                file.getName() +
                                " - " +
                                e.getMessage()
                );

                errors++;
            }
        }

        System.out.println("\nOrganization complete!");
        System.out.println("Files organized: " + organized);
        System.out.println("Errors: " + errors);
    }

    private String getFileExtension(String fileName) {

        int i = fileName.lastIndexOf('.');

        return (i == -1 || i == fileName.length() - 1)
                ? ""
                : fileName.substring(i + 1);
    }

    public List<File> getFilesToOrganize() {
        return filesToOrganize;
    }
}