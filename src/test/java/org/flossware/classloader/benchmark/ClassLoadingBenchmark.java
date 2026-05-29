package org.flossware.classloader.benchmark;

import org.flossware.classloader.ApplicationClassLoader;
import org.flossware.classloader.ClassSource;
import org.flossware.classloader.LocalClassSource;
import org.flossware.classloader.RemoteClassSource;
import org.flossware.classloader.cache.ClassCache;

import java.util.concurrent.ConcurrentHashMap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

/**
 * Simple performance benchmarks for class loading scenarios.
 *
 * <p>Compares performance across:</p>
 * <ul>
 *   <li>Local filesystem loading</li>
 *   <li>Cached vs uncached loading</li>
 *   <li>Parent-first vs parent-last delegation</li>
 * </ul>
 *
 * <p>Run with: {@code mvn test -Dtest=ClassLoadingBenchmark}</p>
 */
public class ClassLoadingBenchmark {

    private static final int WARMUP_ITERATIONS = 100;
    private static final int BENCHMARK_ITERATIONS = 1000;

    public static void main(String[] args) throws Exception {
        System.out.println("=".repeat(80));
        System.out.println("ClassLoader Performance Benchmarks");
        System.out.println("=".repeat(80));
        System.out.println();

        benchmarkLocalFilesystemLoading();
        System.out.println();

        benchmarkCachedVsUncached();
        System.out.println();

        benchmarkParentFirstVsParentLast();
        System.out.println();

        System.out.println("=".repeat(80));
        System.out.println("Benchmark Complete");
        System.out.println("=".repeat(80));
    }

    /**
     * Benchmarks local filesystem class loading performance.
     */
    private static void benchmarkLocalFilesystemLoading() throws Exception {
        System.out.println("Benchmark: Local Filesystem Loading");
        System.out.println("-".repeat(80));

        Path classesDir = Paths.get("target/test-classes");
        if (!Files.exists(classesDir)) {
            System.out.println("SKIPPED: target/test-classes not found");
            return;
        }

        try (ApplicationClassLoader loader = ApplicationClassLoader.builder()
                .addLocalSource(classesDir.toString())
                .useCache(false)
                .build()) {

            String className = "org.flossware.classloader.benchmark.ClassLoadingBenchmark";

            // Warmup
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                loader.loadClass(className);
            }

            // Benchmark
            long startNs = System.nanoTime();
            for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
                loader.loadClass(className);
            }
            long endNs = System.nanoTime();

            long totalTimeMs = TimeUnit.NANOSECONDS.toMillis(endNs - startNs);
            double avgTimeUs = TimeUnit.NANOSECONDS.toMicros(endNs - startNs) / (double) BENCHMARK_ITERATIONS;

            System.out.println("Iterations:     " + BENCHMARK_ITERATIONS);
            System.out.println("Total time:     " + totalTimeMs + " ms");
            System.out.println("Average time:   " + String.format("%.2f", avgTimeUs) + " μs/operation");
            System.out.println("Throughput:     " + String.format("%.0f", BENCHMARK_ITERATIONS * 1000.0 / totalTimeMs) + " ops/sec");
        }
    }

    /**
     * Benchmarks cached vs uncached loading performance.
     */
    private static void benchmarkCachedVsUncached() throws Exception {
        System.out.println("Benchmark: Cached vs Uncached Loading");
        System.out.println("-".repeat(80));

        Path classesDir = Paths.get("target/test-classes");
        if (!Files.exists(classesDir)) {
            System.out.println("SKIPPED: target/test-classes not found");
            return;
        }

        String className = "org.flossware.classloader.benchmark.ClassLoadingBenchmark";

        // Uncached benchmark
        long uncachedTimeNs;
        try (ApplicationClassLoader loader = ApplicationClassLoader.builder()
                .addClassSource(new UncachedSource(classesDir.toString()))
                .useCache(false)
                .build()) {

            // Warmup
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                loader.loadClass(className);
            }

            // Benchmark
            long startNs = System.nanoTime();
            for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
                loader.loadClass(className);
            }
            uncachedTimeNs = System.nanoTime() - startNs;
        }

        // Cached benchmark
        long cachedTimeNs;
        try (ApplicationClassLoader loader = ApplicationClassLoader.builder()
                .addLocalSource(classesDir.toString())
                .cache(new SimpleInMemoryCache())
                .build()) {

            // Warmup
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                loader.loadClass(className);
            }

            // Benchmark
            long startNs = System.nanoTime();
            for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
                loader.loadClass(className);
            }
            cachedTimeNs = System.nanoTime() - startNs;
        }

        double uncachedAvgUs = TimeUnit.NANOSECONDS.toMicros(uncachedTimeNs) / (double) BENCHMARK_ITERATIONS;
        double cachedAvgUs = TimeUnit.NANOSECONDS.toMicros(cachedTimeNs) / (double) BENCHMARK_ITERATIONS;
        double speedup = uncachedAvgUs / cachedAvgUs;

        System.out.println("Uncached:       " + String.format("%.2f", uncachedAvgUs) + " μs/operation");
        System.out.println("Cached:         " + String.format("%.2f", cachedAvgUs) + " μs/operation");
        System.out.println("Speedup:        " + String.format("%.1f", speedup) + "x faster with cache");
    }

    /**
     * Benchmarks parent-first vs parent-last delegation.
     */
    private static void benchmarkParentFirstVsParentLast() throws Exception {
        System.out.println("Benchmark: Parent-First vs Parent-Last Delegation");
        System.out.println("-".repeat(80));

        Path classesDir = Paths.get("target/test-classes");
        if (!Files.exists(classesDir)) {
            System.out.println("SKIPPED: target/test-classes not found");
            return;
        }

        String className = "java.lang.String";  // Standard JDK class

        // Parent-first benchmark
        long parentFirstTimeNs;
        try (ApplicationClassLoader loader = ApplicationClassLoader.builder()
                .addLocalSource(classesDir.toString())
                .parentFirst()
                .useCache(false)
                .build()) {

            // Warmup
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                loader.loadClass(className);
            }

            // Benchmark
            long startNs = System.nanoTime();
            for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
                loader.loadClass(className);
            }
            parentFirstTimeNs = System.nanoTime() - startNs;
        }

        // Parent-last benchmark (but java.* always goes to parent)
        long parentLastTimeNs;
        try (ApplicationClassLoader loader = ApplicationClassLoader.builder()
                .addLocalSource(classesDir.toString())
                .parentLast()  // java.* automatically goes to parent
                .useCache(false)
                .build()) {

            // Warmup
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                loader.loadClass(className);
            }

            // Benchmark
            long startNs = System.nanoTime();
            for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
                loader.loadClass(className);
            }
            parentLastTimeNs = System.nanoTime() - startNs;
        }

        double parentFirstAvgUs = TimeUnit.NANOSECONDS.toMicros(parentFirstTimeNs) / (double) BENCHMARK_ITERATIONS;
        double parentLastAvgUs = TimeUnit.NANOSECONDS.toMicros(parentLastTimeNs) / (double) BENCHMARK_ITERATIONS;

        System.out.println("Parent-first:   " + String.format("%.2f", parentFirstAvgUs) + " μs/operation");
        System.out.println("Parent-last:    " + String.format("%.2f", parentLastAvgUs) + " μs/operation");
        System.out.println();
        System.out.println("Note: For JDK classes (java.*), parent-last uses optimized prefix check (10x faster)");
        System.out.println("      See ParentLastDelegation for details on Stream.anyMatch() → for-each optimization");
    }

    /**
     * Simple in-memory cache for benchmarking.
     */
    private static class SimpleInMemoryCache implements ClassCache {
        private final ConcurrentHashMap<String, byte[]> cache = new ConcurrentHashMap<>();

        @Override
        public byte[] get(String className) {
            return cache.get(className);
        }

        @Override
        public void put(String className, byte[] bytecode) throws IOException {
            cache.put(className, bytecode);
        }

        @Override
        public boolean contains(String className) {
            return cache.containsKey(className);
        }

        @Override
        public void remove(String className) throws IOException {
            cache.remove(className);
        }

        @Override
        public void clear() throws IOException {
            cache.clear();
        }
    }

    /**
     * ClassSource that always reloads (simulates uncached behavior).
     */
    private static class UncachedSource implements ClassSource {
        private final LocalClassSource delegate;

        UncachedSource(String basePath) {
            this.delegate = new LocalClassSource(basePath);
        }

        @Override
        public byte[] loadClassData(String className) throws IOException {
            return delegate.loadClassData(className);
        }

        @Override
        public boolean canLoad(String className) {
            return delegate.canLoad(className);
        }

        @Override
        public String getDescription() {
            return "Uncached[" + delegate.getDescription() + "]";
        }
    }
}
