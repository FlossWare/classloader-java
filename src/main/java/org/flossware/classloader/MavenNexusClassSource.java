package org.flossware.classloader;

import org.flossware.classloader.util.ClassNameUtil;

import java.io.ByteArrayOutputStream;

import static org.flossware.jclassloader.util.ClassLoaderConstants.DEFAULT_BUFFER_SIZE;
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
    private final String nexusUrl;
    private final String repository;
    private final List<MavenArtifact> artifacts;
    private final AuthConfig authConfig;
    private final Map<String, byte[]> classCache;

    /**
     * Creates a Maven Nexus class source with full configuration.
     *
     * @param nexusUrl The Nexus server URL
     * @param repository The Maven repository name
     * @param artifacts The list of Maven artifacts to load classes from
     * @param authConfig The authentication configuration
     * @throws NullPointerException if nexusUrl, repository, or artifacts is null
     * @throws IllegalArgumentException if artifacts list is empty
     */
    public MavenNexusClassSource(String nexusUrl, String repository, List<MavenArtifact> artifacts, AuthConfig authConfig) {
        Objects.requireNonNull(nexusUrl, "nexusUrl cannot be null");
        Objects.requireNonNull(artifacts, "artifacts cannot be null");

        if (artifacts.isEmpty()) {
            throw new IllegalArgumentException("At least one Maven artifact must be specified");
        }

        this.nexusUrl = nexusUrl.endsWith("/") ? nexusUrl : nexusUrl + "/";
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
        this.artifacts = new ArrayList<>(artifacts);
        this.authConfig = authConfig != null ? authConfig : AuthConfig.none();
        this.classCache = new ConcurrentHashMap<>();
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
        String cacheKey = className;
        // Atomic get() - avoids TOCTOU race condition with contains() + get()
        byte[] cachedData = classCache.get(cacheKey);
        if (cachedData != null) {
            return cachedData;
        }

        String classFileName = ClassNameUtil.toClassFilePath(className);

        for (MavenArtifact artifact : artifacts) {
            try {
                String jarUrl = buildJarUrl(artifact);
                byte[] classData = extractClassFromJar(jarUrl, classFileName);
                classCache.put(cacheKey, classData);
                return classData;
            } catch (IOException e) {
            }
        }

        throw new IOException("Class not found in any configured Maven artifacts: " + className);
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
        return "MavenNexusClassSource[" + nexusUrl + ", repo=" + repository +
               ", artifacts=" + artifacts.size() + ", auth=" + authConfig.getAuthType() + "]";
    }

    private String buildJarUrl(MavenArtifact artifact) {
        return nexusUrl + "repository/" + repository + "/" + artifact.toPath();
    }

    private byte[] extractClassFromJar(String jarUrl, String classFileName) throws IOException {
        URL url = new URL(jarUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        configureAuthentication(connection);
        connection.setRequestMethod("GET");

        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("HTTP error code: " + responseCode + " for JAR URL: " + jarUrl);
        }

        try (InputStream in = connection.getInputStream();
             JarInputStream jarIn = new JarInputStream(in)) {

            JarEntry entry;
            while ((entry = jarIn.getNextJarEntry()) != null) {
                if (entry.getName().equals(classFileName) && !entry.isDirectory()) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
                    int bytesRead;
                    while ((bytesRead = jarIn.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                    return out.toByteArray();
                }
            }
        }

        throw new IOException("Class file not found in JAR: " + classFileName);
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

        public MavenNexusClassSource build() {
            Objects.requireNonNull(nexusUrl, "nexusUrl must be set");
            Objects.requireNonNull(repository, "repository must be set");
            return new MavenNexusClassSource(nexusUrl, repository, artifacts, authConfig);
        }
    }
}
