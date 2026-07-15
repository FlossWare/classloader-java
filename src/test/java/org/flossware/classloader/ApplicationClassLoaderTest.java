package org.flossware.classloader;

import org.flossware.classloader.cache.ClassCache;
import org.flossware.classloader.cache.FileSystemCache;
import org.flossware.classloader.delegation.ParentFirstDelegation;
import org.flossware.classloader.delegation.ParentLastDelegation;
import org.flossware.classloader.lifecycle.ClassLoadEvent;
import org.flossware.classloader.lifecycle.ClassLoaderLifecycleListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


class ApplicationClassLoaderTest {

    @Test
    void testBuilderRequiresAtLeastOneSource() {
        assertThrows(IllegalStateException.class, () -> {
            ApplicationClassLoader.builder().build();
        });
    }

    @Test
    void testBuilderWithLocalSource(@TempDir Path tempDir) throws IOException {
        Path classDir = tempDir.resolve("classes");
        Files.createDirectories(classDir);

        ApplicationClassLoader loader = ApplicationClassLoader.builder()
            .addLocalSource(classDir.toString())
            .useCache(false)
            .build();

        assertNotNull(loader);
        assertEquals(1, loader.getClassSources().size());
        assertFalse(loader.isCacheEnabled());
    }

    @Test
    void testBuilderWithRemoteSource() {
        ApplicationClassLoader loader = ApplicationClassLoader.builder()
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
        ApplicationClassLoader loader = ApplicationClassLoader.builder()
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

        ApplicationClassLoader loader = ApplicationClassLoader.builder()
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

        ApplicationClassLoader loader = ApplicationClassLoader.builder()
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
        ApplicationClassLoader loader = ApplicationClassLoader.builder()
            .addRemoteSource("https://example.com/nonexistent/")
            .useCache(false)
            .build();

        assertThrows(ClassNotFoundException.class, () -> {
            loader.loadClass("com.example.NonExistentClass");
        });
    }

    @Test
    void testLoadClassAfterClose(@TempDir Path tempDir) throws Exception {
        Path classDir = tempDir.resolve("classes");
        Files.createDirectories(classDir);

        ApplicationClassLoader loader = ApplicationClassLoader.builder()
            .addLocalSource(classDir.toString())
            .useCache(false)
            .build();

        loader.close();

        assertThrows(IllegalStateException.class, () -> {
            loader.loadClass("com.example.TestClass");
        });
    }

    @Test
    void testMultipleClose(@TempDir Path tempDir) throws Exception {
        Path classDir = tempDir.resolve("classes");
        Files.createDirectories(classDir);

        ApplicationClassLoader loader = ApplicationClassLoader.builder()
            .addLocalSource(classDir.toString())
            .useCache(false)
            .build();

        loader.close();
        assertDoesNotThrow(() -> loader.close());
    }

    @Test
    void testWithLifecycleListener(@TempDir Path tempDir) throws Exception {
        Path classDir = tempDir.resolve("classes");
        Files.createDirectories(classDir);

        AtomicInteger loadCount = new AtomicInteger(0);
        ClassLoaderLifecycleListener listener = new ClassLoaderLifecycleListener() {
            @Override
            public void onClassLoaded(ClassLoadEvent event) {
                loadCount.incrementAndGet();
            }
        };

        Path sourceDir = tempDir.resolve("src");
        Files.createDirectories(sourceDir);
        Path sourceFile = sourceDir.resolve("TestClass2.java");
        Files.writeString(sourceFile, "public class TestClass2 { }");

        ProcessBuilder pb = new ProcessBuilder("javac", "-d", classDir.toString(), sourceFile.toString());
        Process process = pb.start();
        assertEquals(0, process.waitFor());

        ApplicationClassLoader loader = ApplicationClassLoader.builder()
            .addLocalSource(classDir.toString())
            .useCache(false)
            .addListener(listener)
            .build();

        loader.loadClass("TestClass2");
        assertTrue(loadCount.get() > 0);
    }

    @Test
    void testCacheBehavior(@TempDir Path tempDir) throws Exception {
        Path classDir = tempDir.resolve("classes");
        Files.createDirectories(classDir);
        Path cacheDir = tempDir.resolve("cache");

        ClassCache mockCache = mock(ClassCache.class);
        when(mockCache.contains(anyString())).thenReturn(false);

        Path sourceDir = tempDir.resolve("src");
        Files.createDirectories(sourceDir);
        Path sourceFile = sourceDir.resolve("TestClass3.java");
        Files.writeString(sourceFile, "public class TestClass3 { }");

        ProcessBuilder pb = new ProcessBuilder("javac", "-d", classDir.toString(), sourceFile.toString());
        assertEquals(0, pb.start().waitFor());

        ApplicationClassLoader loader = ApplicationClassLoader.builder()
            .addLocalSource(classDir.toString())
            .cache(mockCache)
            .useCache(true)
            .build();

        loader.loadClass("TestClass3");

        verify(mockCache, atLeastOnce()).get("TestClass3");
        verify(mockCache, atLeastOnce()).put(eq("TestClass3"), any(byte[].class));
    }

    @Test
    void testCacheHit(@TempDir Path tempDir) throws Exception {
        Path classDir = tempDir.resolve("classes");
        Files.createDirectories(classDir);

        byte[] fakeClassData = new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, 0, 0, 0, 52};

        ClassCache mockCache = mock(ClassCache.class);
        when(mockCache.contains("com.example.CachedClass")).thenReturn(true);
        when(mockCache.get("com.example.CachedClass")).thenReturn(fakeClassData);

        ApplicationClassLoader loader = ApplicationClassLoader.builder()
            .addLocalSource(classDir.toString())
            .cache(mockCache)
            .useCache(true)
            .build();

        assertThrows(ClassFormatError.class, () -> {
            loader.loadClass("com.example.CachedClass");
        });

        verify(mockCache).get("com.example.CachedClass");
    }

    @Test
    void testParentLastDelegation(@TempDir Path tempDir) throws Exception {
        Path classDir = tempDir.resolve("classes");
        Files.createDirectories(classDir);

        ApplicationClassLoader loader = ApplicationClassLoader.builder()
            .addLocalSource(classDir.toString())
            .useCache(false)
            .delegationStrategy(new ParentLastDelegation())
            .build();

        assertNotNull(loader);
        Class<?> stringClass = loader.loadClass("java.lang.String");
        assertEquals("java.lang.String", stringClass.getName());
    }

    @Test
    void testParentFirstDelegation(@TempDir Path tempDir) throws Exception {
        Path classDir = tempDir.resolve("classes");
        Files.createDirectories(classDir);

        ApplicationClassLoader loader = ApplicationClassLoader.builder()
            .addLocalSource(classDir.toString())
            .useCache(false)
            .delegationStrategy(new ParentFirstDelegation())
            .build();

        assertNotNull(loader);
        Class<?> stringClass = loader.loadClass("java.lang.String");
        assertEquals("java.lang.String", stringClass.getName());
    }

    @Test
    void testMultipleSources(@TempDir Path tempDir) throws Exception {
        Path classDir1 = tempDir.resolve("classes1");
        Path classDir2 = tempDir.resolve("classes2");
        Files.createDirectories(classDir1);
        Files.createDirectories(classDir2);

        ApplicationClassLoader loader = ApplicationClassLoader.builder()
            .addLocalSource(classDir1.toString())
            .addLocalSource(classDir2.toString())
            .useCache(false)
            .build();

        assertEquals(2, loader.getClassSources().size());
    }

    @Test
    void testMultipleListeners(@TempDir Path tempDir) throws Exception {
        Path classDir = tempDir.resolve("classes");
        Files.createDirectories(classDir);

        ClassLoaderLifecycleListener listener1 = mock(ClassLoaderLifecycleListener.class);
        ClassLoaderLifecycleListener listener2 = mock(ClassLoaderLifecycleListener.class);

        Path sourceDir = tempDir.resolve("src");
        Files.createDirectories(sourceDir);
        Path sourceFile = sourceDir.resolve("TestClass4.java");
        Files.writeString(sourceFile, "public class TestClass4 { }");

        ProcessBuilder pb = new ProcessBuilder("javac", "-d", classDir.toString(), sourceFile.toString());
        assertEquals(0, pb.start().waitFor());

        ApplicationClassLoader loader = ApplicationClassLoader.builder()
            .addLocalSource(classDir.toString())
            .useCache(false)
            .addListener(listener1)
            .addListener(listener2)
            .build();

        loader.loadClass("TestClass4");

        verify(listener1, atLeastOnce()).onClassLoaded(any(ClassLoadEvent.class));
        verify(listener2, atLeastOnce()).onClassLoaded(any(ClassLoadEvent.class));
    }

    @Test
    void testGetClassSources(@TempDir Path tempDir) throws Exception {
        Path classDir = tempDir.resolve("classes");
        Files.createDirectories(classDir);

        ApplicationClassLoader loader = ApplicationClassLoader.builder()
            .addLocalSource(classDir.toString())
            .useCache(false)
            .build();

        assertNotNull(loader.getClassSources());
        assertFalse(loader.getClassSources().isEmpty());
        assertEquals(1, loader.getClassSources().size());
    }

    @Test
    void testCustomParent(@TempDir Path tempDir) throws Exception {
        Path classDir = tempDir.resolve("classes");
        Files.createDirectories(classDir);

        ClassLoader customParent = Thread.currentThread().getContextClassLoader();

        ApplicationClassLoader loader = ApplicationClassLoader.builder()
            .addLocalSource(classDir.toString())
            .useCache(false)
            .parent(customParent)
            .build();

        assertNotNull(loader);
        assertEquals(customParent, loader.getParent());
    }

    @Test
    void testTryWithResources(@TempDir Path tempDir) throws Exception {
        Path classDir = tempDir.resolve("classes");
        Files.createDirectories(classDir);

        AtomicBoolean closed = new AtomicBoolean(false);

        try (ApplicationClassLoader loader = ApplicationClassLoader.builder()
            .addLocalSource(classDir.toString())
            .useCache(false)
            .build()) {
            assertNotNull(loader);
        }

        assertTrue(true);
    }

    @Test
    void testLoadClassWithResolve(@TempDir Path tempDir) throws Exception {
        Path classDir = tempDir.resolve("classes");
        Files.createDirectories(classDir);

        Path sourceDir = tempDir.resolve("src");
        Files.createDirectories(sourceDir);
        Path sourceFile = sourceDir.resolve("TestClass5.java");
        Files.writeString(sourceFile, "public class TestClass5 { }");

        ProcessBuilder pb = new ProcessBuilder("javac", "-d", classDir.toString(), sourceFile.toString());
        assertEquals(0, pb.start().waitFor());

        ApplicationClassLoader loader = ApplicationClassLoader.builder()
            .addLocalSource(classDir.toString())
            .useCache(false)
            .build();

        Class<?> clazz = loader.loadClass("TestClass5", true);
        assertNotNull(clazz);
        assertEquals("TestClass5", clazz.getSimpleName());
    }

    @Test
    void testCacheDisabled(@TempDir Path tempDir) throws Exception {
        Path classDir = tempDir.resolve("classes");
        Path cacheDir = tempDir.resolve("cache");
        Files.createDirectories(classDir);

        FileSystemCache cache = new FileSystemCache(cacheDir);

        ApplicationClassLoader loader = ApplicationClassLoader.builder()
            .addLocalSource(classDir.toString())
            .cache(cache)
            .useCache(false)
            .build();

        assertFalse(loader.isCacheEnabled());
    }

    @Test
    void testSourcePriority(@TempDir Path tempDir) throws Exception {
        Path classDir1 = tempDir.resolve("classes1");
        Path classDir2 = tempDir.resolve("classes2");
        Files.createDirectories(classDir1);
        Files.createDirectories(classDir2);

        Path src1 = tempDir.resolve("src1");
        Files.createDirectories(src1);
        Path srcFile1 = src1.resolve("TestPriority.java");
        Files.writeString(srcFile1, "public class TestPriority { public int getValue() { return 1; } }");

        ProcessBuilder pb1 = new ProcessBuilder("javac", "-d", classDir1.toString(), srcFile1.toString());
        assertEquals(0, pb1.start().waitFor());

        ApplicationClassLoader loader = ApplicationClassLoader.builder()
            .addLocalSource(classDir1.toString())
            .addLocalSource(classDir2.toString())
            .useCache(false)
            .build();

        Class<?> clazz = loader.loadClass("TestPriority");
        Object instance = clazz.getDeclaredConstructor().newInstance();
        int value = (int) clazz.getMethod("getValue").invoke(instance);
        assertEquals(1, value);
    }

    @Test
    void testBuilderParentFirstDelegation(@TempDir Path tempDir) throws Exception {
        Path classDir = tempDir.resolve("classes");
        Files.createDirectories(classDir);

        ApplicationClassLoader loader = ApplicationClassLoader.builder()
            .addLocalSource(classDir.toString())
            .parentFirst()
            .build();

        assertNotNull(loader);
    }

    @Test
    void testBuilderParentLastDelegation(@TempDir Path tempDir) throws Exception {
        Path classDir = tempDir.resolve("classes");
        Files.createDirectories(classDir);

        ApplicationClassLoader loader = ApplicationClassLoader.builder()
            .addLocalSource(classDir.toString())
            .parentLast("java.", "javax.")
            .build();

        assertNotNull(loader);
    }

    @Test
    void testBuilderCustomDelegation(@TempDir Path tempDir) throws Exception {
        Path classDir = tempDir.resolve("classes");
        Files.createDirectories(classDir);

        ApplicationClassLoader loader = ApplicationClassLoader.builder()
            .addLocalSource(classDir.toString())
            .customDelegation(name -> name.startsWith("java."))
            .build();

        assertNotNull(loader);
    }

    @Test
    void testBuilderAddLoggingListener(@TempDir Path tempDir) throws Exception {
        Path classDir = tempDir.resolve("classes");
        Files.createDirectories(classDir);

        ApplicationClassLoader loader = ApplicationClassLoader.builder()
            .addLocalSource(classDir.toString())
            .addLoggingListener()
            .build();

        assertNotNull(loader);
    }

    @Test
    void testBuilderAddLoggingListenerVerbose(@TempDir Path tempDir) throws Exception {
        Path classDir = tempDir.resolve("classes");
        Files.createDirectories(classDir);

        ApplicationClassLoader loader = ApplicationClassLoader.builder()
            .addLocalSource(classDir.toString())
            .addLoggingListener(true)
            .build();

        assertNotNull(loader);
    }

    @Test
    void testBuilderTrackResources(@TempDir Path tempDir) throws Exception {
        Path classDir = tempDir.resolve("classes");
        Files.createDirectories(classDir);

        ApplicationClassLoader loader = ApplicationClassLoader.builder()
            .addLocalSource(classDir.toString())
            .trackResources()
            .build();

        assertNotNull(loader);
    }

    @Test
    void testBuilderNullDelegationStrategyThrows(@TempDir Path tempDir) throws Exception {
        Path classDir = tempDir.resolve("classes");
        Files.createDirectories(classDir);

        assertThrows(NullPointerException.class, () -> {
            ApplicationClassLoader.builder()
                .addLocalSource(classDir.toString())
                .delegationStrategy(null)
                .build();
        });
    }

    @Test
    void testBuilderNullListenerThrows(@TempDir Path tempDir) throws Exception {
        assertThrows(NullPointerException.class, () -> {
            ApplicationClassLoader.builder()
                .addLocalSource("/tmp")
                .addListener(null);
        });
    }

    @Test
    void testBuilderMaxClassSourcesLimit(@TempDir Path tempDir) throws Exception {
        Path classDir = tempDir.resolve("classes");
        Files.createDirectories(classDir);

        ApplicationClassLoaderBuilder builder = ApplicationClassLoader.builder();

        // Add 100 sources (the MAX_CLASS_SOURCES limit)
        for (int i = 0; i < 100; i++) {
            builder.addLocalSource(classDir.toString());
        }

        // The 101st should throw IllegalStateException
        assertThrows(IllegalStateException.class, () -> {
            builder.addLocalSource(classDir.toString());
        });
    }

    @Test
    void testBuilderNullClassSourceThrows() {
        assertThrows(NullPointerException.class, () -> {
            ApplicationClassLoader.builder()
                .addClassSource(null);
        });
    }

    @Test
    void testBuilderNoClassSourcesThrows() {
        assertThrows(IllegalStateException.class, () -> {
            ApplicationClassLoader.builder().build();
        });
    }

    @Test
    void testBuilderParentClassLoader(@TempDir Path tempDir) throws Exception {
        Path classDir = tempDir.resolve("classes");
        Files.createDirectories(classDir);

        ClassLoader customParent = ClassLoader.getSystemClassLoader();

        ApplicationClassLoader loader = ApplicationClassLoader.builder()
            .parent(customParent)
            .addLocalSource(classDir.toString())
            .build();

        assertSame(customParent, loader.getParent());
    }

    @Test
    void testBuilderUseCacheTrue(@TempDir Path tempDir) throws Exception {
        Path classDir = tempDir.resolve("classes");
        Path cacheDir = tempDir.resolve("cache");
        Files.createDirectories(classDir);

        FileSystemCache cache = new FileSystemCache(cacheDir);

        ApplicationClassLoader loader = ApplicationClassLoader.builder()
            .addLocalSource(classDir.toString())
            .cache(cache)
            .useCache(true)
            .build();

        assertTrue(loader.isCacheEnabled());
    }

    @Test
    void testFindResource(@TempDir Path tempDir) throws Exception {
        Path classDir = tempDir.resolve("classes");
        Files.createDirectories(classDir);
        Path resourceDir = classDir.resolve("com/example");
        Files.createDirectories(resourceDir);
        byte[] resourceData = "hello=world".getBytes();
        Files.write(resourceDir.resolve("test.properties"), resourceData);

        ApplicationClassLoader loader = ApplicationClassLoader.builder()
            .addLocalSource(classDir.toString())
            .useCache(false)
            .build();

        URL url = loader.findResource("com/example/test.properties");
        assertNotNull(url);

        try (InputStream is = url.openStream()) {
            byte[] loaded = is.readAllBytes();
            assertArrayEquals(resourceData, loaded);
        }
    }

    @Test
    void testGetResourceAsStream(@TempDir Path tempDir) throws Exception {
        Path classDir = tempDir.resolve("classes");
        Files.createDirectories(classDir);
        Path resourceDir = classDir.resolve("config");
        Files.createDirectories(resourceDir);
        byte[] resourceData = "db.url=jdbc:test".getBytes();
        Files.write(resourceDir.resolve("app.properties"), resourceData);

        ApplicationClassLoader loader = ApplicationClassLoader.builder()
            .addLocalSource(classDir.toString())
            .useCache(false)
            .build();

        try (InputStream is = loader.getResourceAsStream("config/app.properties")) {
            assertNotNull(is);
            byte[] loaded = is.readAllBytes();
            assertArrayEquals(resourceData, loaded);
        }
    }

    @Test
    void testFindResourceNotFound(@TempDir Path tempDir) throws Exception {
        Path classDir = tempDir.resolve("classes");
        Files.createDirectories(classDir);

        ApplicationClassLoader loader = ApplicationClassLoader.builder()
            .addLocalSource(classDir.toString())
            .useCache(false)
            .build();

        URL url = loader.findResource("nonexistent/resource.txt");
        assertNull(url);
    }

    @Test
    void testFindResourceAfterClose(@TempDir Path tempDir) throws Exception {
        Path classDir = tempDir.resolve("classes");
        Files.createDirectories(classDir);
        Files.write(classDir.resolve("test.txt"), "data".getBytes());

        ApplicationClassLoader loader = ApplicationClassLoader.builder()
            .addLocalSource(classDir.toString())
            .useCache(false)
            .build();

        loader.close();

        URL url = loader.findResource("test.txt");
        assertNull(url);
    }

    @Test
    void testConcurrentFindClass(@TempDir Path tempDir) throws Exception {
        Path classDir = tempDir.resolve("classes");
        Files.createDirectories(classDir);

        int threadCount = 10;
        Path sourceDir = tempDir.resolve("src");
        Files.createDirectories(sourceDir);
        for (int i = 0; i < threadCount; i++) {
            Path sourceFile = sourceDir.resolve("ConcTest" + i + ".java");
            Files.writeString(sourceFile, "public class ConcTest" + i + " { }");
            ProcessBuilder pb = new ProcessBuilder("javac", "-d", classDir.toString(), sourceFile.toString());
            assertEquals(0, pb.start().waitFor());
        }

        ApplicationClassLoader loader = ApplicationClassLoader.builder()
            .addLocalSource(classDir.toString())
            .useCache(false)
            .build();

        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<Class<?>>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            int idx = i;
            futures.add(executor.submit(() -> {
                barrier.await();
                return loader.loadClass("ConcTest" + idx);
            }));
        }

        for (int i = 0; i < threadCount; i++) {
            Class<?> clazz = futures.get(i).get(10, TimeUnit.SECONDS);
            assertNotNull(clazz);
            assertEquals("ConcTest" + i, clazz.getSimpleName());
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
    }

    @Test
    void testConcurrentFindResource(@TempDir Path tempDir) throws Exception {
        Path classDir = tempDir.resolve("classes");
        Files.createDirectories(classDir);
        Files.write(classDir.resolve("concurrent-test.txt"), "data".getBytes());

        ApplicationClassLoader loader = ApplicationClassLoader.builder()
            .addLocalSource(classDir.toString())
            .useCache(false)
            .build();

        int threadCount = 10;
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<URL>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                barrier.await();
                return loader.findResource("concurrent-test.txt");
            }));
        }

        for (Future<URL> future : futures) {
            URL url = future.get(10, TimeUnit.SECONDS);
            assertNotNull(url);
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
    }

    @Test
    void testConcurrentCloseWhileLoading(@TempDir Path tempDir) throws Exception {
        Path classDir = tempDir.resolve("classes");
        Files.createDirectories(classDir);
        Files.write(classDir.resolve("close-test.txt"), "data".getBytes());

        ApplicationClassLoader loader = ApplicationClassLoader.builder()
            .addLocalSource(classDir.toString())
            .useCache(false)
            .build();

        int threadCount = 5;
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount + 1);

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                startLatch.await();
                for (int j = 0; j < 100; j++) {
                    loader.findResource("close-test.txt");
                }
                return null;
            }));
        }

        futures.add(executor.submit(() -> {
            startLatch.await();
            Thread.sleep(5);
            loader.close();
            return null;
        }));

        startLatch.countDown();

        for (Future<?> future : futures) {
            assertDoesNotThrow(() -> future.get(10, TimeUnit.SECONDS));
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
    }

    @Test
    void testFindResourceMultipleSources(@TempDir Path tempDir) throws Exception {
        Path classDir1 = tempDir.resolve("classes1");
        Path classDir2 = tempDir.resolve("classes2");
        Files.createDirectories(classDir1);
        Files.createDirectories(classDir2);

        byte[] data1 = "source1".getBytes();
        byte[] data2 = "source2".getBytes();
        Files.write(classDir1.resolve("shared.txt"), data1);
        Files.write(classDir2.resolve("shared.txt"), data2);

        ApplicationClassLoader loader = ApplicationClassLoader.builder()
            .addLocalSource(classDir1.toString())
            .addLocalSource(classDir2.toString())
            .useCache(false)
            .build();

        URL url = loader.findResource("shared.txt");
        assertNotNull(url);

        try (InputStream is = url.openStream()) {
            byte[] loaded = is.readAllBytes();
            assertArrayEquals(data1, loaded, "First source should take priority");
        }
    }
}
