package org.flossware.classloader;

import org.flossware.classloader.util.ClassNameUtil;

import java.io.ByteArrayOutputStream;

import static org.flossware.classloader.util.ClassLoaderConstants.DEFAULT_BUFFER_SIZE;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

/**
 * ClassSource implementation for loading classes from Maven repositories (Maven Central, custom repos).
 * Downloads JARs from the repository and extracts class files from them.
 * Includes in-memory caching of loaded classes for performance.
 *
 * <p><b>Thread Safety:</b> This class is thread-safe. The internal class cache uses
 * ConcurrentHashMap to support concurrent class loading operations.</p>
 */
public class MavenRepositoryClassSource implements ClassSource {
    private static final long MAX_JAR_SIZE = 100 * 1024 * 1024; // 100MB default max JAR size
    private static final long MAX_CLASS_SIZE = 10 * 1024 * 1024; // 10MB default max class size
    private static final int DEFAULT_CONNECT_TIMEOUT = 10000; // 10 seconds
    private static final int DEFAULT_READ_TIMEOUT = 30000; // 30 seconds

    private final String repositoryUrl;
    private final List<MavenArtifact> artifacts;
    private final AuthConfig authConfig;
    private final Map<String, byte[]> classCache;
    private final int connectTimeout;
    private final int readTimeout;

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

    @Override
    public byte[] loadClassData(String className) throws IOException {
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
                byte[] classData = extractClassFromJar(jarUrl, classFileName);
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

    @Override
    public boolean canLoad(String className) {
        try {
            loadClassData(className);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public String getDescription() {
        return "MavenRepositoryClassSource[" + repositoryUrl + ", artifacts=" +
               artifacts.size() + ", auth=" + authConfig.getAuthType() + "]";
    }

    private String buildJarUrl(MavenArtifact artifact) {
        return repositoryUrl + artifact.toPath();
    }

    private byte[] extractClassFromJar(String jarUrl, String classFileName) throws IOException {
        URL url = new URL(jarUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(connectTimeout);
        connection.setReadTimeout(readTimeout);
        AuthHelper.configureAuth(connection, authConfig);
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

        try (InputStream in = connection.getInputStream();
             JarInputStream jarIn = new JarInputStream(in)) {

            JarEntry entry;
            while ((entry = jarIn.getNextJarEntry()) != null) {
                if (entry.getName().equals(classFileName) && !entry.isDirectory()) {
                    long size = entry.getSize();

                    if (size > MAX_CLASS_SIZE) {
                        throw new IOException(
                            "Class too large: " + size + " bytes (max " + MAX_CLASS_SIZE + ")"
                        );
                    }

                    // Read with size enforcement
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
                    long totalRead = 0;
                    int bytesRead;

                    while ((bytesRead = jarIn.read(buffer)) != -1) {
                        totalRead += bytesRead;
                        if (totalRead > MAX_CLASS_SIZE) {
                            throw new IOException("Class exceeded size limit: " + totalRead);
                        }
                        out.write(buffer, 0, bytesRead);
                    }

                    return out.toByteArray();
                }
            }
        }

        throw new IOException("Class not found in JAR: " + classFileName + " (URL: " + jarUrl + ")");
    }

    public void addArtifact(MavenArtifact artifact) {
        Objects.requireNonNull(artifact, "artifact cannot be null");
        artifacts.add(artifact);
    }

    public void addArtifact(String coordinates) {
        addArtifact(MavenArtifact.parse(coordinates));
    }

    public List<MavenArtifact> getArtifacts() {
        return new ArrayList<>(artifacts);
    }

    public String getRepositoryUrl() {
        return repositoryUrl;
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

    public static final String MAVEN_CENTRAL = "https://repo1.maven.org/maven2/";
    public static final String JCENTER = "https://jcenter.bintray.com/";
    public static final String GOOGLE = "https://maven.google.com/";
}
