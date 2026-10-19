package edu.vit.hcms.persistence.db;

import edu.vit.hcms.core.exception.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Transaction management helper using ThreadLocal to share a connection across DAO calls.
 * Supports explicit commit/rollback with try-with-resources safety.
 *
 * <p>Thread-safety: each thread gets its own Connection via ThreadLocal.
 * The caller must invoke {@link #begin()}, then {@link #commit()} or {@link #rollback()}.
 *
 * <p>Usage:
 * <pre>
 *   transactionManager.begin();
 *   try {
 *       dao1.save(...);
 *       dao2.update(...);
 *       transactionManager.commit();
 *   } catch (Exception e) {
 *       transactionManager.rollback();
 *       throw e;
 *   }
 * </pre>
 */
public class TransactionManager {

    private static final Logger log = LoggerFactory.getLogger(TransactionManager.class);
    private final DataSource dataSource;

    // ThreadLocal ensures each thread has its own transactional connection
    private final ThreadLocal<Connection> connectionHolder = new ThreadLocal<>();

    public TransactionManager(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Begins a new transaction on the current thread.
     * Obtains a connection from the pool and disables auto-commit.
     *
     * @throws DataAccessException if a connection cannot be obtained
     */
    public void begin() {
        try {
            Connection conn = dataSource.getConnection();
            conn.setAutoCommit(false);
            connectionHolder.set(conn);
            log.debug("Transaction started on thread {}", Thread.currentThread().getName());
        } catch (SQLException e) {
            throw new DataAccessException("Failed to begin transaction", e);
        }
    }

    /**
     * Commits the current transaction and returns the connection to the pool.
     *
     * @throws DataAccessException if commit fails
     */
    public void commit() {
        Connection conn = connectionHolder.get();
        if (conn == null) {
            throw new DataAccessException("No active transaction to commit");
        }
        try {
            conn.commit();
            log.debug("Transaction committed on thread {}", Thread.currentThread().getName());
        } catch (SQLException e) {
            throw new DataAccessException("Failed to commit transaction", e);
        } finally {
            cleanup(conn);
        }
    }

    /**
     * Rolls back the current transaction and returns the connection to the pool.
     */
    public void rollback() {
        Connection conn = connectionHolder.get();
        if (conn == null) {
            return;
        }
        try {
            conn.rollback();
            log.debug("Transaction rolled back on thread {}", Thread.currentThread().getName());
        } catch (SQLException e) {
            log.error("Failed to rollback transaction", e);
        } finally {
            cleanup(conn);
        }
    }

    /**
     * Returns the current transactional connection, or a new auto-commit connection
     * if no transaction is active. DAO methods use this to get connections.
     *
     * @return a JDBC Connection
     */
    public Connection getConnection() {
        Connection conn = connectionHolder.get();
        if (conn != null) {
            return conn;
        }
        try {
            return dataSource.getConnection();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to get connection", e);
        }
    }

    /**
     * Returns true if the current thread has an active transaction.
     */
    public boolean isTransactionActive() {
        return connectionHolder.get() != null;
    }

    /**
     * Releases a connection back to the pool. Only call this for non-transactional connections.
     */
    public void releaseConnection(Connection conn) {
        if (conn != null && connectionHolder.get() != conn) {
            try {
                conn.close();
            } catch (SQLException e) {
                log.error("Failed to release connection", e);
            }
        }
    }

    private void cleanup(Connection conn) {
        connectionHolder.remove();
        try {
            conn.setAutoCommit(true);
            conn.close();
        } catch (SQLException e) {
            log.error("Failed to cleanup connection", e);
        }
    }
}
