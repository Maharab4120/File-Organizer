package com.fileorganizer;

import com.fileorganizer.core.FileCategory;
import com.fileorganizer.core.FileOrganizer;
import javafx.application.Application;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.List;

public class Main extends Application {

    private FileOrganizer organizer;
    private final ObservableList<File> fileRows = FXCollections.observableArrayList();
    private Label folderLabel;
    private Label statusLabel;
    private Button chooseFolderBtn;
    private Button previewBtn;
    private Button organizeBtn;
    private ProgressBar progressBar;

    @Override
    public void start(Stage stage) {
        // --- Top bar ---
        chooseFolderBtn = new Button("📁 Choose Folder");
        folderLabel = new Label("No folder selected");
        HBox topBar = new HBox(10, chooseFolderBtn, folderLabel);
        topBar.setPadding(new Insets(10));
        topBar.setAlignment(Pos.CENTER_LEFT);

        // --- Table ---
        TableView<File> table = new TableView<>(fileRows);

        TableColumn<File, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getName()));
        nameCol.setPrefWidth(340);

        TableColumn<File, String> extCol = new TableColumn<>("Extension");
        extCol.setCellValueFactory(d -> new SimpleStringProperty(extractExt(d.getValue().getName())));
        extCol.setPrefWidth(100);

        TableColumn<File, String> catCol = new TableColumn<>("Category");
        catCol.setCellValueFactory(d -> new SimpleStringProperty(
                FileCategory.fromExtension(extractExt(d.getValue().getName())).getFolderName()));
        catCol.setPrefWidth(120);

        TableColumn<File, String> sizeCol = new TableColumn<>("Size");
        sizeCol.setCellValueFactory(d -> new SimpleStringProperty(formatSize(d.getValue().length())));
        sizeCol.setPrefWidth(100);

        table.getColumns().addAll(nameCol, extCol, catCol, sizeCol);

        // --- Bottom bar ---
        previewBtn = new Button("🔍 Preview");
        organizeBtn = new Button("✅ Organize");
        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(220);
        statusLabel = new Label("Ready");

        previewBtn.setDisable(true);
        organizeBtn.setDisable(true);

        HBox bottomBar = new HBox(10, previewBtn, organizeBtn, progressBar, statusLabel);
        bottomBar.setPadding(new Insets(10));
        bottomBar.setAlignment(Pos.CENTER_LEFT);

        // --- Layout ---
        BorderPane root = new BorderPane();
        root.setTop(topBar);
        root.setCenter(table);
        root.setBottom(bottomBar);

        // --- Actions ---
        chooseFolderBtn.setOnAction(e -> chooseFolder(stage));
        previewBtn.setOnAction(e -> doPreview());
        organizeBtn.setOnAction(e -> doOrganize());

        stage.setTitle("CloudSort");
        stage.setScene(new Scene(root, 900, 560));
        stage.show();
    }

    private void chooseFolder(Stage stage) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose folder to organize");
        File chosen = chooser.showDialog(stage);
        if (chosen == null) return;

        folderLabel.setText(chosen.getAbsolutePath());
        organizer = new FileOrganizer(chosen.getAbsolutePath());
        fileRows.clear();
        progressBar.setProgress(0);
        statusLabel.setText("Folder selected. Click Preview to scan.");
        previewBtn.setDisable(false);
        organizeBtn.setDisable(true);
    }

    private void doPreview() {
        if (organizer == null) return;
        List<File> files = organizer.scanFolder();
        fileRows.setAll(files);
        statusLabel.setText("Preview: " + files.size() + " files ready to organize");
        organizeBtn.setDisable(files.isEmpty());
    }

    private void doOrganize() {
        if (organizer == null || fileRows.isEmpty()) return;

        setBusy(true);
        progressBar.setProgress(0);

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                organizer.setProgressListener((current, total, message) -> {
                    updateProgress(current, total);
                    updateMessage(message);
                });
                organizer.organizeFiles();
                return null;
            }
        };

        progressBar.progressProperty().bind(task.progressProperty());
        statusLabel.textProperty().bind(task.messageProperty());

        task.setOnSucceeded(e -> {
            progressBar.progressProperty().unbind();
            statusLabel.textProperty().unbind();
            progressBar.setProgress(1.0);
            statusLabel.setText("Done.");
            fileRows.clear();
            organizeBtn.setDisable(true);
            setBusy(false);
        });

        task.setOnFailed(e -> {
            progressBar.progressProperty().unbind();
            statusLabel.textProperty().unbind();
            statusLabel.setText("Error: " + task.getException().getMessage());
            setBusy(false);
        });

        Thread t = new Thread(task, "organizer-worker");
        t.setDaemon(true);
        t.start();
    }

    private void setBusy(boolean busy) {
        chooseFolderBtn.setDisable(busy);
        previewBtn.setDisable(busy);
        organizeBtn.setDisable(busy);
    }

    private String extractExt(String name) {
        int i = name.lastIndexOf('.');
        return (i == -1 || i == name.length() - 1) ? "" : name.substring(i + 1);
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    public static void main(String[] args) {
        launch(args);
    }
}