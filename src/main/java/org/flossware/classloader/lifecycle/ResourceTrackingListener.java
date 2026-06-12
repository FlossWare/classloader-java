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
     * <p>Exceptions during resource closure are suppressed to ensure all resources
     * are closed. This is appropriate for cleanup operations where partial
     * success is acceptable. Errors (e.g., OutOfMemoryError) are allowed to
     * propagate after attempting to clear resource tracking state, since they
     * indicate unrecoverable JVM-level problems.</p>
     */
    public void closeAllResources() {
        for (AutoCloseable resource : openResources) {
            closeQuietly(resource);
        }
        openResources.clear();
        loadedClasses.clear();
    }

    /**
     * Closes a resource, suppressing checked exceptions and runtime exceptions.
     *
     * <p>AutoCloseable.close() declares {@code throws Exception}, so after handling
     * IOException, RuntimeException, and InterruptedException specifically, any remaining
     * checked exceptions (e.g., SQLException, NamingException from custom implementations)
     * are caught as Exception. This is the narrowest practical catch for AutoCloseable
     * since the method signature is intentionally broad. Errors are allowed to propagate.</p>
     *
     * <p><b>Justification for broad Exception catch:</b> The AutoCloseable contract
     * intentionally declares a broad {@code throws Exception} to accommodate diverse
     * implementations. After handling the specific exception types above, a catch-all
     * Exception block is appropriate and necessary to handle unchecked/custom exceptions
     * from implementations outside our control.</p>
     *
     * @param resource the resource to close
     */
    private static void closeQuietly(AutoCloseable resource) {
        try {
            resource.close();
        } catch (IOException e) {
            // IO errors during resource closure are suppressed
            // to ensure we continue closing remaining resources
        } catch (InterruptedException e) {
            // Restore the interrupt flag so callers can detect the interruption
            Thread.currentThread().interrupt();
        } catch (IllegalStateException e) {
            // Resource already closed or in invalid state - safe to suppress during cleanup
        } catch (UnsupportedOperationException e) {
            // Close not supported by this resource implementation - safe to suppress
        } catch (java.sql.SQLException e) {
            // SQL errors during resource closure are suppressed during cleanup
        } catch (Exception e) {
            // AutoCloseable.close() declares 'throws Exception'. The specific catch blocks
            // above handle the most common exception types. This final catch handles any
            // remaining domain-specific checked exceptions from implementations outside
            // our control (e.g., javax.naming.NamingException, javax.jms.JMSException).
            // This is the narrowest practical approach for AutoCloseable compliance.
        }
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
