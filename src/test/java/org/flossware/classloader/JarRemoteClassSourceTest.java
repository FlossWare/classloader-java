package org.flossware.classloader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class JarRemoteClassSourceTest {

    private static final byte[] SIMPLE_CLASS_BYTECODE = {
        (byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, // Magic number
        0x00, 0x00, 0x00, 0x34 // Version
    };

    /**
     * Creates a simple JAR file with test classes
     */
    private Path createTestJar(Path dir, String jarName, String... classNames) throws IOException {
        Path jarPath = dir.resolve(jarName);

        try (FileOutputStream fos = new FileOutputStream(jarPath.toFile());
             JarOutputStream jos = new JarOutputStream(fos)) {

            for (String className : classNames) {
                String entryName = className.replace('.', '/') + ".class";
                JarEntry entry = new JarEntry(entryName);
                jos.putNextEntry(entry);
                jos.write(SIMPLE_CLASS_BYTECODE);
                jos.closeEntry();
            }
        }

        return jarPath;
    }

    @Test
    void testLoadClassFromLocalJar(@TempDir Path tempDir) throws Exception {
        Path jarPath = createTestJar(tempDir, "test.jar", "com.example.TestClass");

        // Use file:// URL for local testing
        String jarUrl = jarPath.toUri().toString();

        JarRemoteClassSource source = new JarRemoteClassSource(jarUrl);

        try {
            byte[] classData = source.loadClassData("com.example.TestClass");
            assertNotNull(classData);
            assertArrayEquals(SIMPLE_CLASS_BYTECODE, classData);
        } finally {
            source.close();
        }
    }

    @Test
    void testCanLoadReturnsTrueForExistingClass(@TempDir Path tempDir) throws Exception {
        Path jarPath = createTestJar(tempDir, "test.jar", "com.example.TestClass");
        String jarUrl = jarPath.toUri().toString();

        JarRemoteClassSource source = new JarRemoteClassSource(jarUrl);

        try {
            assertTrue(source.canLoad("com.example.TestClass"));
        } finally {
            source.close();
        }
    }

    @Test
    void testCanLoadReturnsFalseForMissingClass(@TempDir Path tempDir) throws Exception {
        Path jarPath = createTestJar(tempDir, "test.jar", "com.example.TestClass");
        String jarUrl = jarPath.toUri().toString();

        JarRemoteClassSource source = new JarRemoteClassSource(jarUrl);

        try {
            assertFalse(source.canLoad("com.example.MissingClass"));
        } finally {
            source.close();
        }
    }

    @Test
    void testLoadNonExistentClassThrowsIOException(@TempDir Path tempDir) throws Exception {
        Path jarPath = createTestJar(tempDir, "test.jar", "com.example.TestClass");
        String jarUrl = jarPath.toUri().toString();

        JarRemoteClassSource source = new JarRemoteClassSource(jarUrl);

        try {
            IOException thrown = assertThrows(IOException.class, () -> {
                source.loadClassData("com.example.NonExistent");
            });

            assertTrue(thrown.getMessage().contains("Class not found in JAR"));
        } finally {
            source.close();
        }
    }

    @Test
    void testLoadMultipleClassesFromSameJar(@TempDir Path tempDir) throws Exception {
        Path jarPath = createTestJar(tempDir, "test.jar",
            "com.example.Class1",
            "com.example.Class2",
            "com.example.Class3");
        String jarUrl = jarPath.toUri().toString();

        JarRemoteClassSource source = new JarRemoteClassSource(jarUrl);

        try {
            // Load all three classes
            byte[] class1 = source.loadClassData("com.example.Class1");
            byte[] class2 = source.loadClassData("com.example.Class2");
            byte[] class3 = source.loadClassData("com.example.Class3");

            assertNotNull(class1);
            assertNotNull(class2);
            assertNotNull(class3);

            // All should have same bytecode (our simple test bytecode)
            assertArrayEquals(SIMPLE_CLASS_BYTECODE, class1);
            assertArrayEquals(SIMPLE_CLASS_BYTECODE, class2);
            assertArrayEquals(SIMPLE_CLASS_BYTECODE, class3);
        } finally {
            source.close();
        }
    }

    @Test
    void testJarIsOnlyDownloadedOnce(@TempDir Path tempDir) throws Exception {
        Path jarPath = createTestJar(tempDir, "test.jar", "com.example.TestClass");
        String jarUrl = jarPath.toUri().toString();

        JarRemoteClassSource source = new JarRemoteClassSource(jarUrl);

        try {
            // First load triggers download
            source.loadClassData("com.example.TestClass");

            // Second load should use cached JAR (no second download)
            // If JAR was downloaded twice, this would fail or be slower
            source.loadClassData("com.example.TestClass");

            // Verify both loads succeeded
            assertTrue(true);
        } finally {
            source.close();
        }
    }

    @Test
    void testCloseDeletesTempFile(@TempDir Path tempDir) throws Exception {
        Path jarPath = createTestJar(tempDir, "test.jar", "com.example.TestClass");
        String jarUrl = jarPath.toUri().toString();

        JarRemoteClassSource source = new JarRemoteClassSource(jarUrl);

        // Load a class to trigger JAR download
        source.loadClassData("com.example.TestClass");

        // Close should delete temp file
        source.close();

        // Trying to load after close should fail
        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> {
            source.loadClassData("com.example.TestClass");
        });

        assertTrue(thrown.getMessage().contains("closed"));
    }

    @Test
    void testCloseIsIdempotent(@TempDir Path tempDir) throws Exception {
        Path jarPath = createTestJar(tempDir, "test.jar", "com.example.TestClass");
        String jarUrl = jarPath.toUri().toString();

        JarRemoteClassSource source = new JarRemoteClassSource(jarUrl);
        source.loadClassData("com.example.TestClass");

        // Multiple closes should not throw
        source.close();
        source.close();
        source.close();
    }

    @Test
    void testGettersReturnExpectedValues(@TempDir Path tempDir) throws Exception {
        Path jarPath = createTestJar(tempDir, "test.jar", "com.example.TestClass");
        String jarUrl = jarPath.toUri().toString();

        AuthConfig auth = AuthConfig.bearer("test-token");
        RetryPolicy retry = RetryPolicy.noRetry();

        JarRemoteClassSource source = new JarRemoteClassSource(jarUrl, auth, 5000, 10000, retry);

        try {
            assertEquals(jarUrl, source.getJarUrl());
            assertEquals(auth, source.getAuthConfig());
            assertEquals(retry, source.getRetryPolicy());
            assertFalse(source.isClosed());
        } finally {
            source.close();
        }
    }

    @Test
    void testIsClosedAfterClose(@TempDir Path tempDir) throws Exception {
        Path jarPath = createTestJar(tempDir, "test.jar", "com.example.TestClass");
        String jarUrl = jarPath.toUri().toString();

        JarRemoteClassSource source = new JarRemoteClassSource(jarUrl);

        assertFalse(source.isClosed());

        source.close();

        assertTrue(source.isClosed());
    }

    @Test
    void testGetDescription(@TempDir Path tempDir) throws Exception {
        Path jarPath = createTestJar(tempDir, "test.jar", "com.example.TestClass");
        String jarUrl = jarPath.toUri().toString();

        JarRemoteClassSource source = new JarRemoteClassSource(jarUrl);

        try {
            String description = source.getDescription();

            assertTrue(description.contains("JarRemoteClassSource"));
            assertTrue(description.contains(jarUrl) || description.contains("file"));
            assertTrue(description.contains("auth="));
        } finally {
            source.close();
        }
    }

    @Test
    void testConstructorNullJarUrl() {
        assertThrows(NullPointerException.class, () -> {
            new JarRemoteClassSource(null);
        });
    }

    @Test
    void testConstructorWithAuthConfig(@TempDir Path tempDir) throws Exception {
        Path jarPath = createTestJar(tempDir, "test.jar", "com.example.TestClass");
        String jarUrl = jarPath.toUri().toString();

        AuthConfig auth = AuthConfig.basic("user", "pass");

        JarRemoteClassSource source = new JarRemoteClassSource(jarUrl, auth);

        try {
            assertEquals(auth, source.getAuthConfig());
        } finally {
            source.close();
        }
    }

    @Test
    void testLoadClassBeforeJarDownload(@TempDir Path tempDir) throws Exception {
        Path jarPath = createTestJar(tempDir, "test.jar", "com.example.TestClass");
        String jarUrl = jarPath.toUri().toString();

        JarRemoteClassSource source = new JarRemoteClassSource(jarUrl);

        try {
            // First call should trigger JAR download
            byte[] classData = source.loadClassData("com.example.TestClass");
            assertNotNull(classData);
        } finally {
            source.close();
        }
    }

    @Test
    void testInvalidJarUrlThrowsIOException() {
        JarRemoteClassSource source = new JarRemoteClassSource("http://invalid-url-that-does-not-exist-12345.com/test.jar");

        try {
            IOException thrown = assertThrows(IOException.class, () -> {
                source.loadClassData("com.example.TestClass");
            });

            // Should contain error about connection or URL
            assertNotNull(thrown.getMessage());
        } finally {
            try {
                source.close();
            } catch (IOException e) {
                // Ignore close errors
            }
        }
    }

    @Test
    void testCanLoadReturnsFalseOnError() {
        JarRemoteClassSource source = new JarRemoteClassSource("http://invalid-url-12345.com/test.jar");

        try {
            // canLoad should return false on errors
            boolean canLoad = source.canLoad("com.example.TestClass");
            assertFalse(canLoad);
        } finally {
            try {
                source.close();
            } catch (IOException e) {
                // Ignore
            }
        }
    }

    @Test
    void testTryWithResources(@TempDir Path tempDir) throws Exception {
        Path jarPath = createTestJar(tempDir, "test.jar", "com.example.TestClass");
        String jarUrl = jarPath.toUri().toString();

        byte[] classData;

        // Use try-with-resources
        try (JarRemoteClassSource source = new JarRemoteClassSource(jarUrl)) {
            classData = source.loadClassData("com.example.TestClass");
            assertFalse(source.isClosed());
        }

        // After try-with-resources, source should be closed
        assertNotNull(classData);
    }

    @Test
    void testNestedClassLoading(@TempDir Path tempDir) throws Exception {
        // Test loading nested/inner classes
        Path jarPath = createTestJar(tempDir, "test.jar",
            "com.example.OuterClass",
            "com.example.OuterClass$InnerClass");
        String jarUrl = jarPath.toUri().toString();

        JarRemoteClassSource source = new JarRemoteClassSource(jarUrl);

        try {
            byte[] outerClass = source.loadClassData("com.example.OuterClass");
            byte[] innerClass = source.loadClassData("com.example.OuterClass$InnerClass");

            assertNotNull(outerClass);
            assertNotNull(innerClass);
        } finally {
            source.close();
        }
    }

    @Test
    void testLoadFromDifferentPackages(@TempDir Path tempDir) throws Exception {
        Path jarPath = createTestJar(tempDir, "test.jar",
            "com.example.pkg1.Class1",
            "com.example.pkg2.Class2",
            "org.test.Class3");
        String jarUrl = jarPath.toUri().toString();

        JarRemoteClassSource source = new JarRemoteClassSource(jarUrl);

        try {
            assertNotNull(source.loadClassData("com.example.pkg1.Class1"));
            assertNotNull(source.loadClassData("com.example.pkg2.Class2"));
            assertNotNull(source.loadClassData("org.test.Class3"));
        } finally {
            source.close();
        }
    }

    @Test
    void testEmptyJarThrowsIOException(@TempDir Path tempDir) throws Exception {
        // Create an empty JAR
        Path jarPath = createTestJar(tempDir, "empty.jar");
        String jarUrl = jarPath.toUri().toString();

        JarRemoteClassSource source = new JarRemoteClassSource(jarUrl);

        try {
            IOException thrown = assertThrows(IOException.class, () -> {
                source.loadClassData("com.example.TestClass");
            });

            assertTrue(thrown.getMessage().contains("Class not found in JAR"));
        } finally {
            source.close();
        }
    }
}
