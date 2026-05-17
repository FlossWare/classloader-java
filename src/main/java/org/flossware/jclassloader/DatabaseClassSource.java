package org.flossware.jclassloader;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

public class DatabaseClassSource implements ClassSource {
    private final DataSource dataSource;
    private final String tableName;
    private final String classNameColumn;
    private final String classBytesColumn;
    private final String selectQuery;

    public DatabaseClassSource(DataSource dataSource, String tableName,
                              String classNameColumn, String classBytesColumn) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource cannot be null");
        this.tableName = Objects.requireNonNull(tableName, "tableName cannot be null");
        this.classNameColumn = Objects.requireNonNull(classNameColumn, "classNameColumn cannot be null");
        this.classBytesColumn = Objects.requireNonNull(classBytesColumn, "classBytesColumn cannot be null");

        this.selectQuery = "SELECT " + classBytesColumn + " FROM " + tableName +
                          " WHERE " + classNameColumn + " = ?";
    }

    @Override
    public byte[] loadClassData(String className) throws IOException {
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

    public String getTableName() {
        return tableName;
    }

    public String getClassNameColumn() {
        return classNameColumn;
    }

    public String getClassBytesColumn() {
        return classBytesColumn;
    }
}
