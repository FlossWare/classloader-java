package org.flossware.classloader.lifecycle;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Utility listener for tracking loaded classes and resources.
 * Useful for cleanup scenarios (e.g., when undeploying an application).
 */
public class ResourceTrackingListener implements ClassLoaderLifecycleListener {

    private final Set<String> loadedClasses = ConcurrentHashMap.newKeySet();
    private final List<AutoCloseable> openResources = new CopyOnWriteArrayList<>();
    private final AtomicLong totalClassesLoaded = new AtomicLong();
    private final AtomicLong totalBytesLoaded = new AtomicLong();
    private final AtomicLong cacheHits = new AtomicLong();

    /** {@inheritDoc} */
    @Override
    public void onClassLoaded(ClassLoadEvent event) {
        Objects.requireNonNull(event, "event cannot be null");
        loadedClasses.add(event.getClassName());
        totalClassesLoaded.incrementAndGet();
        totalBytesLoaded.addAndGet(event.getClassSizeBytes());
    }

    /** {@inheritDoc} */
    @Override
    public void onClassCacheHit(String className) {
        Objects.requireNonNull(className, "className cannot be null");
        cacheHits.incrementAndGet();
    }

    /** {@inheritDoc} */
    @Override
    public void onResourceOpened(String resourceName, AutoCloseable resource) {
        Objects.requireNonNull(resourceName, "resourceName cannot be null");
        Objects.requireNonNull(resource, "resource cannot be null");
        openResources.add(resource);
    }

    /**
     * Gets the set of loaded class names.
     *
     * @return Unmodifiable set of class names
     */
    public Set<String> getLoadedClasses() {
        return Collections.unmodifiableSet(loadedClasses);
    }

    /**
     * Gets the total number of classes loaded.
     *
     * @return Total classes loaded
     */
    public long getTotalClassesLoaded() {
        return totalClassesLoaded.get();
    }

    /**
     * Gets the total bytes loaded (sum of all class bytecode sizes).
     *
     * @return Total bytes loaded
     */
    public long getTotalBytesLoaded() {
        return totalBytesLoaded.get();
    }

    /**
     * Gets the number of cache hits.
     *
     * @return Number of times a class was loaded from cache
     */
    public long getCacheHits() {
        return cacheHits.get();
    }

    /**
     * Closes all tracked resources and clears all tracking data.
     *
     * <p>Errors during resource closure are suppressed to ensure all resources
     * are closed. This is appropriate for cleanup operations where partial
     * success is acceptable.</p>
     */
    public void closeAllResources() {
        for (AutoCloseable resource : openResources) {
            try {
                resource.close();
            } catch (IOException e) {
                // IO errors during resource closure are suppressed
                // to ensure we continue closing remaining resources
            } catch (RuntimeException e) {
                // Programming errors are suppressed to allow cleanup to continue
            } catch (Exception e) {
                // Other checked exceptions (rare but possible from custom AutoCloseable implementations)
                // are suppressed to ensure we continue closing remaining resources
            }
        }
        openResources.clear();
        loadedClasses.clear();
    }

    /**
     * Clears all tracking data without closing resources.
     */
    public void reset() {
        loadedClasses.clear();
        openResources.clear();
        totalClassesLoaded.set(0);
        totalBytesLoaded.set(0);
        cacheHits.set(0);
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return String.format("ResourceTracker{classes=%d, bytes=%d, cacheHits=%d, resources=%d}",
                totalClassesLoaded.get(), totalBytesLoaded.get(), cacheHits.get(), openResources.size());
    }

    /** {@inheritDoc} */
    @Override
    public void onClassLoaderClosed() {
        closeAllResources();
    }
}
