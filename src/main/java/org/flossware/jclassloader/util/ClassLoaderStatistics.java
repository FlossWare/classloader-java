package org.flossware.jclassloader.util;

/**
 * Statistics about class loading activity.
 * Tracks classes loaded, bytes loaded, and cache hits for monitoring purposes.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * ClassLoaderStatistics stats = new ClassLoaderStatistics(
 *     "my-classloader",
 *     150,           // classes loaded
 *     2_000_000L,    // total bytes
 *     45L            // cache hits
 * );
 *
 * System.out.println("Cache hit rate: " + stats.getCacheHitRate() * 100 + "%");
 * }</pre>
 */
public class ClassLoaderStatistics {
    private final String name;
    private final int classesLoaded;
    private final long totalBytesLoaded;
    private final long cacheHits;

    /**
     * Creates a new class loader statistics snapshot.
     *
     * @param name a name or identifier for this classloader
     * @param classesLoaded the total number of classes loaded
     * @param totalBytesLoaded the total bytes loaded from all sources
     * @param cacheHits the number of classes loaded from cache
     */
    public ClassLoaderStatistics(String name, int classesLoaded,
                                long totalBytesLoaded, long cacheHits) {
        this.name = name;
        this.classesLoaded = classesLoaded;
        this.totalBytesLoaded = totalBytesLoaded;
        this.cacheHits = cacheHits;
    }

    /**
     * Returns the classloader name or identifier.
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the total number of classes loaded.
     *
     * @return the class count
     */
    public int getClassesLoaded() {
        return classesLoaded;
    }

    /**
     * Returns the total bytes loaded from all sources.
     *
     * @return the total bytes
     */
    public long getTotalBytesLoaded() {
        return totalBytesLoaded;
    }

    /**
     * Returns the number of classes loaded from cache.
     *
     * @return the cache hit count
     */
    public long getCacheHits() {
        return cacheHits;
    }

    /**
     * Calculates the cache hit rate as a ratio.
     *
     * @return the cache hit rate (0.0 to 1.0), or 0.0 if no classes loaded
     */
    public double getCacheHitRate() {
        return classesLoaded > 0 ? (double) cacheHits / classesLoaded : 0.0;
    }

    @Override
    public String toString() {
        return String.format("ClassLoaderStatistics{name=%s, classes=%d, bytes=%d, cacheHits=%d, hitRate=%.2f%%}",
                name, classesLoaded, totalBytesLoaded, cacheHits, getCacheHitRate() * 100);
    }
}
