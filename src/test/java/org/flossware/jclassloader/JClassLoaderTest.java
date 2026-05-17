package org.flossware.jclassloader;

import org.flossware.jclassloader.cache.FileSystemCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class JClassLoaderTest {

    @Test
    void testBuilderRequiresAtLeastOneSource() {
        assertThrows(IllegalStateException.class, () -> {
            JClassLoader.builder().build();
        });
    }

    @Test
    void testBuilderWithLocalSource(@TempDir Path tempDir) throws IOException {
        Path classDir = tempDir.resolve("classes");
        Files.createDirectories(classDir);

        JClassLoader loader = JClassLoader.builder()
            .addLocalSource(classDir.toString())
            .useCache(false)
            .build();

        assertNotNull(loader);
        assertEquals(1, loader.getClassSources().size());
        assertFalse(loader.isCacheEnabled());
    }

    @Test
    void testBuilderWithRemoteSource() {
        JClassLoader loader = JClassLoader.builder()
            .addRemoteSource("https://example.com/classes/")
            .useCache(false)
            .build();

        assertNotNull(loader);
        assertEquals(1, loader.getClassSources().size());
        assertTrue(loader.getClassSources().get(0) instanceof RemoteClassSource);
    }

    @Test
    void testBuilderWithRemoteSourceAndAuth() {
        AuthConfig auth = AuthConfig.basic("user", "pass");
        JClassLoader loader = JClassLoader.builder()
            .addRemoteSource("https://example.com/classes/", auth)
            .useCache(false)
            .build();

        assertNotNull(loader);
        RemoteClassSource source = (RemoteClassSource) loader.getClassSources().get(0);
        assertEquals(AuthConfig.AuthType.BASIC, source.getAuthConfig().getAuthType());
    }

    @Test
    void testBuilderWithCache(@TempDir Path tempDir) throws IOException {
        Path cacheDir = tempDir.resolve("cache");
        FileSystemCache cache = new FileSystemCache(cacheDir);

        JClassLoader loader = JClassLoader.builder()
            .addLocalSource(tempDir.toString())
            .cache(cache)
            .useCache(true)
            .build();

        assertNotNull(loader);
        assertTrue(loader.isCacheEnabled());
        assertNotNull(loader.getCache());
    }

    @Test
    void testLoadClassFromLocal(@TempDir Path tempDir) throws Exception {
        Path classDir = tempDir.resolve("classes");
        Files.createDirectories(classDir);

        Path sourceDir = tempDir.resolve("src");
        Files.createDirectories(sourceDir);
        Path sourceFile = sourceDir.resolve("TestClass.java");
        Files.writeString(sourceFile, "public class TestClass { public String getMessage() { return \"Hello\"; } }");

        ProcessBuilder pb = new ProcessBuilder("javac", "-d", classDir.toString(), sourceFile.toString());
        Process process = pb.start();
        int exitCode = process.waitFor();
        assertEquals(0, exitCode, "javac compilation should succeed");

        JClassLoader loader = JClassLoader.builder()
            .addLocalSource(classDir.toString())
            .useCache(false)
            .build();

        Class<?> loadedClass = loader.loadClass("TestClass");
        assertNotNull(loadedClass);
        assertEquals("TestClass", loadedClass.getSimpleName());

        Object instance = loadedClass.getDeclaredConstructor().newInstance();
        Object result = loadedClass.getMethod("getMessage").invoke(instance);
        assertEquals("Hello", result);
    }

    @Test
    void testLoadClassNotFound() {
        JClassLoader loader = JClassLoader.builder()
            .addRemoteSource("https://example.com/nonexistent/")
            .useCache(false)
            .build();

        assertThrows(ClassNotFoundException.class, () -> {
            loader.loadClass("com.example.NonExistentClass");
        });
    }
}
