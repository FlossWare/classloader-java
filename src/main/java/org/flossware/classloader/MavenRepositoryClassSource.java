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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.jar.JarFile;

/**
 * ClassSource implementation for loading classes from Maven repositories (Maven Central, custom repos).
 * Downloads JARs from the repository and extracts class files from them.
 * Caches downloaded JAR files for efficient multi-class extraction.
 * Implements AutoCloseable - call close() to release resources and delete cached JAR files.
 *
 * <p><b>Caching Strategy:</b> JAR files are downloaded once per artifact and cached in temporary files.
 * Multiple class requests from the same artifact reuse the cached JAR without re-downloading.</p>
 *
 * <p><b>Thread Safety:</b> This class is thread-safe. The internal caches use ConcurrentHashMap,
 * and the artifacts collection uses CopyOnWriteArrayList to support safe concurrent modification
 * and iteration during class loading operations.</p>
 */
public class MavenRepositoryClassSource implements ClassSource, AutoCloseable {
    private static final int DEFAULT_CONNECT_TIMEOUT = 10000; // 10 seconds
    private static final int DEFAULT_READ_TIMEOUT = 30000; // 30 seconds

    private final String repositoryUrl;
    private final List<MavenArtifact> artifacts;
    private final AuthConfig authConfig;
    private final Map<String, byte[]> classCache;
    private final Map<String, JarFile> jarFileCache;
    private final Map<String, Path> jarPathCache;
    private volatile boolean closed = false;
    private final Map<String, Object> perArtifactLocks = new ConcurrentHashMap<>();

    // Helper components for JAR download and class extraction
    private final JarDownloadManager downloadManager;
    private final JarClassExtractor classExtractor;

    /**
     * Creates a Maven repository class source with full configuration including timeouts.
     *
     * @param repositoryUrl The Maven repository URL (e.g., "https://repo1.maven.org/maven2/")
     * @param artifacts The list of Maven artifacts to load classes from
     * @param authConfig The authentication configuration
     * @param connectTimeout Connection timeout in milliseconds
     * @param readTimeout Read timeout in milliseconds
     * @throws NullPointerException if repositoryUrl or artifacts is null
     * @throws IllegalArgumentException if artifacts list is empty or timeouts are negative
     */
    public MavenRepositoryClassSource(String repositoryUrl, List<MavenArtifact> artifacts, AuthConfig authConfig,
                                     int connectTimeout, int readTimeout) {
        Objects.requireNonNull(repositoryUrl, "repositoryUrl cannot be null");
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

        this.repositoryUrl = repositoryUrl.endsWith("/") ? repositoryUrl : repositoryUrl + "/";
        this.artifacts = new CopyOnWriteArrayList<>(artifacts);
        this.authConfig = authConfig != null ? authConfig : AuthConfig.none();
        this.classCache = new ConcurrentHashMap<>();
        this.jarFileCache = new ConcurrentHashMap<>();
        this.jarPathCache = new ConcurrentHashMap<>();
        this.downloadManager = new JarDownloadManager(connectTimeout, readTimeout, this.authConfig);
        this.classExtractor = new JarClassExtractor();
    }

    /**
     * Creates a Maven repository class source with default timeouts.
     *
     * @param repositoryUrl The Maven repository URL
     * @param artifacts The list of Maven artifacts to load classes from
     * @param authConfig The authentication configuration
     */
    public MavenRepositoryClassSource(String repositoryUrl, List<MavenArtifact> artifacts, AuthConfig authConfig) {
        this(repositoryUrl, artifacts, authConfig, DEFAULT_CONNECT_TIMEOUT, DEFAULT_READ_TIMEOUT);
    }

    /**
     * Creates a Maven repository class source without authentication.
     *
     * @param repositoryUrl The Maven repository URL
     * @param artifacts The list of Maven artifacts to load classes from
     */
    public MavenRepositoryClassSource(String repositoryUrl, List<MavenArtifact> artifacts) {
        this(repositoryUrl, artifacts, AuthConfig.none());
    }

    /**
     * {@inheritDoc}
     *
     * <p>Searches through all configured Maven artifacts in order, downloading JARs
     * and extracting the requested class file. Results are cached in memory.</p>
     */
    @Override
    public byte[] loadClassData(String className) throws IOException {
        if (closed) {
            throw new IllegalStateException("MavenRepositoryClassSource is closed");
        }

        Objects.requireNonNull(className, "className cannot be null");
        byte[] cachedData = classCache.get(className);
        if (cachedData != null) {
            return cachedData;
        }

        return searchArtifactsForClass(className);
    }

    private byte[] searchArtifactsForClass(String className) throws IOException {
        String classFileName = ClassNameUtil.toClassFilePath(className);
        List<String> errorMessages = new ArrayList<>();

        for (MavenArtifact artifact : artifacts) {
            byte[] classData = tryLoadFromArtifact(artifact, classFileName, className, errorMessages);
            if (classData != null) {
                return classData;
            }
        }

        throwClassNotFoundInArtifacts(className, errorMessages);
        return null; // unreachable
    }

    private byte[] tryLoadFromArtifact(MavenArtifact artifact, String classFileName,
            String className, List<String> errorMessages) {
        try {
            String jarUrl = buildJarUrl(artifact);
            String artifactKey = artifact.toString();
            JarFile jarFile = ensureJarCached(artifactKey, jarUrl);
            byte[] classData = classExtractor.extractClassFromJar(jarFile, classFileName, jarUrl);
            classCache.put(className, classData);
            return classData;
        } catch (IOException e) {
            errorMessages.add(String.format("Artifact %s - %s", artifact.toString(), e.getMessage()));
            return null;
        }
    }

    private void throwClassNotFoundInArtifacts(String className, List<String> errorMessages)
            throws IOException {
        String allErrors = String.join("\n  - ", errorMessages);
        throw new IOException(
            "Class not found in any of " + artifacts.size() + " configured Maven artifacts: " +
            className + "\nAttempted artifacts:\n  - " + allErrors
        );
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
        return "MavenRepositoryClassSource[" + repositoryUrl + ", artifacts=" +
               artifacts.size() + ", auth=" + authConfig.getAuthType() + "]";
    }

    private String buildJarUrl(MavenArtifact artifact) {
        return repositoryUrl + artifact.toPath();
    }

    /**
     * Ensures a JAR file is cached for the given artifact.
     * Uses per-artifact synchronization to allow concurrent downloads of different artifacts.
     */
    private JarFile ensureJarCached(String artifactKey, String jarUrl) throws IOException {
        JarFile existing = jarFileCache.get(artifactKey);
        if (existing != null) {
            return existing;
        }

        Object artifactLock = perArtifactLocks.computeIfAbsent(artifactKey, k -> new Object());

        synchronized (artifactLock) {
            if (closed) {
                throw new IllegalStateException("MavenRepositoryClassSource is closed");
            }

            existing = jarFileCache.get(artifactKey);
            if (existing != null) {
                return existing;
            }

            Path tempJarPath = Files.createTempFile("jclassloader-maven-", ".jar");
            try {
                downloadManager.downloadJarFile(jarUrl, tempJarPath);
                JarFile jarFile = new JarFile(tempJarPath.toFile());
                jarFileCache.put(artifactKey, jarFile);
                jarPathCache.put(artifactKey, tempJarPath);
                return jarFile;
            } catch (IOException e) {
                try {
                    Files.deleteIfExists(tempJarPath);
                } catch (IOException ignored) {
                    // Ignore cleanup errors
                }
                throw e;
            }
        }
    }

    /**
     * Adds a Maven artifact to load classes from.
     *
     * @param artifact The Maven artifact to add
     * @throws NullPointerException if artifact is null
     * @throws IllegalStateException if this class source is closed
     */
    public void addArtifact(MavenArtifact artifact) {
        Objects.requireNonNull(artifact, "artifact cannot be null");
        if (closed) {
            throw new IllegalStateException("MavenRepositoryClassSource is closed");
        }
        artifacts.add(artifact);
    }

    /**
     * Adds a Maven artifact by coordinate string.
     * Parses the coordinates in the format "groupId:artifactId:version".
     *
     * @param coordinates Maven coordinates string
     * @throws NullPointerException if coordinates is null
     * @throws IllegalStateException if this class source is closed
     */
    public void addArtifact(String coordinates) {
        Objects.requireNonNull(coordinates, "coordinates cannot be null");
        addArtifact(MavenArtifact.parse(coordinates));
    }

    /**
     * Gets the list of configured Maven artifacts.
     *
     * @return a copy of the artifacts list
     */
    public List<MavenArtifact> getArtifacts() {
        return new ArrayList<>(artifacts);
    }

    /**
     * Gets the Maven repository URL.
     *
     * @return the repository URL
     */
    public String getRepositoryUrl() {
        return repositoryUrl;
    }

    /**
     * Closes all cached JAR files and deletes their temporary files.
     *
     * <p>Acquires ALL per-artifact lock objects to ensure mutual exclusion with
     * ensureJarCached(), preventing resource leaks from concurrent close and load operations.</p>
     *
     * @throws IOException if an error occurs during cleanup
     */
    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }

        synchronized (perArtifactLocks) {
            if (closed) {
                return;
            }

            acquireAllArtifactLocks();
            closed = true;
            closeAllResources();
        }
    }

    private void acquireAllArtifactLocks() {
        for (Object lock : perArtifactLocks.values()) {
            synchronized (lock) {
                // Acquire each lock to block threads in ensureJarCached()
            }
        }
    }

    private void closeAllResources() throws IOException {
        List<IOException> exceptions = new ArrayList<>();

        closeCachedJarFiles(exceptions);
        jarFileCache.clear();

        deleteTempFiles(exceptions);
        jarPathCache.clear();

        throwAggregatedExceptions(exceptions, "Failed to close MavenRepositoryClassSource");
    }

    private void closeCachedJarFiles(List<IOException> exceptions) {
        for (JarFile jarFile : jarFileCache.values()) {
            try {
                jarFile.close();
            } catch (IOException e) {
                exceptions.add(e);
            }
        }
    }

    private void deleteTempFiles(List<IOException> exceptions) {
        for (Path tempPath : jarPathCache.values()) {
            try {
                Files.deleteIfExists(tempPath);
            } catch (IOException e) {
                exceptions.add(e);
            }
        }
    }

    private void throwAggregatedExceptions(List<IOException> exceptions, String message)
            throws IOException {
        if (exceptions.isEmpty()) {
            return;
        }
        IOException ex = new IOException(message);
        exceptions.forEach(ex::addSuppressed);
        throw ex;
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
     * Creates a new Builder for constructing MavenRepositoryClassSource instances.
     *
     * @return A new Builder with default configuration
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for constructing MavenRepositoryClassSource instances with fluent API.
     *
     * <p><b>Example (Maven Central):</b></p>
     * <pre>{@code
     * MavenRepositoryClassSource source = MavenRepositoryClassSource.builder()
     *     .mavenCentral()
     *     .addArtifact("com.google.guava:guava:32.1.0-jre")
     *     .build();
     * }</pre>
     */
    public static class Builder {
        private String repositoryUrl;
        private final List<MavenArtifact> artifacts = new ArrayList<>();
        private AuthConfig authConfig = AuthConfig.none();
        private int connectTimeout = DEFAULT_CONNECT_TIMEOUT;
        private int readTimeout = DEFAULT_READ_TIMEOUT;

        /** Sets a custom Maven repository URL.
         * @param repositoryUrl Maven repository URL
         * @return this builder */
        public Builder repositoryUrl(String repositoryUrl) {
            this.repositoryUrl = Objects.requireNonNull(repositoryUrl, "repositoryUrl cannot be null");
            return this;
        }

        /** Uses Maven Central as the repository.
         * @return this builder */
        public Builder mavenCentral() {
            this.repositoryUrl = "https://repo1.maven.org/maven2/";
            return this;
        }

        /** Adds a Maven artifact to load classes from.
         * @param artifact Maven artifact descriptor
         * @return this builder */
        public Builder addArtifact(MavenArtifact artifact) {
            this.artifacts.add(Objects.requireNonNull(artifact, "artifact cannot be null"));
            return this;
        }

        /** Adds a Maven artifact using individual coordinates.
         * @param groupId Group ID
         * @param artifactId Artifact ID
         * @param version Version
         * @return this builder */
        public Builder addArtifact(String groupId, String artifactId, String version) {
            return addArtifact(new MavenArtifact(groupId, artifactId, version));
        }

        /** Adds a Maven artifact using coordinate string (format: "groupId:artifactId:version").
         * @param coordinates Maven coordinates string
         * @return this builder */
        public Builder addArtifact(String coordinates) {
            return addArtifact(MavenArtifact.parse(coordinates));
        }

        /** Sets authentication for the repository.
         * @param authConfig Authentication configuration (null = no auth)
         * @return this builder */
        public Builder auth(AuthConfig authConfig) {
            this.authConfig = authConfig;
            return this;
        }

        /** Sets the connection timeout for repository requests.
         * @param timeoutMs Timeout in milliseconds (default: 10000ms, 0 = infinite)
         * @return this builder */
        public Builder connectTimeout(int timeoutMs) {
            if (timeoutMs < 0) {
                throw new IllegalArgumentException("connectTimeout must be >= 0");
            }
            this.connectTimeout = timeoutMs;
            return this;
        }

        /** Sets the read timeout for downloading JARs.
         * @param timeoutMs Timeout in milliseconds (default: 30000ms, 0 = infinite)
         * @return this builder */
        public Builder readTimeout(int timeoutMs) {
            if (timeoutMs < 0) {
                throw new IllegalArgumentException("readTimeout must be >= 0");
            }
            this.readTimeout = timeoutMs;
            return this;
        }

        /** Builds the MavenRepositoryClassSource with configured settings.
         * @return A new MavenRepositoryClassSource instance */
        public MavenRepositoryClassSource build() {
            Objects.requireNonNull(repositoryUrl, "repositoryUrl must be set");
            return new MavenRepositoryClassSource(repositoryUrl, artifacts, authConfig,
                                                 connectTimeout, readTimeout);
        }
    }

    /** Maven Central repository URL. */
    public static final String MAVEN_CENTRAL = "https://repo1.maven.org/maven2/";

    /**
     * JCenter repository URL.
     * @deprecated JCenter is deprecated and read-only since February 2021.
     */
    public static final String JCENTER = "https://jcenter.bintray.com/";

    /** Google Maven repository URL. */
    public static final String GOOGLE = "https://maven.google.com/";
}
