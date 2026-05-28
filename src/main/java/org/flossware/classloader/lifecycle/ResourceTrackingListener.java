package org.flossware.classloader.lifecycle;

import java.util.Collections;
import java.util.List;
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

    @Override
    public void onClassLoaded(ClassLoadEvent event) {
        loadedClasses.add(event.getClassName());
        totalClassesLoaded.incrementAndGet();
        totalBytesLoaded.addAndGet(event.getClassSizeBytes());
    }

    @Override
    public void onClassCacheHit(String className) {
        cacheHits.incrementAndGet();
    }

    @Override
    public void onResourceOpened(String resourceName, AutoCloseable resource) {
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
     */
    public void closeAllResources() {
        for (AutoCloseable resource : openResources) {
            try {
                resource.close();
            } catch (Exception e) {
                // Log but don't propagate
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

    @Override
    public String toString() {
        return String.format("ResourceTracker{classes=%d, bytes=%d, cacheHits=%d, resources=%d}",
                totalClassesLoaded.get(), totalBytesLoaded.get(), cacheHits.get(), openResources.size());
    }
}
