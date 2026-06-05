package org.flossware.classloader;

import org.flossware.classloader.util.ClassNameUtil;

import java.io.ByteArrayOutputStream;

import static org.flossware.classloader.util.ClassLoaderConstants.DEFAULT_BUFFER_SIZE;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
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
 * <p><b>Thread Safety:</b> This class is thread-safe. The internal caches use ConcurrentHashMap
 * to support concurrent class loading operations.</p>
 */
public class MavenRepositoryClassSource implements ClassSource, AutoCloseable {
    private static final long MAX_JAR_SIZE = 100 * 1024 * 1024; // 100MB default max JAR size
    private static final long MAX_CLASS_SIZE = 10 * 1024 * 1024; // 10MB default max class size
    private static final int DEFAULT_CONNECT_TIMEOUT = 10000; // 10 seconds
    private static final int DEFAULT_READ_TIMEOUT = 30000; // 30 seconds

    private final String repositoryUrl;
    private final List<MavenArtifact> artifacts;
    private final AuthConfig authConfig;
    private final Map<String, byte[]> classCache;
    private final Map<String, JarFile> jarFileCache;
    private final Map<String, Path> jarPathCache;
    private final int connectTimeout;
    private final int readTimeout;
    private volatile boolean closed = false;
    private final Map<String, Object> perArtifactLocks = new ConcurrentHashMap<>();

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
        this.artifacts = new ArrayList<>(artifacts);
        this.authConfig = authConfig != null ? authConfig : AuthConfig.none();
        this.classCache = new ConcurrentHashMap<>();
        this.jarFileCache = new ConcurrentHashMap<>();
        this.jarPathCache = new ConcurrentHashMap<>();
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
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
     *
     * <p><b>Thread Safety:</b> Uses fine-grained per-artifact synchronization to allow
     * concurrent downloads of different artifacts while maintaining cache consistency.</p>
     */
    @Override
    public byte[] loadClassData(String className) throws IOException {
        // Check closed flag without synchronized block (volatile for visibility)
        if (closed) {
            throw new IllegalStateException("MavenRepositoryClassSource is closed");
        }

        Objects.requireNonNull(className, "className cannot be null");
        String cacheKey = className;
        // Atomic get() - avoids TOCTOU race condition with contains() + get()
        byte[] cachedData = classCache.get(cacheKey);
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
                byte[] classData = extractClassFromCachedJar(jarFile, classFileName, jarUrl);
                classCache.put(cacheKey, classData);
                return classData;
            } catch (IOException e) {
                // Accumulate errors instead of silently swallowing
                String errorMsg = String.format("Artifact %s - %s",
                    artifact.toString(), e.getMessage());
                errorMessages.add(errorMsg);
            }
        }

        // Throw with ALL error details
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
     * Downloads the JAR once and reuses it for subsequent class extractions.
     * Uses per-artifact synchronization to allow concurrent downloads of different artifacts.
     *
     * @param artifactKey The artifact identifier
     * @param jarUrl The URL to download the JAR from
     * @return A JarFile instance opened on the cached JAR
     * @throws IOException if download or JAR opening fails
     */
    private JarFile ensureJarCached(String artifactKey, String jarUrl) throws IOException {
        JarFile existing = jarFileCache.get(artifactKey);
        if (existing != null) {
            return existing;
        }

        // Get or create per-artifact lock for fine-grained synchronization
        Object artifactLock = perArtifactLocks.computeIfAbsent(artifactKey, k -> new Object());

        synchronized (artifactLock) {
            // Double-check pattern: another thread may have cached it while waiting for lock
            existing = jarFileCache.get(artifactKey);
            if (existing != null) {
                return existing;
            }

            // Download JAR to temp file
            Path tempJarPath = Files.createTempFile("jclassloader-maven-", ".jar");
            try {
                downloadJarFile(jarUrl, tempJarPath);
                JarFile jarFile = new JarFile(tempJarPath.toFile());
                jarFileCache.put(artifactKey, jarFile);
                jarPathCache.put(artifactKey, tempJarPath);
                return jarFile;
            } catch (IOException e) {
                // Clean up temp file on failure
                try {
                    Files.deleteIfExists(tempJarPath);
                } catch (IOException ignored) {
                    // Ignore cleanup errors
                }
                throw e;
            }
        }
    }

    private void downloadJarFile(String jarUrl, Path tempJarPath) throws IOException {
        URL url = new URL(jarUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        try {
            connection.setConnectTimeout(connectTimeout);
            connection.setReadTimeout(readTimeout);
            AuthHelper.configureAuth(connection, authConfig);
            connection.setRequestMethod("GET");

            validateJarResponse(connection, jarUrl);

            try (InputStream in = connection.getInputStream()) {
                Files.copy(in, tempJarPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            safelyDisconnect(connection);
        }
    }

    private void validateJarResponse(HttpURLConnection connection, String jarUrl) throws IOException {
        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("HTTP " + responseCode + " for JAR: " + jarUrl);
        }

        long contentLength = connection.getContentLengthLong();
        if (contentLength > MAX_JAR_SIZE) {
            throw new IOException(
                "JAR too large: " + contentLength + " bytes (max " + MAX_JAR_SIZE + ")"
            );
        }
    }

    private byte[] extractClassFromCachedJar(JarFile jarFile, String classFileName, String jarUrl) throws IOException {
        JarEntry entry = jarFile.getJarEntry(classFileName);
        if (entry == null) {
            throw new IOException("Class not found in JAR: " + classFileName + " (URL: " + jarUrl + ")");
        }

        long size = entry.getSize();
        return extractClassDataFromEntry(jarFile, entry, size);
    }

    private byte[] extractClassDataFromEntry(JarFile jarFile, JarEntry entry, long size) throws IOException {
        if (size < 0) {
            // Unknown size - read with limit
            return readWithSizeLimit(jarFile.getInputStream(entry), MAX_CLASS_SIZE);
        }

        validateClassSize(size);
        return readClassDataWithKnownSize(jarFile, entry, size);
    }

    private void validateClassSize(long size) throws IOException {
        if (size > MAX_CLASS_SIZE) {
            throw new IOException(
                "Class file too large: " + size + " bytes (max " + MAX_CLASS_SIZE + ")"
            );
        }

        if (size > Integer.MAX_VALUE) {
            throw new IOException("Class file exceeds Java array limit: " + size);
        }
    }

    private byte[] readClassDataWithKnownSize(JarFile jarFile, JarEntry entry, long size) throws IOException {
        try (InputStream in = jarFile.getInputStream(entry)) {
            byte[] data = new byte[(int)size];
            readFully(in, data, (int)size);
            return data;
        }
    }

    private void readFully(InputStream in, byte[] data, int size) throws IOException {
        int totalRead = 0;
        while (totalRead < size) {
            int n = in.read(data, totalRead, size - totalRead);
            if (n == -1) {
                return;
            }
            totalRead += n;
        }
    }

    private byte[] readWithSizeLimit(InputStream in, long maxSize) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
            long totalRead = 0;
            int bytesRead;

            while ((bytesRead = in.read(buffer)) != -1) {
                totalRead += bytesRead;
                if (totalRead > maxSize) {
                    throw new IOException("Entry exceeds maximum size: " + totalRead);
                }
                out.write(buffer, 0, bytesRead);
            }

            return out.toByteArray();
        }
    }

    private void safelyDisconnect(HttpURLConnection connection) {
        if (connection != null) {
            try {
                connection.disconnect();
            } catch (RuntimeException e) {
                // Suppress runtime exceptions during resource cleanup to avoid masking original exception
            }
        }
    }

    /**
     * Adds a Maven artifact to load classes from.
     *
     * @param artifact The Maven artifact to add
     * @throws NullPointerException if artifact is null
     */
    public void addArtifact(MavenArtifact artifact) {
        Objects.requireNonNull(artifact, "artifact cannot be null");
        artifacts.add(artifact);
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
     * @throws IOException if an error occurs during cleanup
     */
    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }

        // Use explicit lock object for close coordination with loadClassData
        synchronized (perArtifactLocks) {
            if (closed) {
                return;
            }
            closed = true;

            List<IOException> exceptions = new ArrayList<>();

            // Close all cached JAR files
            for (JarFile jarFile : jarFileCache.values()) {
                try {
                    jarFile.close();
                } catch (IOException e) {
                    exceptions.add(e);
                }
            }
            jarFileCache.clear();

            // Delete all temp JAR files
            for (Path tempPath : jarPathCache.values()) {
                try {
                    Files.deleteIfExists(tempPath);
                } catch (IOException e) {
                    exceptions.add(e);
                }
            }
            jarPathCache.clear();

            // Throw aggregated exception if any occurred
            if (!exceptions.isEmpty()) {
                IOException ex = new IOException("Failed to close MavenRepositoryClassSource");
                exceptions.forEach(ex::addSuppressed);
                throw ex;
            }
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
     * <p>Loads classes from Maven artifacts in any Maven-compatible repository
     * (Maven Central, JCenter, Google, corporate Nexus/Artifactory, etc.).</p>
     *
     * <p><b>Basic Example (Maven Central):</b></p>
     * <pre>{@code
     * MavenRepositoryClassSource source = MavenRepositoryClassSource.builder()
     *     .mavenCentral()
     *     .addArtifact("com.google.guava:guava:32.1.0-jre")
     *     .addArtifact("org.apache.commons:commons-lang3:3.12.0")
     *     .build();
     * }</pre>
     *
     * <p><b>Advanced Example (Corporate Repository with Auth):</b></p>
     * <pre>{@code
     * MavenRepositoryClassSource source = MavenRepositoryClassSource.builder()
     *     .repositoryUrl("https://nexus.example.com/repository/maven-releases/")
     *     .auth(AuthConfig.basic("user", "password"))
     *     .addArtifact("com.example", "my-lib", "1.0.0")
     *     .addArtifact("com.example:another-lib:2.0.0")
     *     .connectTimeout(15000)
     *     .readTimeout(60000)
     *     .build();
     * }</pre>
     *
     * <p><b>Multiple Repositories:</b></p>
     * <p>To load from multiple repositories, create multiple MavenRepositoryClassSource
     * instances and add them to ApplicationClassLoader:</p>
     * <pre>{@code
     * ApplicationClassLoader loader = ApplicationClassLoader.builder()
     *     .addClassSource(MavenRepositoryClassSource.builder()
     *         .mavenCentral()
     *         .addArtifact("com.google.guava:guava:32.1.0-jre")
     *         .build())
     *     .addClassSource(MavenRepositoryClassSource.builder()
     *         .repositoryUrl("https://nexus.example.com/maven-releases/")
     *         .addArtifact("com.example:my-lib:1.0.0")
     *         .build())
     *     .build();
     * }</pre>
     *
     * <p><b>Common Repositories:</b></p>
     * <ul>
     *   <li>Maven Central: https://repo1.maven.org/maven2/</li>
     *   <li>JCenter: https://jcenter.bintray.com/ (deprecated)</li>
     *   <li>Google: https://maven.google.com/</li>
     * </ul>
     *
     * <p><b>Defaults:</b></p>
     * <ul>
     *   <li>connectTimeout: 10000ms (10 seconds)</li>
     *   <li>readTimeout: 30000ms (30 seconds)</li>
     *   <li>auth: none</li>
     * </ul>
     */
    public static class Builder {
        private String repositoryUrl;
        private final List<MavenArtifact> artifacts = new ArrayList<>();
        private AuthConfig authConfig = AuthConfig.none();
        private int connectTimeout = DEFAULT_CONNECT_TIMEOUT;
        private int readTimeout = DEFAULT_READ_TIMEOUT;

        /**
         * Sets a custom Maven repository URL.
         *
         * <p>URL should end with trailing slash and point to the repository root.</p>
         *
         * <p>Examples:</p>
         * <ul>
         *   <li>Nexus: "https://nexus.example.com/repository/maven-releases/"</li>
         *   <li>Artifactory: "https://artifactory.example.com/artifactory/libs-release/"</li>
         *   <li>Local: "file:///home/user/.m2/repository/"</li>
         * </ul>
         *
         * @param repositoryUrl Maven repository URL
         * @return this builder
         * @throws NullPointerException if repositoryUrl is null
         */
        public Builder repositoryUrl(String repositoryUrl) {
            this.repositoryUrl = Objects.requireNonNull(repositoryUrl, "repositoryUrl cannot be null");
            return this;
        }

        /**
         * Uses Maven Central as the repository.
         *
         * <p>Shortcut for {@code repositoryUrl("https://repo1.maven.org/maven2/")}</p>
         *
         * @return this builder
         */
        public Builder mavenCentral() {
            this.repositoryUrl = "https://repo1.maven.org/maven2/";
            return this;
        }

        /**
         * Adds a Maven artifact to load classes from.
         *
         * @param artifact Maven artifact descriptor
         * @return this builder
         * @throws NullPointerException if artifact is null
         */
        public Builder addArtifact(MavenArtifact artifact) {
            this.artifacts.add(Objects.requireNonNull(artifact, "artifact cannot be null"));
            return this;
        }

        /**
         * Adds a Maven artifact using individual coordinates.
         *
         * @param groupId Group ID (e.g., "com.google.guava")
         * @param artifactId Artifact ID (e.g., "guava")
         * @param version Version (e.g., "32.1.0-jre")
         * @return this builder
         */
        public Builder addArtifact(String groupId, String artifactId, String version) {
            return addArtifact(new MavenArtifact(groupId, artifactId, version));
        }

        /**
         * Adds a Maven artifact using coordinate string.
         *
         * <p>Format: {@code "groupId:artifactId:version"}</p>
         *
         * <p>Examples:</p>
         * <ul>
         *   <li>"com.google.guava:guava:32.1.0-jre"</li>
         *   <li>"org.apache.commons:commons-lang3:3.12.0"</li>
         * </ul>
         *
         * @param coordinates Maven coordinates string
         * @return this builder
         */
        public Builder addArtifact(String coordinates) {
            return addArtifact(MavenArtifact.parse(coordinates));
        }

        /**
         * Sets authentication for the repository.
         *
         * <p>Required for private corporate repositories.</p>
         *
         * @param authConfig Authentication configuration (null = no auth)
         * @return this builder
         */
        public Builder auth(AuthConfig authConfig) {
            this.authConfig = authConfig;
            return this;
        }

        /**
         * Sets the connection timeout for repository requests.
         *
         * @param timeoutMs Timeout in milliseconds (default: 10000ms, 0 = infinite)
         * @return this builder
         * @throws IllegalArgumentException if timeoutMs < 0
         */
        public Builder connectTimeout(int timeoutMs) {
            if (timeoutMs < 0) {
                throw new IllegalArgumentException("connectTimeout must be >= 0");
            }
            this.connectTimeout = timeoutMs;
            return this;
        }

        /**
         * Sets the read timeout for downloading JARs.
         *
         * @param timeoutMs Timeout in milliseconds (default: 30000ms, 0 = infinite)
         * @return this builder
         * @throws IllegalArgumentException if timeoutMs < 0
         */
        public Builder readTimeout(int timeoutMs) {
            if (timeoutMs < 0) {
                throw new IllegalArgumentException("readTimeout must be >= 0");
            }
            this.readTimeout = timeoutMs;
            return this;
        }

        /**
         * Builds the MavenRepositoryClassSource with configured settings.
         *
         * @return A new MavenRepositoryClassSource instance
         * @throws NullPointerException if repositoryUrl not set
         */
        public MavenRepositoryClassSource build() {
            Objects.requireNonNull(repositoryUrl, "repositoryUrl must be set");
            return new MavenRepositoryClassSource(repositoryUrl, artifacts, authConfig,
                                                 connectTimeout, readTimeout);
        }
    }

    /**
     * Maven Central repository URL.
     * The primary public repository for open-source Maven artifacts.
     */
    public static final String MAVEN_CENTRAL = "https://repo1.maven.org/maven2/";

    /**
     * JCenter repository URL.
     * @deprecated JCenter is deprecated and read-only since February 2021.
     */
    public static final String JCENTER = "https://jcenter.bintray.com/";

    /**
     * Google Maven repository URL.
     * Hosts Android libraries and Google-maintained dependencies.
     */
    public static final String GOOGLE = "https://maven.google.com/";
}
