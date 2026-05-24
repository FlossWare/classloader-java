package org.flossware.jclassloader;

import org.flossware.jclassloader.cache.ClassCache;
import org.flossware.jclassloader.delegation.DelegationStrategy;
import org.flossware.jclassloader.delegation.ParentFirstDelegation;
import org.flossware.jclassloader.lifecycle.ClassLoadEvent;
import org.flossware.jclassloader.lifecycle.ClassLoaderLifecycleListener;

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
 * IMPORTANT: Always close this ClassLoader when done to prevent memory leaks,
 * especially in hot reload scenarios. Use try-with-resources:
 * <pre>
 * try (JClassLoader loader = JClassLoader.builder()...build()) {
 *     // Use loader
 * }  // Automatically closed
 * </pre>
 */
public class JClassLoader extends ClassLoader implements AutoCloseable {
    private final List<ClassSource> classSources;
    private final ClassCache cache;
    private final boolean useCache;
    private final DelegationStrategy delegationStrategy;
    private final List<ClassLoaderLifecycleListener> listeners;
    private volatile boolean closed = false;

    private JClassLoader(Builder builder) {
        super(builder.parent != null ? builder.parent : getSystemClassLoader());
        this.classSources = new ArrayList<>(builder.classSources);
        this.cache = builder.cache;
        this.useCache = builder.useCache && cache != null;
        this.delegationStrategy = builder.delegationStrategy;
        this.listeners = new CopyOnWriteArrayList<>(builder.listeners);
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

        long startTime = System.nanoTime();
        byte[] classData = null;
        ClassSource usedSource = null;

        // Check cache
        if (useCache && cache.contains(name)) {
            classData = cache.get(name);
            if (classData != null) {
                fireClassCacheHit(name);
                return defineClass(name, classData, 0, classData.length);
            }
        }

        // Load from sources - Optimistic loading approach
        // Skip canLoad() check to reduce network calls by 50% for remote sources
        for (ClassSource source : classSources) {
            try {
                classData = source.loadClassData(name);
                if (classData != null) {
                    usedSource = source;

                    // Cache it
                    if (useCache) {
                        try {
                            cache.put(name, classData);
                            fireClassCached(name, classData);
                        } catch (IOException e) {
                            // Continue even if caching fails
                        }
                    }

                    // Define the class
                    Class<?> clazz = defineClass(name, classData, 0, classData.length);

                    // Fire event
                    long loadTime = System.nanoTime() - startTime;
                    fireClassLoaded(new ClassLoadEvent(name, usedSource, loadTime, classData.length));

                    return clazz;
                }
            } catch (IOException e) {
                // Try next source - exception-based flow is common in Java ClassLoaders
            }
        }

        ClassNotFoundException ex = new ClassNotFoundException(name);
        fireClassLoadFailed(name, ex);
        throw ex;
    }

    private void fireClassLoaded(ClassLoadEvent event) {
        for (ClassLoaderLifecycleListener listener : listeners) {
            try {
                listener.onClassLoaded(event);
            } catch (Exception e) {
                // Don't let listener exceptions break class loading
            }
        }
    }

    private void fireClassCacheHit(String className) {
        for (ClassLoaderLifecycleListener listener : listeners) {
            try {
                listener.onClassCacheHit(className);
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    private void fireClassCached(String className, byte[] classData) {
        for (ClassLoaderLifecycleListener listener : listeners) {
            try {
                listener.onClassCached(className, classData);
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    private void fireClassLoadFailed(String className, Throwable error) {
        for (ClassLoaderLifecycleListener listener : listeners) {
            try {
                listener.onClassLoadFailed(className, error);
            } catch (Exception e) {
                // Ignore
            }
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

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private ClassLoader parent;
        private final List<ClassSource> classSources = new ArrayList<>();
        private ClassCache cache;
        private boolean useCache = true;
        private DelegationStrategy delegationStrategy = new ParentFirstDelegation();
        private final List<ClassLoaderLifecycleListener> listeners = new ArrayList<>();

        public Builder parent(ClassLoader parent) {
            this.parent = parent;
            return this;
        }

        public Builder addClassSource(ClassSource source) {
            Objects.requireNonNull(source, "source cannot be null");
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

        public Builder addSftpSource(String host, String username, String password, String basePath) {
            return addClassSource(SftpClassSource.builder()
                .host(host)
                .username(username)
                .password(password)
                .basePath(basePath)
                .build());
        }

        public Builder addWebDavSource(String baseUrl) {
            return addClassSource(new WebDavClassSource(baseUrl));
        }

        public Builder addWebDavSource(String baseUrl, String username, String password) {
            return addClassSource(new WebDavClassSource(baseUrl, username, password));
        }

        public Builder addDatabaseSource(javax.sql.DataSource dataSource, String tableName,
                                        String classNameColumn, String classBytesColumn) {
            return addClassSource(new DatabaseClassSource(dataSource, tableName,
                                                         classNameColumn, classBytesColumn));
        }

        public Builder addRestApiSource(RestApiClassSource source) {
            return addClassSource(source);
        }

        public Builder addS3Source(org.flossware.jclassloader.cloud.S3ClassSource source) {
            return addClassSource(source);
        }

        public Builder addAzureBlobSource(org.flossware.jclassloader.cloud.AzureBlobClassSource source) {
            return addClassSource(source);
        }

        public Builder addGcsSource(org.flossware.jclassloader.cloud.GcsClassSource source) {
            return addClassSource(source);
        }

        public Builder addGoogleDriveSource(org.flossware.jclassloader.cloud.GoogleDriveClassSource source) {
            return addClassSource(source);
        }

        public Builder addDropboxSource(org.flossware.jclassloader.cloud.DropboxClassSource source) {
            return addClassSource(source);
        }

        public Builder addOneDriveSource(org.flossware.jclassloader.cloud.OneDriveClassSource source) {
            return addClassSource(source);
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

        public JClassLoader build() {
            if (classSources.isEmpty()) {
                throw new IllegalStateException("At least one class source must be configured");
            }

            return new JClassLoader(this);
        }
    }
}
