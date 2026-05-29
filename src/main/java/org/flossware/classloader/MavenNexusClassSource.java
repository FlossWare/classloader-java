package org.flossware.classloader;

import org.flossware.classloader.util.ClassNameUtil;

import java.io.ByteArrayOutputStream;

import static org.flossware.classloader.util.ClassLoaderConstants.DEFAULT_BUFFER_SIZE;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

/**
 * ClassSource implementation for loading classes from Maven artifacts stored in Nexus.
 * Downloads JARs from Nexus and extracts class files from them.
 * Includes in-memory caching of loaded classes for performance.
 *
 * <p><b>Thread Safety:</b> This class is thread-safe. The internal class cache uses
 * ConcurrentHashMap to support concurrent class loading operations.</p>
 */
public class MavenNexusClassSource implements ClassSource {
    private static final long MAX_JAR_SIZE = 100 * 1024 * 1024; // 100MB default max JAR size
    private static final long MAX_CLASS_SIZE = 10 * 1024 * 1024; // 10MB default max class size
    private static final int DEFAULT_CONNECT_TIMEOUT = 10000; // 10 seconds
    private static final int DEFAULT_READ_TIMEOUT = 30000; // 30 seconds

    private final String nexusUrl;
    private final String repository;
    private final List<MavenArtifact> artifacts;
    private final AuthConfig authConfig;
    private final Map<String, byte[]> classCache;
    private final int connectTimeout;
    private final int readTimeout;

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

    @Override
    public byte[] loadClassData(String className) throws IOException {
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
        Objects.requireNonNull(className, "className cannot be null");
        try {
            loadClassData(className);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public String getDescription() {
        return "MavenNexusClassSource[" + nexusUrl + ", repo=" + repository +
               ", artifacts=" + artifacts.size() + ", auth=" + authConfig.getAuthType() + "]";
    }

    private String buildJarUrl(MavenArtifact artifact) {
        return nexusUrl + "repository/" + repository + "/" + artifact.toPath();
    }

    private byte[] extractClassFromJar(String jarUrl, String classFileName) throws IOException {
        URL url = new URL(jarUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
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

    private void configureAuthentication(HttpURLConnection connection) {
        AuthHelper.configureAuth(connection, authConfig);
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

    public String getNexusUrl() {
        return nexusUrl;
    }

    public String getRepository() {
        return repository;
    }

    public AuthConfig getAuthConfig() {
        return authConfig;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String nexusUrl;
        private String repository;
        private final List<MavenArtifact> artifacts = new ArrayList<>();
        private AuthConfig authConfig = AuthConfig.none();
        private int connectTimeout = DEFAULT_CONNECT_TIMEOUT;
        private int readTimeout = DEFAULT_READ_TIMEOUT;

        public Builder nexusUrl(String nexusUrl) {
            this.nexusUrl = Objects.requireNonNull(nexusUrl, "nexusUrl cannot be null");
            return this;
        }

        public Builder repository(String repository) {
            this.repository = Objects.requireNonNull(repository, "repository cannot be null");
            return this;
        }

        public Builder addArtifact(MavenArtifact artifact) {
            this.artifacts.add(Objects.requireNonNull(artifact, "artifact cannot be null"));
            return this;
        }

        public Builder addArtifact(String groupId, String artifactId, String version) {
            return addArtifact(new MavenArtifact(groupId, artifactId, version));
        }

        public Builder addArtifact(String coordinates) {
            return addArtifact(MavenArtifact.parse(coordinates));
        }

        public Builder auth(AuthConfig authConfig) {
            this.authConfig = authConfig;
            return this;
        }

        public Builder connectTimeout(int timeoutMs) {
            if (timeoutMs < 0) {
                throw new IllegalArgumentException("connectTimeout must be >= 0");
            }
            this.connectTimeout = timeoutMs;
            return this;
        }

        public Builder readTimeout(int timeoutMs) {
            if (timeoutMs < 0) {
                throw new IllegalArgumentException("readTimeout must be >= 0");
            }
            this.readTimeout = timeoutMs;
            return this;
        }

        public MavenNexusClassSource build() {
            Objects.requireNonNull(nexusUrl, "nexusUrl must be set");
            Objects.requireNonNull(repository, "repository must be set");
            return new MavenNexusClassSource(nexusUrl, repository, artifacts, authConfig,
                                            connectTimeout, readTimeout);
        }
    }
}
