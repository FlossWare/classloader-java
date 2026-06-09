package org.flossware.classloader;

import org.flossware.classloader.cache.ClassCache;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages resource cleanup for ApplicationClassLoader.
 *
 * Responsibilities:
 * - Closing all AutoCloseable class sources
 * - Closing cache if applicable
 * - Notifying listeners of closure
 * - Aggregating exceptions during shutdown
 */
class ClassLoaderResourceManager {
    private final List<ClassSource> classSources;
    private final ClassCache cache;
    private final ClassLoaderEventDispatcher eventDispatcher;

    ClassLoaderResourceManager(List<ClassSource> classSources, ClassCache cache,
                              ClassLoaderEventDispatcher eventDispatcher) {
        this.classSources = classSources;
        this.cache = cache;
        this.eventDispatcher = eventDispatcher;
    }

    /**
     * Closes all resources and notifies listeners.
     *
     * <p>Collects all exceptions from resource closure and listener notification,
     * then throws an aggregated IOException if any failures occurred. This ensures
     * all resources are attempted to be closed even if some fail.</p>
     *
     * @throws IOException if any resource closure fails, with suppressed exceptions
     *                     from other closure attempts
     */
    void closeResources() throws IOException {
        List<Exception> exceptions = new ArrayList<>();

        // Close all closeable class sources
        for (ClassSource source : classSources) {
            if (source instanceof AutoCloseable) {
                closeAutoCloseable((AutoCloseable) source, exceptions);
            }
        }

        // Close cache if closeable
        if (cache instanceof AutoCloseable) {
            closeAutoCloseable((AutoCloseable) cache, exceptions);
        }

        // Notify listeners
        try {
            eventDispatcher.fireClassLoaderClosed();
        } catch (RuntimeException e) {
            exceptions.add(e);
        }

        // Throw if any exceptions occurred
        if (!exceptions.isEmpty()) {
            IOException ex = new IOException("Failed to close ApplicationClassLoader");
            exceptions.forEach(ex::addSuppressed);
            throw ex;
        }
    }

    /**
     * Helper method to close an AutoCloseable resource and collect exceptions.
     *
     * @param closeable the AutoCloseable resource to close
     * @param exceptions the exception collection to add any errors to
     */
    private void closeAutoCloseable(AutoCloseable closeable, List<Exception> exceptions) {
        try {
            closeable.close();
        } catch (IOException e) {
            // IOException: legitimate I/O failure during closure
            exceptions.add(e);
        } catch (RuntimeException e) {
            // RuntimeException: programming error or resource state issue
            exceptions.add(e);
        } catch (Exception e) {
            // Other checked exceptions from custom AutoCloseable implementations
            // (e.g., InterruptedException, SQLException, etc.)
            exceptions.add(e);
        }
    }
}
