package com.vku.pulseoj.backend.db;

import com.vku.pulseoj.common.dto.ContestantDTO;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * [TV1] Data Access Object for Contestant Accounts.
 * Performs BCrypt password hashing and authentication with Neon PostgreSQL.
 * Provides in-memory fallback cache if offline or in mock mode.
 */
public class ContestantAccountDAO {

    private static final Logger logger = LoggerFactory.getLogger(ContestantAccountDAO.class);

    // In-memory fallback repository for mock/offline mode
    private static final Map<String, MockContestant> MOCK_STORE_BY_USERNAME = new ConcurrentHashMap<>();
    private static final Map<Integer, MockContestant> MOCK_STORE_BY_ID = new ConcurrentHashMap<>();
    private static final AtomicInteger MOCK_ID_GEN = new AtomicInteger(100);

    static {
        // Seed initial default mock accounts
        seedMockAccount("student01", "password123", "Nguyen Van A");
        seedMockAccount("student02", "password123", "Tran Thi B");
        seedMockAccount("student03", "password123", "Le Van C");
    }

    private static void seedMockAccount(String username, String rawPassword, String fullName) {
        int id = MOCK_ID_GEN.incrementAndGet();
        String hash = BCrypt.hashpw(rawPassword, BCrypt.gensalt(10));
        MockContestant mock = new MockContestant(id, username, hash, fullName, false, Instant.now());
        MOCK_STORE_BY_USERNAME.put(username, mock);
        MOCK_STORE_BY_ID.put(id, mock);
    }

    /**
     * Authenticates a user by username and raw password.
     * Returns ContestantDTO if valid and not locked; returns null if invalid.
     */
    public ContestantDTO authenticate(String username, String rawPassword) {
        if (username == null || rawPassword == null) {
            return null;
        }

        if (NeonDataSourceConfig.isMockMode()) {
            MockContestant mock = MOCK_STORE_BY_USERNAME.get(username);
            if (mock != null && !mock.isLocked && BCrypt.checkpw(rawPassword, mock.passwordHash)) {
                return new ContestantDTO(mock.id, mock.username, mock.fullName, mock.isLocked, mock.createdAt);
            }
            return null;
        }

        String sql = "SELECT id, username, password_hash, full_name, is_locked, created_at " +
                     "FROM contestants WHERE username = ?";
        try (Connection conn = NeonDataSourceConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    boolean locked = rs.getBoolean("is_locked");
                    if (locked) {
                        logger.warn("Contestant [{}] attempted login but account is locked.", username);
                        return null;
                    }

                    String storedHash = rs.getString("password_hash");
                    if (BCrypt.checkpw(rawPassword, storedHash)) {
                        Timestamp ts = rs.getTimestamp("created_at");
                        Instant created = (ts != null) ? ts.toInstant() : Instant.now();
                        return new ContestantDTO(
                                rs.getInt("id"),
                                rs.getString("username"),
                                rs.getString("full_name"),
                                locked,
                                created
                        );
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Error during authentication for username [{}]: {}", username, e.getMessage());
        }
        return null;
    }

    /**
     * Creates a new contestant account with BCrypt password hashing.
     */
    public ContestantDTO createContestant(String username, String rawPassword, String fullName) throws Exception {
        if (username == null || username.isBlank() || rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("Username and password must not be empty");
        }

        String passwordHash = BCrypt.hashpw(rawPassword, BCrypt.gensalt(10));

        if (NeonDataSourceConfig.isMockMode()) {
            if (MOCK_STORE_BY_USERNAME.containsKey(username)) {
                throw new IllegalStateException("Contestant username already exists: " + username);
            }
            int id = MOCK_ID_GEN.incrementAndGet();
            MockContestant mock = new MockContestant(id, username, passwordHash, fullName, false, Instant.now());
            MOCK_STORE_BY_USERNAME.put(username, mock);
            MOCK_STORE_BY_ID.put(id, mock);
            return new ContestantDTO(id, username, fullName, false, mock.createdAt);
        }

        String sql = "INSERT INTO contestants (username, password_hash, full_name, is_locked) " +
                     "VALUES (?, ?, ?, FALSE)";
        try (Connection conn = NeonDataSourceConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username.trim());
            ps.setString(2, passwordHash);
            ps.setString(3, fullName.trim());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    int id = keys.getInt(1);
                    return new ContestantDTO(id, username, fullName, false, Instant.now());
                }
            }
        }
        throw new SQLException("Failed to retrieve generated key for created contestant");
    }

    /**
     * Lists all registered contestant accounts.
     */
    public List<ContestantDTO> getAllContestants() {
        List<ContestantDTO> list = new ArrayList<>();

        if (NeonDataSourceConfig.isMockMode()) {
            for (MockContestant m : MOCK_STORE_BY_ID.values()) {
                list.add(new ContestantDTO(m.id, m.username, m.fullName, m.isLocked, m.createdAt));
            }
            return list;
        }

        String sql = "SELECT id, username, full_name, is_locked, created_at FROM contestants ORDER BY id ASC";
        try (Connection conn = NeonDataSourceConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Timestamp ts = rs.getTimestamp("created_at");
                Instant created = (ts != null) ? ts.toInstant() : Instant.now();
                list.add(new ContestantDTO(
                        rs.getInt("id"),
                        rs.getString("username"),
                        rs.getString("full_name"),
                        rs.getBoolean("is_locked"),
                        created
                ));
            }
        } catch (SQLException e) {
            logger.error("Error loading contestants from DB: {}", e.getMessage());
        }
        return list;
    }

    /**
     * Locks or unlocks a contestant account.
     */
    public boolean setLockStatus(int contestantId, boolean locked) {
        if (NeonDataSourceConfig.isMockMode()) {
            MockContestant mock = MOCK_STORE_BY_ID.get(contestantId);
            if (mock != null) {
                mock.isLocked = locked;
                return true;
            }
            return false;
        }

        String sql = "UPDATE contestants SET is_locked = ? WHERE id = ?";
        try (Connection conn = NeonDataSourceConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, locked);
            ps.setInt(2, contestantId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("Failed to update lock status for contestant {}: {}", contestantId, e.getMessage());
            return false;
        }
    }

    public ContestantDTO getById(int contestantId) {
        if (NeonDataSourceConfig.isMockMode()) {
            MockContestant mock = MOCK_STORE_BY_ID.get(contestantId);
            if (mock != null) {
                return new ContestantDTO(mock.id, mock.username, mock.fullName, mock.isLocked, mock.createdAt);
            }
            return null;
        }

        String sql = "SELECT id, username, full_name, is_locked, created_at FROM contestants WHERE id = ?";
        try (Connection conn = NeonDataSourceConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, contestantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Timestamp ts = rs.getTimestamp("created_at");
                    Instant created = (ts != null) ? ts.toInstant() : Instant.now();
                    return new ContestantDTO(
                            rs.getInt("id"),
                            rs.getString("username"),
                            rs.getString("full_name"),
                            rs.getBoolean("is_locked"),
                            created
                    );
                }
            }
        } catch (SQLException e) {
            logger.error("Error retrieving contestant by id {}: {}", contestantId, e.getMessage());
        }
        return null;
    }

    private static class MockContestant {
        final int id;
        final String username;
        final String passwordHash;
        final String fullName;
        boolean isLocked;
        final Instant createdAt;

        MockContestant(int id, String username, String passwordHash, String fullName, boolean isLocked, Instant createdAt) {
            this.id = id;
            this.username = username;
            this.passwordHash = passwordHash;
            this.fullName = fullName;
            this.isLocked = isLocked;
            this.createdAt = createdAt;
        }
    }
}
