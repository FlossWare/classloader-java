package org.flossware.classloader;

import org.flossware.classloader.cache.ClassCache;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
        this.classSources = Objects.requireNonNull(classSources, "classSources cannot be null");
        this.cache = cache;
        this.eventDispatcher = Objects.requireNonNull(eventDispatcher, "eventDispatcher cannot be null");
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
        List<IOException> exceptions = new ArrayList<>();

        // Close all closeable class sources
        for (ClassSource source : classSources) {
            if (source instanceof AutoCloseable) {
                try {
                    closeAutoCloseable((AutoCloseable) source);
                } catch (IOException e) {
                    exceptions.add(e);
                }
            }
        }

        // Close cache if closeable
        if (cache instanceof AutoCloseable) {
            try {
                closeAutoCloseable((AutoCloseable) cache);
            } catch (IOException e) {
                exceptions.add(e);
            }
        }

        // Notify listeners - listener callbacks can throw any RuntimeException since
        // they are external implementations. Catch specific unchecked exception types
        // that are most likely from callback invocations.
        try {
            eventDispatcher.fireClassLoaderClosed();
        } catch (IllegalStateException | NullPointerException | UnsupportedOperationException e) {
            exceptions.add(new IOException("Listener notification failed during close", e));
        }

        // Throw if any exceptions occurred
        if (!exceptions.isEmpty()) {
            IOException ex = new IOException("Failed to close ApplicationClassLoader");
            exceptions.forEach(ex::addSuppressed);
            throw ex;
        }
    }

    /**
     * Closes an AutoCloseable resource, converting checked exceptions to IOException.
     *
     * <p>AutoCloseable.close() declares {@code throws Exception}, which is intentionally
     * broad to accommodate diverse implementations. This method narrows the exception
     * handling by catching specific exception types and wrapping any remaining checked
     * exceptions as IOException with the original as the cause.</p>
     *
     * <p><b>Justification for broad Exception catch:</b> The AutoCloseable contract
     * intentionally declares a broad {@code throws Exception} to support implementations
     * that throw custom or domain-specific checked exceptions. After handling the specific
     * exception types (IOException, RuntimeException, InterruptedException), a catch-all
     * Exception block is the narrowest practical approach for AutoCloseable compliance.</p>
     *
     * @param closeable the resource to close
     * @throws IOException if closing fails
     */
    private static void closeAutoCloseable(AutoCloseable closeable) throws IOException {
        try {
            closeable.close();
        } catch (IOException e) {
            throw e;
        } catch (IllegalStateException e) {
            throw new IOException("Resource in invalid state during close: " + closeable, e);
        } catch (UnsupportedOperationException e) {
            throw new IOException("Close not supported on resource: " + closeable, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while closing resource: " + closeable, e);
        } catch (java.sql.SQLException e) {
            throw new IOException("SQL error closing resource: " + closeable, e);
        } catch (ReflectiveOperationException e) {
            throw new IOException("Reflection error closing resource: " + closeable, e);
        } catch (Exception e) {
            // AutoCloseable.close() declares 'throws Exception'. The specific catch blocks
            // above handle the most common exception types. This final catch handles any
            // remaining domain-specific checked exceptions from implementations outside
            // our control (e.g., javax.naming.NamingException, javax.jms.JMSException).
            // This is the narrowest practical approach for AutoCloseable compliance.
            throw new IOException("Failed to close resource: " + closeable, e);
        }
    }
}
