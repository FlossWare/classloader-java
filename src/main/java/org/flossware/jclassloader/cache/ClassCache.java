package org.flossware.jclassloader.cache;

import java.io.IOException;

/**
 * Interface for caching class bytecode.
 * Implementations can use various storage mechanisms (file system, Redis, memory, etc.).
 */
public interface ClassCache {
    /**
     * Retrieves cached class bytecode.
     *
     * @param className The fully qualified class name
     * @return The class bytecode, or null if not cached
     */
    byte[] get(String className);

    /**
     * Stores class bytecode in the cache.
     *
     * @param className The fully qualified class name
     * @param classData The class bytecode to cache
     * @throws IOException if caching fails
     */
    void put(String className, byte[] classData) throws IOException;

    /**
     * Checks if a class is present in the cache.
     *
     * @param className The fully qualified class name
     * @return true if the class is cached, false otherwise
     */
    boolean contains(String className);

    /**
     * Clears all cached classes.
     *
     * @throws IOException if clearing fails
     */
    void clear() throws IOException;

    /**
     * Removes a specific class from the cache.
     *
     * @param className The fully qualified class name to remove
     * @throws IOException if removal fails
     */
    void remove(String className) throws IOException;
}
