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
     * @throws IOException if any resource closure fails
     */
    void closeResources() throws IOException {
        List<Exception> exceptions = new ArrayList<>();

        // Close all closeable class sources
        for (ClassSource source : classSources) {
            if (source instanceof AutoCloseable) {
                try {
                    ((AutoCloseable) source).close();
                } catch (IOException e) {
                    exceptions.add(e);
                } catch (RuntimeException e) {
                    exceptions.add(e);
                }
            }
        }

        // Close cache if closeable
        if (cache instanceof AutoCloseable) {
            try {
                ((AutoCloseable) cache).close();
            } catch (IOException e) {
                exceptions.add(e);
            } catch (RuntimeException e) {
                exceptions.add(e);
            }
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
}
