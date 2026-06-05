package org.flossware.classloader;

import org.flossware.classloader.jar.JarClassExtractor;
import org.flossware.classloader.jar.JarDownloadManager;
import org.flossware.classloader.util.ClassNameUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.jar.JarFile;

/**
 * ClassSource implementation for loading classes from Maven artifacts stored in Nexus.
 * Downloads JARs from Nexus and extracts class files from them.
 * Caches downloaded JAR files for efficient multi-class extraction.
 * Implements AutoCloseable - call close() to release resources and delete cached JAR files.
 *
 * <p><b>Caching Strategy:</b> JAR files are downloaded once per artifact and cached in temporary files.
 * Multiple class requests from the same artifact reuse the cached JAR without re-downloading.</p>
 *
 * <p><b>Thread Safety:</b> This class is thread-safe. The internal caches use ConcurrentHashMap
 * to support concurrent class loading operations.</p>
 */
public class MavenNexusClassSource implements ClassSource, AutoCloseable {
    private static final int DEFAULT_CONNECT_TIMEOUT = 10000; // 10 seconds
    private static final int DEFAULT_READ_TIMEOUT = 30000; // 30 seconds

    private final String nexusUrl;
    private final String repository;
    private final List<MavenArtifact> artifacts;
    private final AuthConfig authConfig;
    private final Map<String, byte[]> classCache;
    private final ConcurrentHashMap<String, FutureTask<JarFile>> jarFileCache;
    private final ConcurrentHashMap<String, Path> jarPathCache;
    private volatile boolean closed = false;
    private final ReentrantReadWriteLock closeLock = new ReentrantReadWriteLock();

    // Helper components for JAR download and class extraction
    private final JarDownloadManager downloadManager;
    private final JarClassExtractor classExtractor;

    /**
     * Creates a Maven Nexus class source with full configuration including timeouts.
     *
     * @param nexusUrl The Nexus server URL
     * @param repository The Maven repository name
     * @param artifacts The list of Maven artifacts to load classes from
     * @param authConfig The authentication configuration
     * @param connectTimeout Connection timeout in milliseconds
     * @param readTimeout Read timeout in milliseconds
     * @throws NullPointerException if nexusUrl, repository, or artifacts is null
     * @throws IllegalArgumentException if artifacts list is empty or timeouts are negative
     */
    public MavenNexusClassSource(String nexusUrl, String repository, List<MavenArtifact> artifacts,
                                AuthConfig authConfig, int connectTimeout, int readTimeout) {
        Objects.requireNonNull(nexusUrl, "nexusUrl cannot be null");
        Objects.requireNonNull(artifacts, "artifacts cannot be null");

        if (artifacts.isEmpty()) {
            throw new IllegalArgumentException("At least one Maven artifact must be specified");
        }
        if (connectTimeout < 0) {
            throw new IllegalArgumentException("connectTimeout must be >= 0");
        }
        if (readTimeout < 0) {
            throw new IllegalArgumentException("readTimeout must be >= 0");
        }

        this.nexusUrl = nexusUrl.endsWith("/") ? nexusUrl : nexusUrl + "/";
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
        this.artifacts = new ArrayList<>(artifacts);
        this.authConfig = authConfig != null ? authConfig : AuthConfig.none();
        this.classCache = new ConcurrentHashMap<>();
        this.jarFileCache = new ConcurrentHashMap<>();
        this.jarPathCache = new ConcurrentHashMap<>();
        this.downloadManager = new JarDownloadManager(connectTimeout, readTimeout, this.authConfig);
        this.classExtractor = new JarClassExtractor();
    }

    /**
     * Creates a Maven Nexus class source with default timeouts.
     *
     * @param nexusUrl The Nexus server URL
     * @param repository The Maven repository name
     * @param artifacts The list of Maven artifacts to load classes from
     * @param authConfig The authentication configuration
     */
    public MavenNexusClassSource(String nexusUrl, String repository, List<MavenArtifact> artifacts, AuthConfig authConfig) {
        this(nexusUrl, repository, artifacts, authConfig, DEFAULT_CONNECT_TIMEOUT, DEFAULT_READ_TIMEOUT);
    }

    /**
     * Creates a Maven Nexus class source without authentication.
     *
     * @param nexusUrl The Nexus server URL
     * @param repository The Maven repository name
     * @param artifacts The list of Maven artifacts to load classes from
     */
    public MavenNexusClassSource(String nexusUrl, String repository, List<MavenArtifact> artifacts) {
        this(nexusUrl, repository, artifacts, AuthConfig.none());
    }

    /**
     * {@inheritDoc}
     *
     * <p>Searches through all configured Maven artifacts in order, downloading JARs
     * from Nexus and extracting the requested class file. Results are cached in memory.</p>
     */
    @Override
    public byte[] loadClassData(String className) throws IOException {
        Objects.requireNonNull(className, "className cannot be null");

        // Check cache first without locking (optimization for cache hits)
        byte[] cachedData = classCache.get(className);
        if (cachedData != null) {
            return cachedData;
        }

        // Acquire read lock to prevent close() from running while we create resources.
        closeLock.readLock().lock();
        try {
            if (closed) {
                throw new IllegalStateException("MavenNexusClassSource is closed");
            }

            // Double-check cache (another thread may have loaded it while we waited for lock)
            cachedData = classCache.get(className);
            if (cachedData != null) {
                return cachedData;
            }

            String classFileName = ClassNameUtil.toClassFilePath(className);
            List<String> errorMessages = new ArrayList<>();

            for (MavenArtifact artifact : artifacts) {
                try {
                    String jarUrl = buildJarUrl(artifact);
                    String artifactKey = artifact.toString();
                    JarFile jarFile = ensureJarCached(artifactKey, jarUrl);
                    byte[] classData = classExtractor.extractClassFromJar(jarFile, classFileName, jarUrl);
                    classCache.put(className, classData);
                    return classData;
                } catch (IOException e) {
                    errorMessages.add(String.format("Artifact %s - %s", artifact.toString(), e.getMessage()));
                }
            }

            String allErrors = String.join("\n  - ", errorMessages);
            throw new IOException(
                "Class not found in any of " + artifacts.size() + " configured Maven artifacts: " +
                className + "\nAttempted artifacts:\n  - " + allErrors
            );
        } finally {
            closeLock.readLock().unlock();
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean canLoad(String className) {
        Objects.requireNonNull(className, "className cannot be null");
        try {
            loadClassData(className);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** {@inheritDoc} */
    @Override
    public String getDescription() {
        closeLock.readLock().lock();
        try {
            return "MavenNexusClassSource[" + nexusUrl + ", repo=" + repository +
                   ", artifacts=" + artifacts.size() + ", auth=" + authConfig.getAuthType() + "]";
        } finally {
            closeLock.readLock().unlock();
        }
    }

    private String buildJarUrl(MavenArtifact artifact) {
        return nexusUrl + "repository/" + repository + "/" + artifact.toPath();
    }

    /**
     * Ensures a JAR file is cached for the given artifact.
     * Downloads the JAR once and reuses it for subsequent class extractions.
     *
     * <p>Uses a FutureTask-based per-artifact locking pattern so that downloads of different
     * artifacts proceed fully in parallel, while duplicate downloads of the same artifact
     * are coalesced -- only the first thread downloads, and subsequent threads wait on
     * the same Future.</p>
     */
    private JarFile ensureJarCached(String artifactKey, String jarUrl) throws IOException {
        FutureTask<JarFile> newTask = new FutureTask<>(new Callable<JarFile>() {
            @Override
            public JarFile call() throws IOException {
                Path tempJarPath = Files.createTempFile("jclassloader-nexus-", ".jar");
                jarPathCache.put(artifactKey, tempJarPath);
                try {
                    downloadManager.downloadJarFile(jarUrl, tempJarPath);
                    return new JarFile(tempJarPath.toFile());
                } catch (IOException e) {
                    jarPathCache.remove(artifactKey);
                    try {
                        Files.deleteIfExists(tempJarPath);
                    } catch (IOException ignored) {
                        // Ignore cleanup errors
                    }
                    throw e;
                }
            }
        });

        FutureTask<JarFile> existingTask = jarFileCache.putIfAbsent(artifactKey, newTask);
        if (existingTask == null) {
            newTask.run();
            existingTask = newTask;
        }

        try {
            return existingTask.get();
        } catch (ExecutionException e) {
            jarFileCache.remove(artifactKey, existingTask);
            Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            throw new IOException("Failed to download artifact: " + artifactKey, cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for artifact download: " + artifactKey, e);
        } catch (CancellationException e) {
            jarFileCache.remove(artifactKey, existingTask);
            Path tempPath = jarPathCache.remove(artifactKey);
            if (tempPath != null) {
                try {
                    Files.deleteIfExists(tempPath);
                } catch (IOException ignored) {
                    // Ignore cleanup errors
                }
            }
            throw new IOException("Artifact download was cancelled: " + artifactKey, e);
        }
    }

    /**
     * Adds a Maven artifact to load classes from.
     * Thread-safe: synchronizes with {@link #loadClassData(String)} to prevent ConcurrentModificationException.
     *
     * @param artifact The Maven artifact to add
     * @throws NullPointerException if artifact is null
     * @throws IllegalStateException if this class source is closed
     */
    public void addArtifact(MavenArtifact artifact) {
        Objects.requireNonNull(artifact, "artifact cannot be null");
        closeLock.writeLock().lock();
        try {
            if (closed) {
                throw new IllegalStateException("MavenNexusClassSource is closed");
            }
            artifacts.add(artifact);
        } finally {
            closeLock.writeLock().unlock();
        }
    }

    /**
     * Adds a Maven artifact by coordinate string.
     * Parses the coordinates in the format "groupId:artifactId:version".
     *
     * @param coordinates Maven coordinates string
     */
    public void addArtifact(String coordinates) {
        addArtifact(MavenArtifact.parse(coordinates));
    }

    /**
     * Gets the list of configured Maven artifacts.
     * Thread-safe: returns a copy of the artifacts list under synchronization.
     *
     * @return a copy of the artifacts list
     */
    public List<MavenArtifact> getArtifacts() {
        closeLock.readLock().lock();
        try {
            return new ArrayList<>(artifacts);
        } finally {
            closeLock.readLock().unlock();
        }
    }

    /**
     * Gets the Nexus server URL.
     *
     * @return the Nexus URL
     */
    public String getNexusUrl() {
        return nexusUrl;
    }

    /**
     * Gets the Maven repository name in Nexus.
     *
     * @return the repository name
     */
    public String getRepository() {
        return repository;
    }

    /**
     * Gets the authentication configuration.
     *
     * @return the authentication configuration
     */
    public AuthConfig getAuthConfig() {
        return authConfig;
    }

    /**
     * Closes all cached JAR files and deletes their temporary files.
     *
     * @throws IOException if an error occurs during cleanup
     */
    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }

        closeLock.writeLock().lock();
        try {
            if (closed) {
                return;
            }
            closed = true;

            List<IOException> exceptions = new ArrayList<>();

            for (FutureTask<JarFile> task : jarFileCache.values()) {
                try {
                    if (task.isDone() && !task.isCancelled()) {
                        task.get().close();
                    }
                } catch (ExecutionException e) {
                    // Task failed during download -- nothing to close
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    exceptions.add(new IOException("Interrupted during close", e));
                } catch (IOException e) {
                    exceptions.add(e);
                }
            }
            jarFileCache.clear();

            for (Path tempPath : jarPathCache.values()) {
                try {
                    Files.deleteIfExists(tempPath);
                } catch (IOException e) {
                    exceptions.add(e);
                }
            }
            jarPathCache.clear();

            if (!exceptions.isEmpty()) {
                IOException ex = new IOException("Failed to close MavenNexusClassSource");
                exceptions.forEach(ex::addSuppressed);
                throw ex;
            }
        } finally {
            closeLock.writeLock().unlock();
        }
    }

    /**
     * Checks if this class source is closed.
     *
     * @return true if closed, false otherwise
     */
    public boolean isClosed() {
        return closed;
    }

    /**
     * Creates a new Builder for constructing MavenNexusClassSource instances.
     *
     * @return a new Builder with default configuration
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for constructing MavenNexusClassSource instances with fluent API.
     *
     * <p>Configures Nexus Maven repository access for class loading.</p>
     *
     * <p><b>Example:</b></p>
     * <pre>{@code
     * MavenNexusClassSource source = MavenNexusClassSource.builder()
     *     .nexusUrl("https://nexus.example.com")
     *     .repository("maven-releases")
     *     .addArtifact("com.example:my-lib:1.0.0")
     *     .auth(AuthConfig.basic("user", "pass"))
     *     .build();
     * }</pre>
     */
    public static class Builder {
        private String nexusUrl;
        private String repository;
        private final List<MavenArtifact> artifacts = new ArrayList<>();
        private AuthConfig authConfig = AuthConfig.none();
        private int connectTimeout = DEFAULT_CONNECT_TIMEOUT;
        private int readTimeout = DEFAULT_READ_TIMEOUT;

        /** Sets the Nexus server URL.
         * @param nexusUrl The Nexus URL
         * @return this builder */
        public Builder nexusUrl(String nexusUrl) {
            this.nexusUrl = Objects.requireNonNull(nexusUrl, "nexusUrl cannot be null");
            return this;
        }

        /** Sets the Maven repository name in Nexus.
         * @param repository The repository name
         * @return this builder */
        public Builder repository(String repository) {
            this.repository = Objects.requireNonNull(repository, "repository cannot be null");
            return this;
        }

        /** Adds a Maven artifact to load classes from.
         * @param artifact The Maven artifact descriptor
         * @return this builder */
        public Builder addArtifact(MavenArtifact artifact) {
            this.artifacts.add(Objects.requireNonNull(artifact, "artifact cannot be null"));
            return this;
        }

        /** Adds a Maven artifact using individual coordinates.
         * @param groupId The Maven group ID
         * @param artifactId The Maven artifact ID
         * @param version The artifact version
         * @return this builder */
        public Builder addArtifact(String groupId, String artifactId, String version) {
            return addArtifact(new MavenArtifact(groupId, artifactId, version));
        }

        /** Adds a Maven artifact by coordinate string (format: "groupId:artifactId:version").
         * @param coordinates Maven coordinates string
         * @return this builder */
        public Builder addArtifact(String coordinates) {
            return addArtifact(MavenArtifact.parse(coordinates));
        }

        /** Sets the authentication configuration for accessing Nexus.
         * @param authConfig Authentication configuration (null for no auth)
         * @return this builder */
        public Builder auth(AuthConfig authConfig) {
            this.authConfig = authConfig;
            return this;
        }

        /** Sets the connection timeout for Nexus requests.
         * @param timeoutMs Timeout in milliseconds (default: 10000ms, 0 = infinite)
         * @return this builder */
        public Builder connectTimeout(int timeoutMs) {
            if (timeoutMs < 0) {
                throw new IllegalArgumentException("connectTimeout must be >= 0");
            }
            this.connectTimeout = timeoutMs;
            return this;
        }

        /** Sets the read timeout for downloading JARs from Nexus.
         * @param timeoutMs Timeout in milliseconds (default: 30000ms, 0 = infinite)
         * @return this builder */
        public Builder readTimeout(int timeoutMs) {
            if (timeoutMs < 0) {
                throw new IllegalArgumentException("readTimeout must be >= 0");
            }
            this.readTimeout = timeoutMs;
            return this;
        }

        /** Builds the MavenNexusClassSource with configured settings.
         * @return a new MavenNexusClassSource instance */
        public MavenNexusClassSource build() {
            Objects.requireNonNull(nexusUrl, "nexusUrl must be set");
            Objects.requireNonNull(repository, "repository must be set");
            return new MavenNexusClassSource(nexusUrl, repository, artifacts, authConfig,
                                            connectTimeout, readTimeout);
        }
    }
}
