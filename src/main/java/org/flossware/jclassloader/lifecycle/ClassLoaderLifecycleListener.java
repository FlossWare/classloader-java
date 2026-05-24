package org.flossware.jclassloader.lifecycle;

/**
 * Listener for class loading lifecycle events.
 * Useful for tracking, logging, security checks, and resource management.
 */
public interface ClassLoaderLifecycleListener {

    /**
     * Called when a class is successfully loaded from a source.
     *
     * @param event Event containing details about the loaded class
     */
    default void onClassLoaded(ClassLoadEvent event) {}

    /**
     * Called when a class is loaded from cache.
     *
     * @param className The name of the class loaded from cache
     */
    default void onClassCacheHit(String className) {}

    /**
     * Called when a class is cached.
     *
     * @param className The name of the class being cached
     * @param classData The bytecode of the class
     */
    default void onClassCached(String className, byte[] classData) {}

    /**
     * Called when class loading fails.
     *
     * @param className The name of the class that failed to load
     * @param error The error that occurred
     */
    default void onClassLoadFailed(String className, Throwable error) {}

    /**
     * Called when a resource is opened (for tracking cleanup).
     *
     * @param resourceName The name of the resource
     * @param resource The opened resource (if AutoCloseable)
     */
    default void onResourceOpened(String resourceName, AutoCloseable resource) {}

    /**
     * Called when the ClassLoader is being closed/disposed.
     * Use this to clean up resources, clear caches, and prevent memory leaks.
     */
    default void onClassLoaderClosed() {}
}
