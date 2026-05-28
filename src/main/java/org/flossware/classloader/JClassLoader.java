package org.flossware.classloader;

import org.flossware.classloader.cache.ClassCache;
import org.flossware.classloader.delegation.DelegationStrategy;
import org.flossware.classloader.delegation.ParentFirstDelegation;
import org.flossware.classloader.lifecycle.ClassLoadEvent;
import org.flossware.classloader.lifecycle.ClassLoaderLifecycleListener;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Custom ClassLoader supporting multiple class sources with caching and lifecycle management.
 * Implements AutoCloseable for proper resource cleanup and memory leak prevention.
 *
 * <h2>Memory Leak Prevention</h2>
 * <p><b>CRITICAL:</b> ClassLoaders can cause memory leaks if not properly managed. This ClassLoader
 * holds strong references to all loaded classes, which prevents those classes (and their static fields)
 * from being garbage collected until the ClassLoader itself is collected.</p>
 *
 * <h3>Always Close When Done</h3>
 * <p>Use try-with-resources to ensure proper cleanup:</p>
 * <pre>{@code
 * try (JClassLoader loader = JClassLoader.builder()
 *         .addLocalSource("/path/to/classes")
 *         .build()) {
 *     Class<?> clazz = loader.loadClass("com.example.MyClass");
 *     // Use the class
 * }  // Automatically closes and releases resources
 * }</pre>
 *
 * <h3>Hot Reload / Class Reloading Scenarios</h3>
 * <p>When implementing hot reload or dynamic class reloading:</p>
 * <ol>
 *   <li><b>Create a new ClassLoader for each reload</b> - Don't reuse ClassLoaders</li>
 *   <li><b>Close the old ClassLoader</b> - Call {@link #close()} on the old loader</li>
 *   <li><b>Clear all references</b> - Null out references to classes from the old loader</li>
 *   <li><b>Avoid static state</b> - Static fields in loaded classes prevent GC</li>
 *   <li><b>Be careful with ThreadLocal</b> - Can leak ClassLoader references</li>
 * </ol>
 *
 * <p><b>Example - Proper Hot Reload:</b></p>
 * <pre>{@code
 * // Old way (MEMORY LEAK - old ClassLoader never released):
 * JClassLoader loader = JClassLoader.builder()...build();
 * // ... use loader ...
 * loader = JClassLoader.builder()...build();  // OLD LOADER LEAKED!
 *
 * // Correct way:
 * JClassLoader oldLoader = currentLoader;
 * JClassLoader newLoader = JClassLoader.builder()...build();
 *
 * // Switch to new loader
 * currentLoader = newLoader;
 *
 * // Release old loader and all its classes
 * if (oldLoader != null) {
 *     oldLoader.close();  // Close resources
 *     oldLoader = null;   // Clear reference
 * }
 * System.gc();  // Suggest GC (doesn't guarantee collection)
 * }</pre>
 *
 * <h3>Memory Leak Detection</h3>
 * <p>To detect ClassLoader leaks in your application:</p>
 * <ul>
 *   <li>Use heap dumps and memory profilers (VisualVM, YourKit, JProfiler)</li>
 *   <li>Look for multiple instances of the same ClassLoader class</li>
 *   <li>Check for references from Thread contexts, static fields, or caches</li>
 *   <li>Monitor {@link #isClosed()} - closed loaders should be GC'd</li>
 * </ul>
 *
 * <h3>Common Memory Leak Causes</h3>
 * <ul>
 *   <li><b>ThreadLocal variables</b> holding class references</li>
 *   <li><b>Static caches</b> holding instances from dynamically loaded classes</li>
 *   <li><b>Logging frameworks</b> holding references to logger instances</li>
 *   <li><b>Thread pools</b> with threads created by loaded classes</li>
 *   <li><b>Weak/Soft reference caches</b> not cleared on reload</li>
 * </ul>
 *
 * @see #close()
 * @see #isClosed()
 */
public class JClassLoader extends ClassLoader implements AutoCloseable {
    private final List<ClassSource> classSources;
    private final ClassCache cache;
    private final boolean useCache;
    private final DelegationStrategy delegationStrategy;
    private final List<ClassLoaderLifecycleListener> listeners;
    private final BytecodeVerifier bytecodeVerifier;
    private volatile boolean closed = false;

    private JClassLoader(Builder builder) {
        super(builder.parent != null ? builder.parent : getSystemClassLoader());
        this.classSources = new ArrayList<>(builder.classSources);
        this.cache = builder.cache;
        this.useCache = builder.useCache && cache != null;
        this.delegationStrategy = builder.delegationStrategy;
        this.listeners = new CopyOnWriteArrayList<>(builder.listeners);
        this.bytecodeVerifier = builder.bytecodeVerifier;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        // Check if already loaded
        Class<?> c = findLoadedClass(name);
        if (c != null) {
            return c;
        }

        // Use delegation strategy
        c = delegationStrategy.loadClass(name, getParent(), this::findClassInternal);

        if (resolve) {
            resolveClass(c);
        }

        return c;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        return findClassInternal(name);
    }

    private Class<?> findClassInternal(String name) throws ClassNotFoundException {
        if (closed) {
            throw new IllegalStateException("JClassLoader is closed");
        }

        byte[] classData = null;
        ClassSource usedSource = null;

        // Try cache first (single operation to avoid TOCTOU race)
        if (useCache && cache != null) {
            classData = cache.get(name);
            if (classData != null) {
                fireClassCacheHit(name);
                verifyBytecode(name, classData);
                return defineClass(name, classData, 0, classData.length);
            }
        }

        // Load from sources with fast-fail approach
        List<String> attemptedSources = new ArrayList<>();
        List<String> failureReasons = new ArrayList<>();

        for (ClassSource source : classSources) {
            // Fast path: check if source can load before attempting expensive network call
            if (!source.canLoad(name)) {
                continue;
            }

            attemptedSources.add(source.getDescription());

            try {
                long loadStartTime = System.nanoTime();
                classData = source.loadClassData(name);

                if (classData != null) {
                    usedSource = source;
                    long loadTime = System.nanoTime() - loadStartTime;

                    // Validate class data
                    validateClassData(name, classData);

                    // Verify bytecode
                    verifyBytecode(name, classData);

                    // Cache successful load
                    if (useCache && cache != null) {
                        tryCacheClassData(name, classData);
                    }

                    // Define the class
                    Class<?> clazz = defineClass(name, classData, 0, classData.length);

                    // Fire event with accurate load time
                    fireClassLoaded(new ClassLoadEvent(name, usedSource, loadTime, classData.length));

                    return clazz;
                }
            } catch (IOException e) {
                failureReasons.add(source.getDescription() + ": " + e.getMessage());
                // Continue to next source
            }
        }

        // Build detailed error message
        String errorMsg = "Class not found: " + name +
                         " (tried " + attemptedSources.size() + " sources";
        if (!failureReasons.isEmpty()) {
            errorMsg += ", failures: " + String.join("; ", failureReasons);
        }
        errorMsg += ")";

        ClassNotFoundException ex = new ClassNotFoundException(errorMsg);
        fireClassLoadFailed(name, ex);
        throw ex;
    }

    /**
     * Validates class bytecode data.
     * Checks magic number and minimum size to prevent invalid class data from being loaded.
     */
    private void validateClassData(String name, byte[] classData) throws ClassNotFoundException {
        if (classData.length < 4) {
            throw new ClassNotFoundException(
                name + ": Invalid class data (too small: " + classData.length + " bytes)");
        }

        // Check magic number (0xCAFEBABE)
        if (classData[0] != (byte)0xCA || classData[1] != (byte)0xFE ||
            classData[2] != (byte)0xBA || classData[3] != (byte)0xBE) {
            throw new ClassNotFoundException(
                name + ": Invalid class file magic number");
        }
    }

    /**
     * Verifies bytecode if verifier is configured.
     * Extracted to avoid code duplication.
     */
    private void verifyBytecode(String name, byte[] classData) throws ClassNotFoundException {
        if (bytecodeVerifier != null) {
            try {
                bytecodeVerifier.verify(name, classData);
            } catch (SecurityException e) {
                ClassNotFoundException ex = new ClassNotFoundException(
                    "Bytecode verification failed: " + name, e);
                fireClassLoadFailed(name, ex);
                throw ex;
            }
        }
    }

    /**
     * Attempts to cache class data, logging failures but not throwing exceptions.
     * Cache failures should not prevent class loading from succeeding.
     */
    private void tryCacheClassData(String name, byte[] classData) {
        try {
            cache.put(name, classData);
            fireClassCached(name, classData);
        } catch (IOException e) {
            logError("Failed to cache class " + name + ": " + e.getMessage());
            fireClassCacheFailed(name, e);
        } catch (Throwable e) {
            // Catch all errors (including OutOfMemoryError) to prevent cache failures
            // from breaking class loading
            logError("Unexpected error caching class " + name + ": " + e.getMessage());
            fireClassCacheFailed(name, e);
        }
    }

    private void fireClassLoaded(ClassLoadEvent event) {
        for (ClassLoaderLifecycleListener listener : listeners) {
            try {
                listener.onClassLoaded(event);
            } catch (Exception e) {
                // Don't let listener exceptions break class loading, but log them
                logError("Listener error in " + listener.getClass().getSimpleName() +
                        ".onClassLoaded: " + e.getMessage());
            }
        }
    }

    private void fireClassCacheHit(String className) {
        for (ClassLoaderLifecycleListener listener : listeners) {
            try {
                listener.onClassCacheHit(className);
            } catch (Exception e) {
                // Don't let listener exceptions break class loading, but log them
                logError("Listener error in " + listener.getClass().getSimpleName() +
                        ".onClassCacheHit: " + e.getMessage());
            }
        }
    }

    private void fireClassCached(String className, byte[] classData) {
        for (ClassLoaderLifecycleListener listener : listeners) {
            try {
                listener.onClassCached(className, classData);
            } catch (Exception e) {
                // Don't let listener exceptions break class loading, but log them
                logError("Listener error in " + listener.getClass().getSimpleName() +
                        ".onClassCached: " + e.getMessage());
            }
        }
    }

    private void fireClassLoadFailed(String className, Throwable error) {
        for (ClassLoaderLifecycleListener listener : listeners) {
            try {
                listener.onClassLoadFailed(className, error);
            } catch (Exception e) {
                // Don't let listener exceptions break class loading, but log them
                logError("Listener error in " + listener.getClass().getSimpleName() +
                        ".onClassLoadFailed: " + e.getMessage());
            }
        }
    }

    private void fireClassCacheFailed(String className, Throwable error) {
        for (ClassLoaderLifecycleListener listener : listeners) {
            try {
                listener.onClassCacheFailed(className, error);
            } catch (Exception e) {
                // Don't let listener exceptions break class loading, but log them
                logError("Listener error in " + listener.getClass().getSimpleName() +
                        ".onClassCacheFailed: " + e.getMessage());
            }
        }
    }

    /**
     * Logs an error message. Uses SLF4J if available, otherwise falls back to System.err.
     */
    private static void logError(String message) {
        try {
            // Try SLF4J if available (optional dependency)
            Class<?> loggerFactoryClass = Class.forName("org.slf4j.LoggerFactory");
            Object logger = loggerFactoryClass.getMethod("getLogger", Class.class)
                .invoke(null, JClassLoader.class);
            logger.getClass().getMethod("error", String.class).invoke(logger, message);
        } catch (Exception e) {
            // SLF4J not available or error occurred, fall back to System.err
            System.err.println("[JClassLoader ERROR] " + message);
        }
    }

    /**
     * Closes this ClassLoader and releases all resources.
     * This includes closing all AutoCloseable class sources, cache, and notifying listeners.
     * After closing, this ClassLoader cannot load any more classes.
     *
     * @throws IOException if an error occurs while closing resources
     */
    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }

        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;

            List<Exception> exceptions = new ArrayList<>();

            // Close all closeable class sources
            for (ClassSource source : classSources) {
                if (source instanceof AutoCloseable) {
                    try {
                        ((AutoCloseable) source).close();
                    } catch (Exception e) {
                        exceptions.add(e);
                    }
                }
            }

            // Close cache if closeable
            if (cache instanceof AutoCloseable) {
                try {
                    ((AutoCloseable) cache).close();
                } catch (Exception e) {
                    exceptions.add(e);
                }
            }

            // Notify listeners
            for (ClassLoaderLifecycleListener listener : listeners) {
                try {
                    listener.onClassLoaderClosed();
                } catch (Exception e) {
                    exceptions.add(e);
                }
            }

            // Throw if any exceptions occurred
            if (!exceptions.isEmpty()) {
                IOException ex = new IOException("Failed to close JClassLoader");
                exceptions.forEach(ex::addSuppressed);
                throw ex;
            }
        }
    }

    /**
     * Checks if this ClassLoader is closed.
     *
     * @return true if closed, false otherwise
     */
    public boolean isClosed() {
        return closed;
    }

    public List<ClassSource> getClassSources() {
        return Collections.unmodifiableList(classSources);
    }

    public ClassCache getCache() {
        return cache;
    }

    public boolean isCacheEnabled() {
        return useCache;
    }

    public DelegationStrategy getDelegationStrategy() {
        return delegationStrategy;
    }

    public List<ClassLoaderLifecycleListener> getListeners() {
        return Collections.unmodifiableList(listeners);
    }

    public BytecodeVerifier getBytecodeVerifier() {
        return bytecodeVerifier;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private static final int MAX_CLASS_SOURCES = 100;

        private ClassLoader parent;
        private final List<ClassSource> classSources = new ArrayList<>();
        private ClassCache cache;
        private boolean useCache = true;
        private DelegationStrategy delegationStrategy = new ParentFirstDelegation();
        private final List<ClassLoaderLifecycleListener> listeners = new ArrayList<>();
        private BytecodeVerifier bytecodeVerifier;

        public Builder parent(ClassLoader parent) {
            this.parent = parent;
            return this;
        }

        public Builder addClassSource(ClassSource source) {
            Objects.requireNonNull(source, "source cannot be null");
            if (classSources.size() >= MAX_CLASS_SOURCES) {
                throw new IllegalStateException(
                    "Too many class sources (max " + MAX_CLASS_SOURCES + ")");
            }
            this.classSources.add(source);
            return this;
        }

        public Builder addLocalSource(String path) {
            return addClassSource(new LocalClassSource(path));
        }

        public Builder addRemoteSource(String baseUrl) {
            return addClassSource(new RemoteClassSource(baseUrl));
        }

        public Builder addRemoteSource(String baseUrl, AuthConfig authConfig) {
            return addClassSource(new RemoteClassSource(baseUrl, authConfig));
        }

        public Builder addRemoteJar(String jarUrl) {
            return addClassSource(new JarRemoteClassSource(jarUrl));
        }

        public Builder addRemoteJar(String jarUrl, AuthConfig authConfig) {
            return addClassSource(new JarRemoteClassSource(jarUrl, authConfig));
        }

        public Builder addNexusRawSource(String nexusUrl, String repository) {
            return addClassSource(new NexusClassSource(nexusUrl, repository, NexusClassSource.NexusMode.RAW));
        }

        public Builder addNexusRawSource(String nexusUrl, String repository, AuthConfig authConfig) {
            return addClassSource(new NexusClassSource(nexusUrl, repository, NexusClassSource.NexusMode.RAW, authConfig));
        }

        public Builder addNexusMavenSource(MavenNexusClassSource source) {
            return addClassSource(source);
        }

        public Builder addMavenCentral(String... artifactCoordinates) {
            MavenRepositoryClassSource.Builder builder = MavenRepositoryClassSource.builder()
                .mavenCentral();
            for (String coords : artifactCoordinates) {
                builder.addArtifact(coords);
            }
            return addClassSource(builder.build());
        }

        public Builder addMavenRepository(String repositoryUrl, String... artifactCoordinates) {
            MavenRepositoryClassSource.Builder builder = MavenRepositoryClassSource.builder()
                .repositoryUrl(repositoryUrl);
            for (String coords : artifactCoordinates) {
                builder.addArtifact(coords);
            }
            return addClassSource(builder.build());
        }

        public Builder addDatabaseSource(javax.sql.DataSource dataSource, String tableName,
                                        String classNameColumn, String classBytesColumn) {
            return addClassSource(new DatabaseClassSource(dataSource, tableName,
                                                         classNameColumn, classBytesColumn));
        }

        public Builder addRestApiSource(RestApiClassSource source) {
            return addClassSource(source);
        }

        public Builder addCloudStorage(org.flossware.cloud.storage.CloudStorageClient client) {
            return addClassSource(new CloudStorageClassSource(client));
        }

        public Builder cache(ClassCache cache) {
            this.cache = cache;
            return this;
        }

        public Builder useCache(boolean useCache) {
            this.useCache = useCache;
            return this;
        }

        public Builder delegationStrategy(DelegationStrategy strategy) {
            this.delegationStrategy = Objects.requireNonNull(strategy, "delegationStrategy cannot be null");
            return this;
        }

        public Builder parentFirst() {
            return delegationStrategy(new ParentFirstDelegation());
        }

        public Builder parentLast(String... alwaysParentPrefixes) {
            return delegationStrategy(new org.flossware.jclassloader.delegation.ParentLastDelegation(alwaysParentPrefixes));
        }

        public Builder customDelegation(java.util.function.Predicate<String> parentFirstPredicate) {
            return delegationStrategy(new org.flossware.jclassloader.delegation.CustomDelegation(parentFirstPredicate));
        }

        public Builder addListener(ClassLoaderLifecycleListener listener) {
            this.listeners.add(Objects.requireNonNull(listener, "listener cannot be null"));
            return this;
        }

        public Builder addLoggingListener() {
            return addListener(new org.flossware.jclassloader.lifecycle.LoggingListener());
        }

        public Builder addLoggingListener(boolean verbose) {
            return addListener(new org.flossware.jclassloader.lifecycle.LoggingListener(verbose));
        }

        public Builder trackResources() {
            return addListener(new org.flossware.jclassloader.lifecycle.ResourceTrackingListener());
        }

        public Builder bytecodeVerifier(BytecodeVerifier verifier) {
            this.bytecodeVerifier = verifier;
            return this;
        }

        public JClassLoader build() {
            if (classSources.isEmpty()) {
                throw new IllegalStateException("At least one class source must be configured");
            }

            return new JClassLoader(this);
        }
    }
}
