package edu.vit.hcms.persistence.db;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Functional interface for mapping a JDBC ResultSet row to a domain object.
 * Used by DAO implementations to decouple SQL execution from object construction.
 *
 * @param <T> the domain type produced by this mapper
 */
@FunctionalInterface
public interface RowMapper<T> {

    /**
     * Maps the current row of a ResultSet to a domain object.
     * The cursor is already positioned on the current row.
     *
     * @param rs the ResultSet with cursor on the current row
     * @return the mapped domain object
     * @throws SQLException if a column read fails
     */
    T mapRow(ResultSet rs) throws SQLException;
}
