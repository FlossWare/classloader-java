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
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.jar.JarEntry;
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
    private static final long MAX_JAR_SIZE = 100 * 1024 * 1024; // 100MB default max JAR size
    private static final long MAX_CLASS_SIZE = 10 * 1024 * 1024; // 10MB default max class size
    private static final int DEFAULT_CONNECT_TIMEOUT = 10000; // 10 seconds
    private static final int DEFAULT_READ_TIMEOUT = 30000; // 30 seconds

    private final String nexusUrl;
    private final String repository;
    private final List<MavenArtifact> artifacts;
    private final AuthConfig authConfig;
    private final Map<String, byte[]> classCache;
    private final ConcurrentHashMap<String, FutureTask<JarFile>> jarFileCache;
    private final ConcurrentHashMap<String, Path> jarPathCache;
    private final int connectTimeout;
    private final int readTimeout;
    private volatile boolean closed = false;

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
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
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
        if (closed) {
            throw new IllegalStateException("MavenNexusClassSource is closed");
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
        return "MavenNexusClassSource[" + nexusUrl + ", repo=" + repository +
               ", artifacts=" + artifacts.size() + ", auth=" + authConfig.getAuthType() + "]";
    }

    private String buildJarUrl(MavenArtifact artifact) {
        return nexusUrl + "repository/" + repository + "/" + artifact.toPath();
    }

    /**
     * Ensures a JAR file is cached for the given artifact.
     * Downloads the JAR once and reuses it for subsequent class extractions.
     *
     * <p>Uses a FutureTask-based per-artifact locking pattern so that downloads of different
     * artifacts proceed fully in parallel (no shared lock or ConcurrentHashMap bin-lock held
     * during I/O), while duplicate downloads of the same artifact are coalesced -- only the
     * first thread downloads, and subsequent threads wait on the same Future.</p>
     *
     * @param artifactKey The artifact identifier
     * @param jarUrl The URL to download the JAR from
     * @return A JarFile instance opened on the cached JAR
     * @throws IOException if download or JAR opening fails
     */
    private JarFile ensureJarCached(String artifactKey, String jarUrl) throws IOException {
        // Create a FutureTask that encapsulates the download work
        FutureTask<JarFile> newTask = new FutureTask<>(new Callable<JarFile>() {
            @Override
            public JarFile call() throws IOException {
                Path tempJarPath = Files.createTempFile("jclassloader-nexus-", ".jar");
                // Register temp file immediately so it can be cleaned up on cancellation or close()
                jarPathCache.put(artifactKey, tempJarPath);
                try {
                    downloadJarFile(jarUrl, tempJarPath);
                    JarFile jarFile = new JarFile(tempJarPath.toFile());
                    return jarFile;
                } catch (IOException e) {
                    // Clean up temp file on failure
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

        // Atomically insert the task -- only the winner actually runs the download.
        // putIfAbsent returns null if this thread won, or the existing task otherwise.
        FutureTask<JarFile> existingTask = jarFileCache.putIfAbsent(artifactKey, newTask);
        if (existingTask == null) {
            // This thread won the race -- run the download (outside any CHM lock)
            newTask.run();
            existingTask = newTask;
        }

        try {
            return existingTask.get();
        } catch (ExecutionException e) {
            // Download failed -- remove the poisoned future so retries can try again
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
            // Clean up temp file if it was created before cancellation
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

    private void downloadJarFile(String jarUrl, Path tempJarPath) throws IOException {
        URL url = new URL(jarUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        try {
            connection.setConnectTimeout(connectTimeout);
            connection.setReadTimeout(readTimeout);
            configureAuthentication(connection);
            connection.setRequestMethod("GET");

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + responseCode + " for JAR: " + jarUrl);
            }

            // Check JAR size before downloading
            long contentLength = connection.getContentLengthLong();
            if (contentLength > MAX_JAR_SIZE) {
                throw new IOException(
                    "JAR too large: " + contentLength + " bytes (max " + MAX_JAR_SIZE + ")"
                );
            }

            try (InputStream in = connection.getInputStream()) {
                Files.copy(in, tempJarPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            safelyDisconnect(connection);
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
                throw new IOException("Incomplete read: expected " + size + " bytes, but got " + totalRead);
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

    private void configureAuthentication(HttpURLConnection connection) {
        AuthHelper.configureAuth(connection, authConfig);
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

        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;

            List<IOException> exceptions = new ArrayList<>();

            // Close all cached JAR files
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
                IOException ex = new IOException("Failed to close MavenNexusClassSource");
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

        /**
         * Sets the Nexus server URL.
         *
         * @param nexusUrl The Nexus URL (e.g., "https://nexus.example.com")
         * @return this builder
         * @throws NullPointerException if nexusUrl is null
         */
        public Builder nexusUrl(String nexusUrl) {
            this.nexusUrl = Objects.requireNonNull(nexusUrl, "nexusUrl cannot be null");
            return this;
        }

        /**
         * Sets the Maven repository name in Nexus.
         *
         * @param repository The repository name (e.g., "maven-releases")
         * @return this builder
         * @throws NullPointerException if repository is null
         */
        public Builder repository(String repository) {
            this.repository = Objects.requireNonNull(repository, "repository cannot be null");
            return this;
        }

        /**
         * Adds a Maven artifact to load classes from.
         *
         * @param artifact The Maven artifact descriptor
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
         * @param groupId The Maven group ID
         * @param artifactId The Maven artifact ID
         * @param version The artifact version
         * @return this builder
         */
        public Builder addArtifact(String groupId, String artifactId, String version) {
            return addArtifact(new MavenArtifact(groupId, artifactId, version));
        }

        /**
         * Adds a Maven artifact by coordinate string.
         * Format: "groupId:artifactId:version"
         *
         * @param coordinates Maven coordinates string
         * @return this builder
         */
        public Builder addArtifact(String coordinates) {
            return addArtifact(MavenArtifact.parse(coordinates));
        }

        /**
         * Sets the authentication configuration for accessing Nexus.
         *
         * @param authConfig Authentication configuration (null for no auth)
         * @return this builder
         */
        public Builder auth(AuthConfig authConfig) {
            this.authConfig = authConfig;
            return this;
        }

        /**
         * Sets the connection timeout for Nexus requests.
         *
         * @param timeoutMs Timeout in milliseconds (default: 10000ms, 0 = infinite)
         * @return this builder
         * @throws IllegalArgumentException if timeoutMs is negative
         */
        public Builder connectTimeout(int timeoutMs) {
            if (timeoutMs < 0) {
                throw new IllegalArgumentException("connectTimeout must be >= 0");
            }
            this.connectTimeout = timeoutMs;
            return this;
        }

        /**
         * Sets the read timeout for downloading JARs from Nexus.
         *
         * @param timeoutMs Timeout in milliseconds (default: 30000ms, 0 = infinite)
         * @return this builder
         * @throws IllegalArgumentException if timeoutMs is negative
         */
        public Builder readTimeout(int timeoutMs) {
            if (timeoutMs < 0) {
                throw new IllegalArgumentException("readTimeout must be >= 0");
            }
            this.readTimeout = timeoutMs;
            return this;
        }

        /**
         * Builds the MavenNexusClassSource with configured settings.
         *
         * @return a new MavenNexusClassSource instance
         * @throws NullPointerException if nexusUrl or repository is not set
         */
        public MavenNexusClassSource build() {
            Objects.requireNonNull(nexusUrl, "nexusUrl must be set");
            Objects.requireNonNull(repository, "repository must be set");
            return new MavenNexusClassSource(nexusUrl, repository, artifacts, authConfig,
                                            connectTimeout, readTimeout);
        }
    }
}
