package edu.vit.hcms.persistence.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import edu.vit.hcms.core.config.AppConfig;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;

/**
 * Manages the HikariCP connection pool and Flyway migrations.
 * Implements AutoCloseable for graceful shutdown.
 *
 * <p>Thread-safe: HikariDataSource is inherently thread-safe.
 * The pool is configured via AppConfig environment variables.
 */
public class DatabaseManager implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(DatabaseManager.class);
    private final HikariDataSource dataSource;

    /**
     * Initializes the connection pool and runs Flyway migrations.
     * Retries database connection on startup to handle Docker compose ordering.
     */
    public DatabaseManager() {
        this(AppConfig.getInstance().get("DB_URL"),
             AppConfig.getInstance().get("DB_USER"),
             AppConfig.getInstance().get("DB_PASSWORD"));
    }

    /**
     * Overloaded constructor for custom database configurations (e.g., Testcontainers or test suites).
     *
     * @param jdbcUrl the JDBC connection string
     * @param username database username
     * @param password database password
     */
    public DatabaseManager(String jdbcUrl, String username, String password) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(jdbcUrl);
        hikariConfig.setUsername(username);
        hikariConfig.setPassword(password);
        hikariConfig.setMaximumPoolSize(10);
        hikariConfig.setMinimumIdle(2);
        hikariConfig.setConnectionTimeout(30_000);
        hikariConfig.setIdleTimeout(600_000);
        hikariConfig.setMaxLifetime(1_800_000);
        hikariConfig.setPoolName("HCMSPool");

        // MySQL-specific optimizations
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");

        this.dataSource = createWithRetry(hikariConfig, 5, 3000);
        log.info("HikariCP connection pool initialized for URL: {}", jdbcUrl);

        runMigrations();
    }

    /**
     * Retries DataSource creation to handle DB startup delays in Docker compose.
     */
    private HikariDataSource createWithRetry(HikariConfig config, int maxRetries, long delayMs) {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return new HikariDataSource(config);
            } catch (Exception e) {
                log.warn("Database connection attempt {}/{} failed: {}", attempt, maxRetries, e.getMessage());
                if (attempt == maxRetries) {
                    throw e;
                }
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted during DB connection retry", ie);
                }
            }
        }
        throw new IllegalStateException("Should not reach here");
    }

    /**
     * Runs Flyway migrations. In dev profile, also applies seed data.
     */
    private void runMigrations() {
        log.info("Running Flyway migrations...");
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .load();
        flyway.migrate();
        log.info("Flyway migrations completed");
    }

    /** Returns the underlying DataSource for DAO use. */
    public DataSource getDataSource() {
        return dataSource;
    }

    /** Returns pool statistics for the metrics endpoint. */
    public String getPoolStats() {
        if (dataSource == null || dataSource.isClosed()) {
            return "closed";
        }
        var mxBean = dataSource.getHikariPoolMXBean();
        if (mxBean == null) {
            return "active=0, idle=0, total=0, waiting=0";
        }
        return String.format("active=%d, idle=%d, total=%d, waiting=%d",
                mxBean.getActiveConnections(),
                mxBean.getIdleConnections(),
                mxBean.getTotalConnections(),
                mxBean.getThreadsAwaitingConnection());
    }

    /** Checks if the database is reachable. */
    public boolean isHealthy() {
        try (var conn = dataSource.getConnection()) {
            return conn.isValid(3);
        } catch (Exception e) {
            log.error("Health check failed", e);
            return false;
        }
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            log.info("HikariCP connection pool closed");
        }
    }
}
