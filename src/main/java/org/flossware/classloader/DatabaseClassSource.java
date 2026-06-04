package org.flossware.classloader;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

/**
 * ClassSource implementation for loading classes from a JDBC database.
 * Class bytecode is stored in a table with columns for class name and class bytes.
 *
 * <h2>Performance: Connection Pooling</h2>
 * <p><b>IMPORTANT:</b> This class creates a new database connection for every class load operation.
 * For production use, always provide a pooled DataSource (HikariCP, Apache DBCP, etc.) to avoid
 * connection overhead and resource exhaustion.</p>
 *
 * <h3>Recommended Setup (HikariCP)</h3>
 * <pre>{@code
 * // Create a connection pool
 * HikariConfig config = new HikariConfig();
 * config.setJdbcUrl("jdbc:postgresql://localhost/classes");
 * config.setUsername("user");
 * config.setPassword("password");
 * config.setMaximumPoolSize(10);
 * config.setMinimumIdle(2);
 * config.setConnectionTimeout(30000);
 *
 * DataSource pooledDataSource = new HikariDataSource(config);
 *
 * // Pass the pooled DataSource to DatabaseClassSource
 * DatabaseClassSource source = new DatabaseClassSource(
 *     pooledDataSource,  // Reuses connections from pool
 *     "class_table",
 *     "class_name",
 *     "class_bytes"
 * );
 * }</pre>
 *
 * <h3>Performance Impact</h3>
 * <ul>
 *   <li><b>Without pooling:</b> Loading 100 classes = ~200 new connections (slow)</li>
 *   <li><b>With pooling:</b> Loading 100 classes = reuses 10 pooled connections (fast)</li>
 * </ul>
 *
 * @see javax.sql.DataSource
 */
public class DatabaseClassSource implements ClassSource {
    private final DataSource dataSource;
    private final String tableName;
    private final String classNameColumn;
    private final String classBytesColumn;
    private final String selectQuery;

    /**
     * Creates a database class source.
     *
     * @param dataSource The JDBC DataSource to use
     * @param tableName The table name containing class data
     * @param classNameColumn The column name containing fully qualified class names
     * @param classBytesColumn The column name containing class bytecode (BLOB/BINARY)
     * @throws NullPointerException if any parameter is null
     * @throws IllegalArgumentException if table or column names contain invalid characters
     */
    public DatabaseClassSource(DataSource dataSource, String tableName,
                              String classNameColumn, String classBytesColumn) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource cannot be null");
        this.tableName = validateIdentifier(tableName, "tableName");
        this.classNameColumn = validateIdentifier(classNameColumn, "classNameColumn");
        this.classBytesColumn = validateIdentifier(classBytesColumn, "classBytesColumn");

        this.selectQuery = "SELECT " + classBytesColumn + " FROM " + tableName +
                          " WHERE " + classNameColumn + " = ?";
    }

    /**
     * Validates that an identifier (table/column name) contains only safe characters.
     * Prevents SQL injection by ensuring identifiers are alphanumeric with underscores only.
     */
    private static String validateIdentifier(String identifier, String paramName) {
        Objects.requireNonNull(identifier, paramName + " cannot be null");
        if (!identifier.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
            throw new IllegalArgumentException(paramName + " must be a valid SQL identifier (alphanumeric and underscore only): " + identifier);
        }
        return identifier;
    }

    @Override
    public byte[] loadClassData(String className) throws IOException {
        Objects.requireNonNull(className, "className cannot be null");
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(selectQuery)) {

            stmt.setString(1, className);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getBytes(1);
                } else {
                    throw new IOException("Class not found in database: " + className);
                }
            }

        } catch (SQLException e) {
            throw new IOException("Database error loading class: " + className, e);
        }
    }

    @Override
    public boolean canLoad(String className) {
        Objects.requireNonNull(className, "className cannot be null");
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(selectQuery)) {

            stmt.setString(1, className);

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }

        } catch (SQLException e) {
            return false;
        }
    }

    @Override
    public String getDescription() {
        return "DatabaseClassSource[table=" + tableName + ", classColumn=" +
               classNameColumn + ", bytesColumn=" + classBytesColumn + "]";
    }

    /**
     * Gets the database table name containing class data.
     *
     * @return the table name
     */
    public String getTableName() {
        return tableName;
    }

    /**
     * Gets the column name containing fully qualified class names.
     *
     * @return the class name column
     */
    public String getClassNameColumn() {
        return classNameColumn;
    }

    /**
     * Gets the column name containing class bytecode.
     *
     * @return the class bytes column
     */
    public String getClassBytesColumn() {
        return classBytesColumn;
    }
}
