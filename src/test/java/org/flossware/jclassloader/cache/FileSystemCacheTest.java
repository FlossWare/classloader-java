package org.flossware.jclassloader.cache;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;


/**
 * Tests for FileSystemCache including security (path traversal) and concurrency tests.
 */
class FileSystemCacheTest {

    @Test
    void testPutAndGet(@TempDir Path tempDir) throws IOException {
        FileSystemCache cache = new FileSystemCache(tempDir);
        byte[] classData = {1, 2, 3, 4, 5};

        cache.put("com.example.TestClass", classData);
        byte[] retrieved = cache.get("com.example.TestClass");

        assertArrayEquals(classData, retrieved);
    }

    @Test
    void testContains(@TempDir Path tempDir) throws IOException {
        FileSystemCache cache = new FileSystemCache(tempDir);
        byte[] classData = {1, 2, 3};

        assertFalse(cache.contains("com.example.TestClass"));

        cache.put("com.example.TestClass", classData);

        assertTrue(cache.contains("com.example.TestClass"));
    }

    @Test
    void testGetNonexistentReturnsNull(@TempDir Path tempDir) throws IOException {
        FileSystemCache cache = new FileSystemCache(tempDir);

        assertNull(cache.get("com.example.DoesNotExist"));
    }

    @Test
    void testRemove(@TempDir Path tempDir) throws IOException {
        FileSystemCache cache = new FileSystemCache(tempDir);
        byte[] classData = {1, 2, 3};

        cache.put("com.example.TestClass", classData);
        assertTrue(cache.contains("com.example.TestClass"));

        cache.remove("com.example.TestClass");
        assertFalse(cache.contains("com.example.TestClass"));
    }

    @Test
    void testClear(@TempDir Path tempDir) throws IOException {
        FileSystemCache cache = new FileSystemCache(tempDir);

        cache.put("com.example.Class1", new byte[]{1});
        cache.put("com.example.Class2", new byte[]{2});
        cache.put("com.example.Class3", new byte[]{3});

        assertTrue(cache.contains("com.example.Class1"));
        assertTrue(cache.contains("com.example.Class2"));
        assertTrue(cache.contains("com.example.Class3"));

        cache.clear();

        assertFalse(cache.contains("com.example.Class1"));
        assertFalse(cache.contains("com.example.Class2"));
        assertFalse(cache.contains("com.example.Class3"));
    }

    @Test
    void testPathTraversalWithDotDot(@TempDir Path tempDir) throws IOException {
        FileSystemCache cache = new FileSystemCache(tempDir);

        // Attempt path traversal with ..
        assertThrows(IOException.class, () -> {
            cache.put("com.example..secret.Password", new byte[]{1, 2, 3});
        });

        assertFalse(cache.contains("com.example..secret.Password"));
    }

    @Test
    void testPathTraversalWithSlash(@TempDir Path tempDir) throws IOException {
        FileSystemCache cache = new FileSystemCache(tempDir);

        // Attempt path traversal with /
        assertThrows(IOException.class, () -> {
            cache.put("com/example/TestClass", new byte[]{1, 2, 3});
        });

        assertFalse(cache.contains("com/example/TestClass"));
    }

    @Test
    void testPathTraversalWithBackslash(@TempDir Path tempDir) throws IOException {
        FileSystemCache cache = new FileSystemCache(tempDir);

        // Attempt path traversal with \
        assertThrows(IOException.class, () -> {
            cache.put("com\\example\\TestClass", new byte[]{1, 2, 3});
        });

        assertFalse(cache.contains("com\\example\\TestClass"));
    }

    @Test
    void testPathTraversalGet(@TempDir Path tempDir) throws IOException {
        FileSystemCache cache = new FileSystemCache(tempDir);

        // Attempt path traversal on get
        assertNull(cache.get("....etc.passwd"));
    }

    @Test
    void testPutNullClassNameThrowsException(@TempDir Path tempDir) throws IOException {
        FileSystemCache cache = new FileSystemCache(tempDir);

        assertThrows(NullPointerException.class, () -> {
            cache.put(null, new byte[]{1, 2, 3});
        });
    }

    @Test
    void testPutNullDataThrowsException(@TempDir Path tempDir) throws IOException {
        FileSystemCache cache = new FileSystemCache(tempDir);

        assertThrows(NullPointerException.class, () -> {
            cache.put("com.example.TestClass", null);
        });
    }

    @Test
    void testConstructorCreatesDirectory(@TempDir Path tempDir) throws IOException {
        Path cacheDir = tempDir.resolve("cache");
        assertFalse(Files.exists(cacheDir));

        FileSystemCache cache = new FileSystemCache(cacheDir);

        assertTrue(Files.exists(cacheDir));
        assertTrue(Files.isDirectory(cacheDir));
    }

    @Test
    void testConstructorWithString(@TempDir Path tempDir) throws IOException {
        Path cacheDir = tempDir.resolve("cache");
        FileSystemCache cache = new FileSystemCache(cacheDir.toString());

        assertTrue(Files.exists(cacheDir));
    }

    @Test
    void testConstructorNullThrowsException() {
        assertThrows(NullPointerException.class, () -> {
            new FileSystemCache((Path) null);
        });
    }

    @Test
    void testGetCacheDirectory(@TempDir Path tempDir) throws IOException {
        FileSystemCache cache = new FileSystemCache(tempDir);

        assertEquals(tempDir.toAbsolutePath().normalize(), cache.getCacheDirectory());
    }

    @Test
    void testAtomicWrite(@TempDir Path tempDir) throws IOException {
        FileSystemCache cache = new FileSystemCache(tempDir);
        byte[] classData = {1, 2, 3, 4, 5};

        // Put twice - second should overwrite atomically
        cache.put("com.example.TestClass", new byte[]{1});
        cache.put("com.example.TestClass", classData);

        byte[] retrieved = cache.get("com.example.TestClass");
        assertArrayEquals(classData, retrieved);
    }

    @Test
    void testNestedPackages(@TempDir Path tempDir) throws IOException {
        FileSystemCache cache = new FileSystemCache(tempDir);
        byte[] classData = {1, 2, 3};

        cache.put("com.example.deep.nested.pkg.TestClass", classData);

        assertTrue(cache.contains("com.example.deep.nested.pkg.TestClass"));
        assertArrayEquals(classData, cache.get("com.example.deep.nested.pkg.TestClass"));
    }

    @Test
    void testConcurrentPut(@TempDir Path tempDir) throws IOException, InterruptedException {
        FileSystemCache cache = new FileSystemCache(tempDir);

        // Simulate concurrent writes
        Thread t1 = new Thread(() -> {
            try {
                for (int i = 0; i < 100; i++) {
                    cache.put("com.example.Class" + i, new byte[]{(byte) i});
                }
            } catch (IOException e) {
                fail("Thread 1 failed: " + e.getMessage());
            }
        });

        Thread t2 = new Thread(() -> {
            try {
                for (int i = 100; i < 200; i++) {
                    cache.put("com.example.Class" + i, new byte[]{(byte) i});
                }
            } catch (IOException e) {
                fail("Thread 2 failed: " + e.getMessage());
            }
        });

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        // Verify all writes succeeded
        for (int i = 0; i < 200; i++) {
            assertTrue(cache.contains("com.example.Class" + i),
                    "Missing class " + i);
        }
    }
}
