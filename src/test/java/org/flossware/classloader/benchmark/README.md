# ClassLoader Performance Benchmarks

Simple performance benchmarks for comparing class loading scenarios.

## Running Benchmarks

### Using Maven

```bash
# Run all benchmarks
mvn test-compile exec:java -Dexec.mainClass="org.flossware.classloader.benchmark.ClassLoadingBenchmark" -Dexec.classpathScope=test
```

### Using Java directly

```bash
# Compile first
mvn clean test-compile

# Run benchmarks
java -cp "target/classes:target/test-classes:$(mvn dependency:build-classpath -q -Dmdep.outputFile=/dev/stdout)" \
    org.flossware.classloader.benchmark.ClassLoadingBenchmark
```

## Benchmark Scenarios

### 1. Local Filesystem Loading

Measures raw performance of loading classes from local filesystem.

**Typical Results:**
- ~2-5 μs per operation
- ~200,000-500,000 ops/sec throughput

### 2. Cached vs Uncached Loading

Compares performance impact of caching.

**Expected Behavior:**
- Uncached: Full filesystem read every time
- Cached: Memory lookup after first load
- **Speedup: 5-10x faster with cache** (for repeated loads)

**Note:** First-time loads show minimal difference; cache benefit appears on subsequent loads of the same class.

### 3. Parent-First vs Parent-Last Delegation

Compares delegation strategy performance for JDK classes.

**Results:**
- Parent-first: ~0.5-1 μs (direct parent delegation)
- Parent-last: ~1-2 μs (optimized prefix check before parent delegation)

**Key Optimization:**
The ParentLastDelegation class was optimized from `Stream.anyMatch()` to a simple `for-each` loop, providing **10x performance improvement** for prefix checking. See [PR #56](https://github.com/FlossWare/classloader-java/issues/56) for details.

## Benchmark Configuration

- **Warmup iterations:** 100
- **Benchmark iterations:** 1,000
- **JVM:** Inherits from Maven/system JVM

## Interpreting Results

### Microseconds (μs)

- **< 1 μs:** Excellent - cached or parent-delegated
- **1-5 μs:** Good - local filesystem loading
- **5-50 μs:** Acceptable - remote HTTP loading with good network
- **> 50 μs:** Slow - remote loading with high latency or large files

### Throughput (ops/sec)

- **> 100,000:** Excellent - memory/cache-based
- **10,000-100,000:** Good - local filesystem
- **1,000-10,000:** Acceptable - fast remote loading
- **< 1,000:** Slow - high-latency remote loading

## Adding More Benchmarks

To add new benchmarks:

1. Add a `private static void benchmarkXxx()` method
2. Call it from `main()`
3. Follow the pattern: warmup → measure → report

Example:

```java
private static void benchmarkRemoteLoading() throws Exception {
    System.out.println("Benchmark: Remote HTTP Loading");
    System.out.println("-".repeat(80));

    // Warmup
    for (int i = 0; i < WARMUP_ITERATIONS; i++) {
        // ... warmup code ...
    }

    // Benchmark
    long startNs = System.nanoTime();
    for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
        // ... benchmark code ...
    }
    long endNs = System.nanoTime();

    // Report
    double avgTimeUs = TimeUnit.NANOSECONDS.toMicros(endNs - startNs) / (double) BENCHMARK_ITERATIONS;
    System.out.println("Average time:   " + String.format("%.2f", avgTimeUs) + " μs/operation");
}
```

## Limitations

These are **micro-benchmarks** with simplified scenarios. Production performance depends on:

- Network latency and bandwidth (for remote loading)
- Disk I/O performance (for local loading)
- Class file size
- JVM warmup and JIT compilation
- GC pressure
- Concurrent class loading

For rigorous benchmarking, consider using [JMH (Java Microbenchmark Harness)](https://github.com/openjdk/jmh).
