package com.fileorganizer.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DatabaseManager implements AutoCloseable {

    public record MoveRecord(long id, String batchId, String sourcePath,
                             String destPath, String category) {}

    private final Connection conn;
    private final Path dbPath;

    public DatabaseManager() throws SQLException {
        Path dir = Paths.get(System.getProperty("user.home"), ".cloudsort");
        try {
            Files.createDirectories(dir);
        } catch (Exception e) {
            throw new SQLException("Cannot create DB directory: " + dir, e);
        }
        this.dbPath = dir.resolve("history.db");
        this.conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);

        // Enforce foreign key constraints in SQLite
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON;");
        }

        initSchema();
    }

    private void initSchema() throws SQLException {
        try (Statement st = conn.createStatement()) {
            // Table 1: batches (Parent table)
            st.execute("""
                CREATE TABLE IF NOT EXISTS batches (
                    batch_id      TEXT PRIMARY KEY,
                    source_folder TEXT NOT NULL,
                    started_at    INTEGER NOT NULL,
                    rule_pack     TEXT
                )
                """);

            // Table 2: moves (Child table with Foreign Key)
            st.execute("""
                CREATE TABLE IF NOT EXISTS moves (
                    id          INTEGER PRIMARY KEY AUTOINCREMENT,
                    batch_id    TEXT    NOT NULL,
                    source_path TEXT    NOT NULL,
                    dest_path   TEXT    NOT NULL,
                    category    TEXT    NOT NULL,
                    moved_at    INTEGER NOT NULL,
                    undone      INTEGER NOT NULL DEFAULT 0,
                    FOREIGN KEY (batch_id) REFERENCES batches(batch_id) ON DELETE CASCADE
                )
                """);

            st.execute("CREATE INDEX IF NOT EXISTS idx_batch ON moves(batch_id)");
        }
    }

    public String newBatchId() {
        return UUID.randomUUID().toString();
    }

    public void createBatch(String batchId, String sourceFolder, String rulePack) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO batches (batch_id, source_folder, started_at, rule_pack) VALUES (?, ?, ?, ?)")) {
            ps.setString(1, batchId);
            ps.setString(2, sourceFolder);
            ps.setLong(3, System.currentTimeMillis());
            ps.setString(4, rulePack);
            ps.executeUpdate();
        }
    }

    public void logMove(String batchId, String source, String dest, String category)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO moves (batch_id, source_path, dest_path, category, moved_at) "
                        + "VALUES (?,?,?,?,?)")) {
            ps.setString(1, batchId);
            ps.setString(2, source);
            ps.setString(3, dest);
            ps.setString(4, category);
            ps.setLong(5, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    public List<MoveRecord> getLastBatch() throws SQLException {
        String batchId = null;
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT batch_id FROM moves WHERE undone = 0 ORDER BY id DESC LIMIT 1")) {
            if (rs.next()) batchId = rs.getString(1);
        }
        if (batchId == null) return List.of();

        List<MoveRecord> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, source_path, dest_path, category FROM moves "
                        + "WHERE batch_id = ? AND undone = 0 ORDER BY id ASC")) {
            ps.setString(1, batchId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new MoveRecord(
                            rs.getLong(1), batchId,
                            rs.getString(2), rs.getString(3), rs.getString(4)));
                }
            }
        }
        return list;
    }

    public void markUndone(List<MoveRecord> records) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE moves SET undone = 1 WHERE id = ?")) {
            for (MoveRecord r : records) {
                ps.setLong(1, r.id());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    public void clearAllHistory() throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("DELETE FROM moves");
            st.execute("DELETE FROM batches");
        }
    }

    public Path getDbPath() {
        return dbPath;
    }

    @Override
    public void close() throws SQLException {
        if (conn != null && !conn.isClosed()) conn.close();
    }
}