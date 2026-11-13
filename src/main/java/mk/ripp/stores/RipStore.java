package mk.ripp.stores;

import mk.ripp.model.RipMetadata;
import mk.ripp.model.RipSummary;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class RipStore implements AutoCloseable {
    public static final int HASH_LENGTH = 32;

    private static final String CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS rips (
                id            TEXT PRIMARY KEY,
                file_name     TEXT    NOT NULL,
                chunk_size    INTEGER NOT NULL,
                file_size     INTEGER NOT NULL,
                chunk_hashes  BLOB    NOT NULL
            )
            """;

    private final Connection connection;

    public RipStore(Path dbFile) {
        try {
            Files.createDirectories(dbFile.getParent());
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.toAbsolutePath());
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA journal_mode=WAL");
                statement.execute("PRAGMA busy_timeout=5000");
                statement.execute(CREATE_TABLE);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Could not open rip database " + dbFile, e);
        }
    }

    public synchronized void insert(RipMetadata rip) {
        byte[] hashes = pack(rip.chunkHashes());
        String sql = "INSERT INTO rips (id, file_name, file_size, chunk_size, chunk_hashes) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, rip.id().toString());
            statement.setString(2, rip.fileName());
            statement.setLong(3, rip.fileSize());
            statement.setInt(4, rip.chunkSize());
            statement.setBytes(5, hashes);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not insert rip " + rip.id(), e);
        }
    }

    public synchronized Optional<RipMetadata> find(UUID id) {
        String sql = "SELECT file_name, file_size, chunk_size, chunk_hashes FROM rips WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id.toString());
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return Optional.empty();
                }
                return Optional.of(new RipMetadata(
                        id,
                        rows.getString("file_name"),
                        rows.getLong("file_size"),
                        rows.getInt("chunk_size"),
                        unpack(rows.getBytes("chunk_hashes"))
                ));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not read rip " + id, e);
        }
    }

    public synchronized List<RipSummary> latest(int limit) {
        String sql = """
            SELECT id, file_name, file_size, chunk_size, length(chunk_hashes) / ? AS total_chunks
            FROM rips
            ORDER BY rowid DESC
            LIMIT ?
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, HASH_LENGTH);
            statement.setInt(2, limit);
            try (ResultSet rows = statement.executeQuery()) {
                List<RipSummary> rips = new ArrayList<>();
                while (rows.next()) {
                    rips.add(new RipSummary(
                            UUID.fromString(rows.getString("id")),
                            rows.getString("file_name"),
                            rows.getLong("file_size"),
                            rows.getInt("chunk_size"),
                            rows.getInt("total_chunks")
                    ));
                }
                return rips;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not list rips", e);
        }
    }

    @Override
    public synchronized void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not close rip database", e);
        }
    }

    private static byte[] pack(List<byte[]> hashes) {
        byte[] packed = new byte[hashes.size() * HASH_LENGTH];
        for (int i = 0; i < hashes.size(); i++) {
            byte[] hash = hashes.get(i);
            if (hash.length != HASH_LENGTH) {
                throw new IllegalArgumentException("Chunk hash " + i + " must be " + HASH_LENGTH + " bytes");
            }
            System.arraycopy(hash, 0, packed, i * HASH_LENGTH, HASH_LENGTH);
        }
        return packed;
    }

    private static List<byte[]> unpack(byte[] packed) {
        if (packed.length % HASH_LENGTH != 0) {
            throw new IllegalStateException("chunk_hashes length is not a multiple of " + HASH_LENGTH);
        }
        int count = packed.length / HASH_LENGTH;
        List<byte[]> hashes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            byte[] hash = new byte[HASH_LENGTH];
            System.arraycopy(packed, i * HASH_LENGTH, hash, 0, HASH_LENGTH);
            hashes.add(hash);
        }
        return hashes;
    }
}