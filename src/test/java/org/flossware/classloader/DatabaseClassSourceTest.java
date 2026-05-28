package org.flossware.classloader;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Tests for DatabaseClassSource including SQL injection prevention tests.
 */
class DatabaseClassSourceTest {

    private DataSource dataSource;
    private DatabaseClassSource classSource;

    @BeforeEach
    void setUp() throws SQLException {
        // Create in-memory H2 database
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        dataSource = ds;

        // Create test table
        try (Connection conn = dataSource.getConnection()) {
            conn.createStatement().execute(
                "CREATE TABLE class_loader (" +
                "  class_name VARCHAR(255) PRIMARY KEY," +
                "  class_bytes BLOB" +
                ")"
            );

            // Insert test data
            PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO class_loader (class_name, class_bytes) VALUES (?, ?)"
            );
            ps.setString(1, "com.example.TestClass");
            ps.setBytes(2, new byte[]{1, 2, 3, 4, 5});
            ps.executeUpdate();

            ps.setString(1, "com.example.AnotherClass");
            ps.setBytes(2, new byte[]{10, 20, 30});
            ps.executeUpdate();
        }

        classSource = new DatabaseClassSource(dataSource, "class_loader",
                "class_name", "class_bytes");
    }

    @AfterEach
    void tearDown() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            conn.createStatement().execute("DROP TABLE IF EXISTS class_loader");
        }
    }

    @Test
    void testLoadValidClass() throws IOException {
        byte[] classData = classSource.loadClassData("com.example.TestClass");

        assertNotNull(classData);
        assertArrayEquals(new byte[]{1, 2, 3, 4, 5}, classData);
    }

    @Test
    void testCanLoadValidClass() {
        assertTrue(classSource.canLoad("com.example.TestClass"));
        assertTrue(classSource.canLoad("com.example.AnotherClass"));
    }

    @Test
    void testCannotLoadNonexistentClass() {
        assertFalse(classSource.canLoad("com.example.DoesNotExist"));
    }

    @Test
    void testLoadNonexistentClassThrowsException() {
        assertThrows(IOException.class, () -> {
            classSource.loadClassData("com.example.DoesNotExist");
        });
    }

    @Test
    void testSqlInjectionPrevention() {
        // Attempt SQL injection
        String maliciousClassName = "TestClass'; DROP TABLE class_loader; --";

        // Should safely handle the malicious input without executing the DROP
        assertFalse(classSource.canLoad(maliciousClassName));

        // Verify table still exists by loading a valid class
        assertTrue(classSource.canLoad("com.example.TestClass"));
    }

    @Test
    void testInvalidTableNameThrowsException() {
        // Table names with special characters should be rejected
        assertThrows(IllegalArgumentException.class, () -> {
            new DatabaseClassSource(dataSource, "bad_table'; DROP TABLE test; --",
                    "class_name", "class_bytes");
        });
    }

    @Test
    void testInvalidColumnNameThrowsException() {
        // Column names with special characters should be rejected
        assertThrows(IllegalArgumentException.class, () -> {
            new DatabaseClassSource(dataSource, "class_loader",
                    "class_name'; DROP TABLE test; --", "class_bytes");
        });
    }

    @Test
    void testValidIdentifiersWithUnderscores() {
        // Underscores are valid in SQL identifiers
        assertDoesNotThrow(() -> {
            new DatabaseClassSource(dataSource, "class_loader",
                    "class_name", "class_bytes");
        });
    }

    @Test
    void testGetDescription() {
        String description = classSource.getDescription();

        assertTrue(description.contains("DatabaseClassSource"));
        assertTrue(description.contains("class_loader"));
        assertTrue(description.contains("class_name"));
        assertTrue(description.contains("class_bytes"));
    }

    @Test
    void testConstructorNullDataSourceThrowsException() {
        assertThrows(NullPointerException.class, () -> {
            new DatabaseClassSource(null, "table", "col1", "col2");
        });
    }

    @Test
    void testConstructorNullTableNameThrowsException() {
        assertThrows(NullPointerException.class, () -> {
            new DatabaseClassSource(dataSource, null, "col1", "col2");
        });
    }

    @Test
    void testConstructorNullClassNameColumnThrowsException() {
        assertThrows(NullPointerException.class, () -> {
            new DatabaseClassSource(dataSource, "table", null, "col2");
        });
    }

    @Test
    void testConstructorNullClassBytesColumnThrowsException() {
        assertThrows(NullPointerException.class, () -> {
            new DatabaseClassSource(dataSource, "table", "col1", null);
        });
    }

    @Test
    void testLoadClassWithSpecialCharacters() throws SQLException {
        // Insert class with special characters in name
        try (Connection conn = dataSource.getConnection()) {
            PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO class_loader (class_name, class_bytes) VALUES (?, ?)"
            );
            ps.setString(1, "com.example.Class$Inner");
            ps.setBytes(2, new byte[]{99});
            ps.executeUpdate();
        }

        // Should be able to load it safely
        assertTrue(classSource.canLoad("com.example.Class$Inner"));
    }

    @Test
    void testEmptyTableNameThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            new DatabaseClassSource(dataSource, "", "col1", "col2");
        });
    }

    @Test
    void testTableNameStartingWithNumberThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            new DatabaseClassSource(dataSource, "9table", "col1", "col2");
        });
    }

    @Test
    void testColumnNameStartingWithNumberThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            new DatabaseClassSource(dataSource, "table", "9column", "col2");
        });
    }
}
