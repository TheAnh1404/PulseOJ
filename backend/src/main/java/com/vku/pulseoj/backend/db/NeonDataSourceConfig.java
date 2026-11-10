package com.vku.pulseoj.backend.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * [TV1] Neon Serverless PostgreSQL HikariCP Connection Pool Manager.
 * Reads NEON_DB_URL from .env file or system environment variables.
 * Automatically initializes the database schema upon first launch.
 */
public class NeonDataSourceConfig {

    private static final Logger logger = LoggerFactory.getLogger(NeonDataSourceConfig.class);

    private static HikariDataSource dataSource;
    private static boolean isInitialized = false;
    private static boolean isMockMode = false;

    private NeonDataSourceConfig() {}

    /**
     * Initializes the HikariCP pool. Thread-safe singleton initialization.
     */
    public static synchronized void init() {
        if (isInitialized) {
            return;
        }

        Dotenv dotenv = null;
        try {
            dotenv = Dotenv.configure().ignoreIfMissing().load();
        } catch (Exception e) {
            logger.debug("Dotenv file not found, checking system properties/env: {}", e.getMessage());
        }

        String dbUrl = getEnvValue(dotenv, "NEON_DB_URL");
        String dbUser = getEnvValue(dotenv, "NEON_DB_USER");
        String dbPassword = getEnvValue(dotenv, "NEON_DB_PASSWORD");
        String mockFlag = getEnvValue(dotenv, "PULSE_OFFLINE_MOCK_DB");

        if ("true".equalsIgnoreCase(mockFlag) || dbUrl == null || dbUrl.isBlank() || dbUrl.contains("<endpoint>")) {
            logger.warn("===============================================================================");
            logger.warn("No active NEON_DB_URL configured in .env. Running in Memory/Mock Fallback Mode.");
            logger.warn("Set NEON_DB_URL=jdbc:postgresql://<neon-endpoint>/pulseoj?sslmode=require in .env");
            logger.warn("===============================================================================");
            isMockMode = true;
            isInitialized = true;
            return;
        }

        try {
            HikariConfig config = new HikariConfig();
            config.setPoolName("PulseOJ-Neon-Pool");
            config.setJdbcUrl(dbUrl);

            if (dbUser != null && !dbUser.isBlank()) {
                config.setUsername(dbUser);
            }
            if (dbPassword != null && !dbPassword.isBlank()) {
                config.setPassword(dbPassword);
            }

            // Connection Pool parameters tailored for Neon Serverless PostgreSQL
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);
            config.setIdleTimeout(30000); // 30s
            config.setMaxLifetime(600000); // 10 min
            config.setConnectionTimeout(10000); // 10s
            config.setLeakDetectionThreshold(15000);

            // PostgreSQL optimizations
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            config.addDataSourceProperty("sslmode", "require");

            dataSource = new HikariDataSource(config);
            logger.info("Successfully established HikariCP pool to Neon PostgreSQL: {}", dbUrl);

            // Execute schema.sql to ensure tables exist
            executeSchemaInit();

            isInitialized = true;
            isMockMode = false;
        } catch (Exception e) {
            logger.error("Failed to connect to Neon PostgreSQL. Falling back to Mock DB: {}", e.getMessage(), e);
            isMockMode = true;
            isInitialized = true;
        }
    }

    private static String getEnvValue(Dotenv dotenv, String key) {
        if (dotenv != null) {
            String val = dotenv.get(key);
            if (val != null && !val.isBlank()) return val;
        }
        String sysEnv = System.getenv(key);
        if (sysEnv != null && !sysEnv.isBlank()) return sysEnv;
        return System.getProperty(key);
    }

    public static synchronized Connection getConnection() throws SQLException {
        if (!isInitialized) {
            init();
        }
        if (isMockMode || dataSource == null) {
            throw new SQLException("Database is running in Mock Mode (No direct JDBC connection)");
        }
        return dataSource.getConnection();
    }

    public static DataSource getDataSource() {
        if (!isInitialized) {
            init();
        }
        return dataSource;
    }

    public static boolean isMockMode() {
        if (!isInitialized) {
            init();
        }
        return isMockMode;
    }

    /**
     * Executes the schema.sql script to ensure tables and indexes are created.
     */
    private static void executeSchemaInit() {
        Path schemaPath = Paths.get("schema.sql");
        if (!Files.exists(schemaPath)) {
            schemaPath = Paths.get("../schema.sql");
        }

        if (Files.exists(schemaPath)) {
            logger.info("Executing database schema migration from: {}", schemaPath.toAbsolutePath());
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                String sqlContent = Files.readString(schemaPath);
                // Split statements by semicolon
                String[] statements = sqlContent.split(";");
                for (String sql : statements) {
                    String trimmed = sql.trim();
                    if (!trimmed.isEmpty()) {
                        try {
                            stmt.execute(trimmed);
                        } catch (SQLException ex) {
                            logger.debug("Schema statement execution note: {}", ex.getMessage());
                        }
                    }
                }
                logger.info("Database schema initialized successfully.");
            } catch (Exception e) {
                logger.warn("Could not execute schema migration: {}", e.getMessage());
            }
        } else {
            logger.info("schema.sql not found at project root. Skipping automatic migration.");
        }
    }

    public static synchronized void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("HikariCP connection pool closed.");
        }
        isInitialized = false;
    }
}
