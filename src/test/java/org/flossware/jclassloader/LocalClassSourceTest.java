package org.flossware.jclassloader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for LocalClassSource including security (path traversal) tests.
 */
class LocalClassSourceTest {

    @Test
    void testLoadValidClass(@TempDir Path tempDir) throws IOException {
        // Create test class file
        Path packageDir = tempDir.resolve("com/example");
        Files.createDirectories(packageDir);
        byte[] classData = {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE}; // Mock class data
        Files.write(packageDir.resolve("TestClass.class"), classData);

        LocalClassSource source = new LocalClassSource(tempDir);

        // Should successfully load
        byte[] loaded = source.loadClassData("com.example.TestClass");
        assertArrayEquals(classData, loaded);
    }

    @Test
    void testCanLoadValidClass(@TempDir Path tempDir) throws IOException {
        // Create test class file
        Path packageDir = tempDir.resolve("com/example");
        Files.createDirectories(packageDir);
        Files.write(packageDir.resolve("TestClass.class"), new byte[]{1, 2, 3});

        LocalClassSource source = new LocalClassSource(tempDir);

        assertTrue(source.canLoad("com.example.TestClass"));
    }

    @Test
    void testCannotLoadNonexistentClass(@TempDir Path tempDir) {
        LocalClassSource source = new LocalClassSource(tempDir);

        assertFalse(source.canLoad("com.example.DoesNotExist"));
    }

    @Test
    void testLoadNonexistentClassThrowsException(@TempDir Path tempDir) {
        LocalClassSource source = new LocalClassSource(tempDir);

        assertThrows(IOException.class, () -> {
            source.loadClassData("com.example.DoesNotExist");
        });
    }

    @Test
    void testPathTraversalWithDotDot(@TempDir Path tempDir) {
        LocalClassSource source = new LocalClassSource(tempDir);

        // Attempt path traversal with ..
        assertThrows(IOException.class, () -> {
            source.loadClassData("com.example..secret.Password");
        });

        assertFalse(source.canLoad("com.example..secret.Password"));
    }

    @Test
    void testPathTraversalWithAbsolutePath(@TempDir Path tempDir) {
        LocalClassSource source = new LocalClassSource(tempDir);

        // Attempt path traversal with /
        assertThrows(IOException.class, () -> {
            source.loadClassData("com/example/TestClass");
        });

        assertFalse(source.canLoad("com/example/TestClass"));
    }

    @Test
    void testPathTraversalWithBackslash(@TempDir Path tempDir) {
        LocalClassSource source = new LocalClassSource(tempDir);

        // Attempt path traversal with \
        assertThrows(IOException.class, () -> {
            source.loadClassData("com\\example\\TestClass");
        });

        assertFalse(source.canLoad("com\\example\\TestClass"));
    }

    @Test
    void testPathTraversalAttemptToEscapeBaseDir(@TempDir Path tempDir) {
        LocalClassSource source = new LocalClassSource(tempDir);

        // Attempt to escape base directory
        assertThrows(IOException.class, () -> {
            source.loadClassData("....etc.passwd");
        });

        assertFalse(source.canLoad("....etc.passwd"));
    }

    @Test
    void testGetDescription(@TempDir Path tempDir) {
        LocalClassSource source = new LocalClassSource(tempDir);

        String description = source.getDescription();
        assertTrue(description.startsWith("LocalClassSource["));
        assertTrue(description.contains(tempDir.toString()));
    }

    @Test
    void testGetBasePath(@TempDir Path tempDir) {
        LocalClassSource source = new LocalClassSource(tempDir);

        assertEquals(tempDir.toAbsolutePath().normalize(), source.getBasePath());
    }

    @Test
    void testConstructorWithString(@TempDir Path tempDir) {
        LocalClassSource source = new LocalClassSource(tempDir.toString());

        assertNotNull(source);
        assertEquals(tempDir.toAbsolutePath().normalize(), source.getBasePath());
    }

    @Test
    void testConstructorNullPathThrowsException() {
        assertThrows(NullPointerException.class, () -> {
            new LocalClassSource((Path) null);
        });
    }

    @Test
    void testNestedPackages(@TempDir Path tempDir) throws IOException {
        // Create deeply nested package
        Path packageDir = tempDir.resolve("com/example/deep/nested/pkg");
        Files.createDirectories(packageDir);
        byte[] classData = {1, 2, 3, 4, 5};
        Files.write(packageDir.resolve("DeepClass.class"), classData);

        LocalClassSource source = new LocalClassSource(tempDir);

        byte[] loaded = source.loadClassData("com.example.deep.nested.pkg.DeepClass");
        assertArrayEquals(classData, loaded);
    }

    @Test
    void testLoadClassDataFromDirectory(@TempDir Path tempDir) throws IOException {
        // Create a directory with the .class extension (should fail)
        Path packageDir = tempDir.resolve("com/example");
        Files.createDirectories(packageDir);
        Files.createDirectory(packageDir.resolve("NotAClass.class"));

        LocalClassSource source = new LocalClassSource(tempDir);

        // Should throw because it's a directory, not a file
        assertThrows(IOException.class, () -> {
            source.loadClassData("com.example.NotAClass");
        });
    }
}
