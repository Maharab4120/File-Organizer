package com.fileorganizer;

import com.fileorganizer.core.*;
import com.fileorganizer.util.FileHasher;
import javafx.application.Application;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.prefs.Preferences;

public class Main extends Application {

    private static final String DEFAULT_RULES_URL =
            "https://gist.githubusercontent.com/Maharab4120/16be6c588ff61802caf3f0e8bbbfb86c/raw/5486fb5de259686306fcb64cfa7c787e4de15df5/cloudsort-rules.json";

    private FileOrganizer organizer;
    private DatabaseManager database;
    private RuleEngine rules = RuleEngine.builtInDefaults();
    private Scene scene;

    private final ExecutorService executor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        return t;
    });

    private final ObservableList<File> fileRows = FXCollections.observableArrayList();
    private Label folderLabel;
    private Label statusLabel;
    private Label rulesLabel;
    private TextField rulesUrlField;
    private Button chooseFolderBtn;
    private Button previewBtn;
    private Button organizeBtn;
    private Button undoBtn;
    private Button clearHistoryBtn;
    private Button fetchRulesBtn;
    private ToggleButton darkToggle;
    private CheckBox dupCheck;
    private ProgressBar progressBar;

    @Override
    public void init() {
        try {
            database = new DatabaseManager();
            System.out.println("DB: " + database.getDbPath());
        } catch (SQLException e) {
            System.err.println("DB init failed: " + e.getMessage());
        }

        String cached = RuleCache.load();
        if (cached != null) {
            try {
                rules = RuleFetcher.parse(cached);
                System.out.println("Loaded cached rules: " + rules.getName());
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void start(Stage stage) {
        // --- Rules bar ---
        rulesUrlField = new TextField(DEFAULT_RULES_URL);
        HBox.setHgrow(rulesUrlField, Priority.ALWAYS);

        fetchRulesBtn = new Button("🔄 Fetch Rules");
        rulesLabel = new Label("Rules: " + rules.getName());
        darkToggle = new ToggleButton("🌙 Dark");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox rulesBar = new HBox(10,
                new Label("Rules URL:"), rulesUrlField, fetchRulesBtn,
                rulesLabel, spacer, darkToggle);
        rulesBar.setPadding(new Insets(10, 10, 0, 10));
        rulesBar.setAlignment(Pos.CENTER_LEFT);

        // --- Folder bar ---
        chooseFolderBtn = new Button("📁 Choose Folder");
        folderLabel = new Label("No folder selected");
        HBox folderBar = new HBox(10, chooseFolderBtn, folderLabel);
        folderBar.setPadding(new Insets(10));
        folderBar.setAlignment(Pos.CENTER_LEFT);

        VBox top = new VBox(rulesBar, folderBar);

        // --- Table ---
        TableView<File> table = new TableView<>(fileRows);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<File, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getName()));
        nameCol.prefWidthProperty().bind(table.widthProperty().multiply(0.40));

        TableColumn<File, String> extCol = new TableColumn<>("Extension");
        extCol.setCellValueFactory(d -> new SimpleStringProperty(extractExt(d.getValue().getName())));
        extCol.prefWidthProperty().bind(table.widthProperty().multiply(0.15));

        TableColumn<File, String> catCol = new TableColumn<>("Category");
        catCol.setCellValueFactory(d -> new SimpleStringProperty(
                rules.findCategory(d.getValue().getName())));
        catCol.prefWidthProperty().bind(table.widthProperty().multiply(0.25));

        TableColumn<File, String> sizeCol = new TableColumn<>("Size");
        sizeCol.setCellValueFactory(d -> new SimpleStringProperty(formatSize(d.getValue().length())));
        sizeCol.prefWidthProperty().bind(table.widthProperty().multiply(0.20));

        table.getColumns().addAll(nameCol, extCol, catCol, sizeCol);

        // --- Bottom bar ---
        previewBtn = new Button("🔍 Preview");
        organizeBtn = new Button("✅ Organize");
        undoBtn = new Button("↩️ Undo Last");
        clearHistoryBtn = new Button("🗑️ Clear History");
        dupCheck = new CheckBox("Detect duplicates");
        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(180);
        statusLabel = new Label("Ready");

        previewBtn.setDisable(true);
        organizeBtn.setDisable(true);
        undoBtn.setDisable(database == null);
        clearHistoryBtn.setDisable(database == null);

        HBox bottomBar = new HBox(10,
                previewBtn, organizeBtn, undoBtn, clearHistoryBtn, dupCheck, progressBar, statusLabel);
        bottomBar.setPadding(new Insets(10));
        bottomBar.setAlignment(Pos.CENTER_LEFT);

        BorderPane root = new BorderPane();
        root.setTop(top);
        root.setCenter(table);
        root.setBottom(bottomBar);

        scene = new Scene(root, 1060, 600);

        // --- Actions ---
        chooseFolderBtn.setOnAction(e -> chooseFolder(stage));
        previewBtn.setOnAction(e -> doPreview());
        organizeBtn.setOnAction(e -> doOrganize());
        undoBtn.setOnAction(e -> doUndo());
        clearHistoryBtn.setOnAction(e -> doClearHistory());
        fetchRulesBtn.setOnAction(e -> doFetchRules());
        darkToggle.setOnAction(e -> applyDarkMode(darkToggle.isSelected()));

        // Restore dark mode preference
        boolean dark = Preferences.userNodeForPackage(Main.class).getBoolean("darkMode", false);
        darkToggle.setSelected(dark);
        applyDarkMode(dark);

        stage.setTitle("CloudSort");
        stage.setMinWidth(700);
        stage.setMinHeight(400);
        stage.setScene(scene);
        stage.show();
    }

    private void applyDarkMode(boolean dark) {
        scene.getStylesheets().clear();
        if (dark) {
            URL css = getClass().getResource("/dark.css");
            if (css != null) scene.getStylesheets().add(css.toExternalForm());
        }
        Preferences.userNodeForPackage(Main.class).putBoolean("darkMode", dark);
        darkToggle.setText(dark ? "Light Mode" : "Dark Mode");
    }

    private void doFetchRules() {
        String url = rulesUrlField.getText().trim();
        if (url.isEmpty()) { statusLabel.setText("Enter a rules URL"); return; }

        setBusy(true);
        statusLabel.setText("Fetching rules…");

        Task<RuleEngine> task = new Task<>() {
            @Override protected RuleEngine call() throws Exception {
                String json = RuleFetcher.download(url);
                RuleEngine engine = RuleFetcher.parse(json);
                RuleCache.save(json);
                return engine;
            }
        };

        task.setOnSucceeded(e -> {
            rules = task.getValue();
            rulesLabel.setText("Rules: " + rules.getName()
                    + " (" + rules.getCategories().size() + " categories)");
            statusLabel.setText("Rules updated");
            setBusy(false);
            if (organizer != null) organizer.setRules(rules);
            if (!fileRows.isEmpty()) {
                doPreview();
            }
        });

        task.setOnFailed(e -> {
            statusLabel.setText("Fetch failed: "
                    + (task.getException() != null ? task.getException().getMessage() : "?"));
            setBusy(false);
        });

        executor.submit(task);
    }

    private void chooseFolder(Stage stage) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose folder to organize");
        File chosen = chooser.showDialog(stage);
        if (chosen == null) return;

        folderLabel.setText(chosen.getAbsolutePath());
        organizer = new FileOrganizer(chosen.getAbsolutePath(), rules);
        organizer.setDatabase(database);
        organizer.setDetectDuplicates(dupCheck.isSelected());
        fileRows.clear();
        progressBar.setProgress(0);
        statusLabel.setText("Folder selected. Click Preview to scan.");
        previewBtn.setDisable(false);
        organizeBtn.setDisable(true);
    }

    private void doPreview() {
        if (organizer == null) return;
        setBusy(true);
        statusLabel.setText("Scanning folder...");

        Task<List<File>> task = new Task<>() {
            @Override
            protected List<File> call() {
                organizer.setDetectDuplicates(dupCheck.isSelected());
                return organizer.scanFolder();
            }
        };

        task.setOnSucceeded(e -> {
            List<File> files = task.getValue();
            fileRows.setAll(files);
            statusLabel.setText("Preview: " + files.size() + " files ready to organize");
            setBusy(false);
            organizeBtn.setDisable(files.isEmpty());
        });

        task.setOnFailed(e -> {
            statusLabel.setText("Scan failed: " + task.getException().getMessage());
            setBusy(false);
        });

        executor.submit(task);
    }

    private void doOrganize() {
        if (organizer == null || fileRows.isEmpty()) return;

        organizer.setDetectDuplicates(dupCheck.isSelected());
        setBusy(true);
        progressBar.setProgress(0);

        Task<Void> task = new Task<>() {
            @Override protected Void call() {
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
            fileRows.clear();
            setBusy(false);
            organizeBtn.setDisable(true);
        });

        task.setOnFailed(e -> {
            progressBar.progressProperty().unbind();
            statusLabel.textProperty().unbind();
            statusLabel.setText("Error: " + task.getException().getMessage());
            setBusy(false);
        });

        executor.submit(task);
    }

    private void doUndo() {
        if (database == null) return;
        setBusy(true);
        statusLabel.setText("Undoing last batch...");

        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                List<DatabaseManager.MoveRecord> records = database.getLastBatch();
                if (records.isEmpty()) {
                    return "Nothing to undo.";
                }

                int restored = 0, failed = 0;
                for (DatabaseManager.MoveRecord r : records) {
                    Path from = Paths.get(r.destPath());
                    Path to   = Paths.get(r.sourcePath());
                    try {
                        if (!Files.exists(from)) { failed++; continue; }
                        Files.createDirectories(to.getParent());
                        Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
                        restored++;
                    } catch (IOException ex) {
                        System.err.println("Undo failed: " + from + " - " + ex.getMessage());
                        failed++;
                    }
                }
                database.markUndone(records);
                return "Undo complete: " + restored + " restored, " + failed + " failed.";
            }
        };

        task.setOnSucceeded(e -> {
            statusLabel.setText(task.getValue());
            setBusy(false);
        });

        task.setOnFailed(e -> {
            statusLabel.setText("Undo error: " + task.getException().getMessage());
            setBusy(false);
        });

        executor.submit(task);
    }

    private void doClearHistory() {
        if (database == null) return;
        setBusy(true);

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                database.clearAllHistory();
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            statusLabel.setText("History cleared successfully.");
            setBusy(false);
        });

        task.setOnFailed(e -> {
            statusLabel.setText("Clear history error: " + task.getException().getMessage());
            setBusy(false);
        });

        executor.submit(task);
    }

    private void setBusy(boolean busy) {
        chooseFolderBtn.setDisable(busy);
        previewBtn.setDisable(busy);
        organizeBtn.setDisable(busy);
        undoBtn.setDisable(busy || database == null);
        clearHistoryBtn.setDisable(busy || database == null);
        fetchRulesBtn.setDisable(busy);
        dupCheck.setDisable(busy);
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

    @Override public void stop() throws Exception {
        executor.shutdown();
        if (database != null) database.close();
    }

    public static void main(String[] args) { launch(args); }
}