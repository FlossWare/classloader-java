package org.flossware.classloader.lifecycle;

import java.util.Objects;

/**
 * Listener for logging/debugging class loading.
 * Writes to System.out by default. Can be extended to use a logging framework.
 */
public class LoggingListener implements ClassLoaderLifecycleListener {

    private final boolean verbose;

    /**
     * Creates a logging listener.
     *
     * @param verbose If true, logs all events including cache hits. If false, only logs actual loads.
     */
    public LoggingListener(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Creates a non-verbose logging listener.
     */
    public LoggingListener() {
        this(false);
    }

    /** {@inheritDoc} */
    @Override
    public void onClassLoaded(ClassLoadEvent event) {
        Objects.requireNonNull(event, "event cannot be null");
        System.out.printf("[ApplicationClassLoader] Loaded %s from %s in %dms (%d bytes)%n",
                event.getClassName(),
                event.getSource().getDescription(),
                event.getLoadTimeMillis(),
                event.getClassSizeBytes());
    }

    /** {@inheritDoc} */
    @Override
    public void onClassCacheHit(String className) {
        Objects.requireNonNull(className, "className cannot be null");
        if (verbose) {
            System.out.printf("[ApplicationClassLoader] Cache hit: %s%n", className);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void onClassCached(String className, byte[] classData) {
        Objects.requireNonNull(className, "className cannot be null");
        Objects.requireNonNull(classData, "classData cannot be null");
        if (verbose) {
            System.out.printf("[ApplicationClassLoader] Cached: %s (%d bytes)%n", className, classData.length);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void onClassLoadFailed(String className, Throwable error) {
        Objects.requireNonNull(className, "className cannot be null");
        Objects.requireNonNull(error, "error cannot be null");
        System.out.printf("[ApplicationClassLoader] Failed to load %s: %s%n", className, error.getMessage());
    }

    /** {@inheritDoc} */
    @Override
    public void onResourceOpened(String resourceName, AutoCloseable resource) {
        Objects.requireNonNull(resourceName, "resourceName cannot be null");
        Objects.requireNonNull(resource, "resource cannot be null");
        if (verbose) {
            System.out.printf("[ApplicationClassLoader] Opened resource: %s%n", resourceName);
        }
    }
}
