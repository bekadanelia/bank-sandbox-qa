package io.github.bekadanelia.sandboxbank.db;

import io.github.bekadanelia.sandboxbank.config.TestConfig;
import io.github.bekadanelia.sandboxbank.config.TestConfig.Credentials;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Plain JDBC access to the bank's PostgreSQL database.
 *
 * <p>There are two doors. {@link #reader()} connects as a role that can only read, and every
 * verification query goes through it, so a test can never repair the data it is checking.
 * {@link #owner()} exists for the few tests that try to write bad data and expect to be refused.
 */
public final class Database {

    private final Credentials credentials;

    private Database(Credentials credentials) {
        this.credentials = credentials;
    }

    public static Database reader() {
        return new Database(TestConfig.dbReader());
    }

    public static Database owner() {
        return new Database(TestConfig.dbOwner());
    }

    /** Statements that run on one connection inside one transaction. */
    @FunctionalInterface
    public interface Work {
        void run(Connection connection) throws SQLException;
    }

    public List<Row> query(String sql, Object... parameters) {
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) {
                statement.setObject(index + 1, parameters[index]);
            }
            try (ResultSet results = statement.executeQuery()) {
                return read(results);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Query failed: %s%n%s".formatted(e.getMessage(), sql), e);
        }
    }

    /** The only row of a query that must return exactly one. */
    public Row one(String sql, Object... parameters) {
        List<Row> rows = query(sql, parameters);
        if (rows.size() != 1) {
            throw new AssertionError("Expected exactly one row but got %d from:%n%s".formatted(rows.size(), sql));
        }
        return rows.getFirst();
    }

    /**
     * Runs the work in a transaction that is rolled back whatever happens, so write attempts
     * leave no trace even when the database unexpectedly accepts them.
     */
    public void inRolledBackTransaction(Work work) throws SQLException {
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try {
                work.run(connection);
            } finally {
                connection.rollback();
            }
        }
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(TestConfig.dbUrl(), credentials.user(), credentials.password());
    }

    private static List<Row> read(ResultSet results) throws SQLException {
        ResultSetMetaData columns = results.getMetaData();
        List<Row> rows = new ArrayList<>();
        while (results.next()) {
            Map<String, Object> values = new LinkedHashMap<>();
            for (int index = 1; index <= columns.getColumnCount(); index++) {
                values.put(columns.getColumnLabel(index), results.getObject(index));
            }
            rows.add(new Row(values));
        }
        return rows;
    }
}
