/**
 * Caching implementations for class bytecode.
 *
 * <p>This package provides various caching strategies to improve class loading performance
 * by storing class bytecode in memory or on disk.</p>
 *
 * <h2>Key Classes</h2>
 * <ul>
 *   <li>{@link org.flossware.jclassloader.cache.ClassCache} - Interface for cache implementations</li>
 *   <li>{@link org.flossware.jclassloader.cache.MemoryCache} - In-memory LRU cache</li>
 *   <li>{@link org.flossware.jclassloader.cache.FileSystemCache} - Disk-based persistent cache</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // In-memory cache with LRU eviction
 * ClassCache memoryCache = new MemoryCache(1000);  // Max 1000 classes
 *
 * // Filesystem cache for persistence
 * ClassCache fileCache = new FileSystemCache(Paths.get("/tmp/class-cache"));
 *
 * JClassLoader loader = JClassLoader.builder()
 *     .addRemoteSource("https://example.com/classes/")
 *     .cache(memoryCache)
 *     .build();
 * }</pre>
 *
 * @since 1.0
 */
package org.flossware.classloader.cache;
