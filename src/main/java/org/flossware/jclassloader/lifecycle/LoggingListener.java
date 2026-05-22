package org.flossware.jclassloader.lifecycle;

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

    @Override
    public void onClassLoaded(ClassLoadEvent event) {
        System.out.printf("[JClassLoader] Loaded %s from %s in %dms (%d bytes)%n",
                event.getClassName(),
                event.getSource().getDescription(),
                event.getLoadTimeMillis(),
                event.getClassSizeBytes());
    }

    @Override
    public void onClassCacheHit(String className) {
        if (verbose) {
            System.out.printf("[JClassLoader] Cache hit: %s%n", className);
        }
    }

    @Override
    public void onClassCached(String className, byte[] classData) {
        if (verbose) {
            System.out.printf("[JClassLoader] Cached: %s (%d bytes)%n", className, classData.length);
        }
    }

    @Override
    public void onClassLoadFailed(String className, Throwable error) {
        System.out.printf("[JClassLoader] Failed to load %s: %s%n", className, error.getMessage());
    }

    @Override
    public void onResourceOpened(String resourceName, AutoCloseable resource) {
        if (verbose) {
            System.out.printf("[JClassLoader] Opened resource: %s%n", resourceName);
        }
    }
}
