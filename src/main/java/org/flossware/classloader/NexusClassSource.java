package org.flossware.classloader;

import org.flossware.classloader.util.ClassNameUtil;

import java.io.ByteArrayOutputStream;

import static org.flossware.classloader.util.ClassLoaderConstants.DEFAULT_BUFFER_SIZE;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

/**
 * ClassSource implementation for loading classes from Sonatype Nexus repositories.
 * Supports both RAW repositories (direct .class files) and MAVEN repositories (classes from JARs).
 *
 * <p><b>Thread Safety:</b> This class is thread-safe. The internal JAR cache uses
 * ConcurrentHashMap to support concurrent class loading operations.</p>
 */
public class NexusClassSource implements ClassSource {
    private static final long MAX_CLASS_SIZE = 10 * 1024 * 1024; // 10MB default max class size

    private final String nexusUrl;
    private final String repository;
    private final AuthConfig authConfig;
    private final NexusMode mode;
    private final Map<String, byte[]> jarCache;

    /**
     * Nexus repository mode.
     *
     * <p><b>WARNING:</b> MAVEN mode is currently non-functional due to incomplete
     * implementation of searchInJars(). Use MavenNexusClassSource instead for loading
     * classes from Maven artifacts in Nexus, or use RAW mode for direct .class files.</p>
     */
    public enum NexusMode {
        /** Raw repository with direct .class files */
        RAW,
        /**
         * Maven repository with JAR files.
         * @deprecated MAVEN mode is non-functional. Use {@link MavenNexusClassSource} instead.
         */
        @Deprecated
        MAVEN
    }

    /**
     * Creates a Nexus class source with full configuration.
     *
     * @param nexusUrl The Nexus server URL (e.g., "https://nexus.example.com")
     * @param repository The repository name
     * @param mode The repository mode (RAW or MAVEN)
     * @param authConfig The authentication configuration
     * @throws NullPointerException if nexusUrl or repository is null
     */
    public NexusClassSource(String nexusUrl, String repository, NexusMode mode, AuthConfig authConfig) {
        Objects.requireNonNull(nexusUrl, "nexusUrl cannot be null");
        this.nexusUrl = nexusUrl.endsWith("/") ? nexusUrl : nexusUrl + "/";
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
        this.mode = mode != null ? mode : NexusMode.MAVEN;
        this.authConfig = authConfig != null ? authConfig : AuthConfig.none();
        this.jarCache = new ConcurrentHashMap<>();
    }

    /**
     * Creates a Nexus class source without authentication.
     *
     * @param nexusUrl The Nexus server URL
     * @param repository The repository name
     * @param mode The repository mode
     * @throws NullPointerException if nexusUrl or repository is null
     */
    public NexusClassSource(String nexusUrl, String repository, NexusMode mode) {
        this(nexusUrl, repository, mode, AuthConfig.none());
    }

    /**
     * Creates a Nexus class source in MAVEN mode without authentication.
     *
     * @param nexusUrl The Nexus server URL
     * @param repository The repository name
     * @throws NullPointerException if nexusUrl or repository is null
     */
    public NexusClassSource(String nexusUrl, String repository) {
        this(nexusUrl, repository, NexusMode.MAVEN, AuthConfig.none());
    }

    /**
     * {@inheritDoc}
     *
     * <p>Loads class data from Nexus using the configured mode (RAW or MAVEN).</p>
     */
    @Override
    public byte[] loadClassData(String className) throws IOException {
        Objects.requireNonNull(className, "className cannot be null");
        if (mode == NexusMode.RAW) {
            return loadFromRaw(className);
        } else {
            return loadFromMaven(className);
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
        return "NexusClassSource[" + nexusUrl + ", repo=" + repository + ", mode=" + mode + ", auth=" + authConfig.getAuthType() + "]";
    }

    private byte[] loadFromRaw(String className) throws IOException {
        String classPath = ClassNameUtil.toClassFilePath(className);
        String url = nexusUrl + "repository/" + repository + "/" + classPath;
        return fetchUrl(url);
    }

    /**
     * @deprecated MAVEN mode is non-functional. searchInJars() is broken.
     *             Use {@link MavenNexusClassSource} instead.
     */
    @Deprecated
    private byte[] loadFromMaven(String className) throws IOException {
        throw new UnsupportedOperationException(
            "MAVEN mode is non-functional (searchInJars() not implemented). " +
            "Use MavenNexusClassSource instead for loading classes from Maven artifacts in Nexus."
        );
    }

    /**
     * @deprecated This method is non-functional (returns null always).
     *             Proper implementation would require JSON parsing of Nexus search API response.
     */
    @Deprecated
    @SuppressWarnings("unused")
    private byte[] searchInJars(String packagePath, String classFileInJar) throws IOException {
        // This method is completely broken:
        // 1. Returns null when HTTP 200 OK (backwards logic!)
        // 2. Swallows all exceptions
        // 3. Never actually parses the response or downloads JARs
        // 4. Always returns null
        //
        // Proper implementation would require:
        // - Parse Nexus search API JSON response
        // - Extract JAR download URLs
        // - Download each JAR and search for the class
        // - Return class bytecode
        //
        // Use MavenNexusClassSource instead - it's a complete implementation.
        throw new UnsupportedOperationException(
            "searchInJars() is not implemented. Use MavenNexusClassSource instead."
        );
    }

    /**
     * Fetches a URL and returns its contents as a byte array.
     *
     * <p><b>Exception Handling:</b> RuntimeException suppression in the finally block is
     * appropriate here because disconnect() is a cleanup operation in a finally block.
     * Suppressing cleanup exceptions prevents them from masking the original IOException
     * thrown during the download attempt.</p>
     *
     * @param urlString the URL to fetch
     * @return the response body as bytes
     * @throws IOException if the HTTP request fails or the response is too large
     */
    private byte[] fetchUrl(String urlString) throws IOException {
        URL url = new URL(urlString);
        // HttpURLConnection does not implement AutoCloseable, so we use try/finally with disconnect()
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        try {
            configureAuthentication(connection);
            validateHttpResponse(connection, urlString);
            return readClassDataWithSizeLimit(connection.getInputStream(), urlString);
        } finally {
            safelyDisconnect(connection);
        }
    }

    private void validateHttpResponse(HttpURLConnection connection, String urlString) throws IOException {
        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("HTTP error code: " + responseCode + " for URL: " + urlString);
        }
    }

    private byte[] readClassDataWithSizeLimit(InputStream inputStream, String sourceIdentifier) throws IOException {
        try (InputStream in = inputStream;
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
            int bytesRead;
            long totalBytes = 0;

            while ((bytesRead = in.read(buffer)) != -1) {
                totalBytes += bytesRead;
                validateClassDataSize(totalBytes, sourceIdentifier);
                out.write(buffer, 0, bytesRead);
            }

            return out.toByteArray();
        }
    }

    private void validateClassDataSize(long totalBytes, String sourceIdentifier) throws IOException {
        if (totalBytes > MAX_CLASS_SIZE) {
            throw new IOException("Class file too large: " + totalBytes +
                                " bytes (max: " + MAX_CLASS_SIZE + " bytes) for " + sourceIdentifier);
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
     * Downloads a JAR from the given URL and extracts the specified class file entry.
     *
     * <p>Opens an HTTP connection to {@code jarUrl}, streams the response into a
     * {@link java.util.jar.JarInputStream}, and searches for an entry whose name
     * matches {@code classFileName}. Returns the raw bytecode of the matching
     * entry, or throws an {@link IOException} if the entry is not found or the
     * download fails.</p>
     *
     * @param jarUrl        the URL of the JAR file to download
     * @param classFileName the JAR entry name of the class file (e.g.
     *                      {@code "com/example/MyClass.class"})
     * @return the class bytecode extracted from the JAR
     * @throws IOException if the download fails, the HTTP response is not 200,
     *                     or the class entry is not found in the JAR
     */
    protected byte[] loadClassFromJar(String jarUrl, String classFileName) throws IOException {
        Objects.requireNonNull(jarUrl, "jarUrl cannot be null");
        Objects.requireNonNull(classFileName, "classFileName cannot be null");
        URL url = new URL(jarUrl);
        // HttpURLConnection does not implement AutoCloseable, so we use try/finally with disconnect()
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        try {
            configureAuthentication(connection);
            validateHttpResponse(connection, jarUrl);
            return extractClassFromJarStream(connection.getInputStream(), classFileName);
        } finally {
            safelyDisconnect(connection);
        }
    }

    private byte[] extractClassFromJarStream(InputStream inputStream, String classFileName) throws IOException {
        try (InputStream in = inputStream;
             JarInputStream jarIn = new JarInputStream(in)) {

            byte[] classData = findAndExtractClassFromJar(jarIn, classFileName);
            if (classData != null) {
                return classData;
            }
        }

        throw new IOException("Class file not found in JAR: " + classFileName);
    }

    private byte[] findAndExtractClassFromJar(JarInputStream jarIn, String classFileName) throws IOException {
        JarEntry entry;
        while ((entry = jarIn.getNextJarEntry()) != null) {
            if (entry.getName().equals(classFileName)) {
                return extractClassDataFromJarEntry(jarIn);
            }
        }
        return null;
    }

    private byte[] extractClassDataFromJarEntry(JarInputStream jarIn) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
            int bytesRead;
            long totalBytes = 0;

            while ((bytesRead = jarIn.read(buffer)) != -1) {
                totalBytes += bytesRead;
                if (totalBytes > MAX_CLASS_SIZE) {
                    throw new IOException("Class file too large: " + totalBytes +
                                        " bytes (max: " + MAX_CLASS_SIZE + " bytes)");
                }
                out.write(buffer, 0, bytesRead);
            }
            return out.toByteArray();
        }
    }

    private void configureAuthentication(HttpURLConnection connection) {
        AuthHelper.configureAuth(connection, authConfig);
    }

    private String getPackagePath(String className) {
        int lastDot = className.lastIndexOf('.');
        if (lastDot > 0) {
            return className.substring(0, lastDot).replace('.', '/');
        }
        return null;
    }

    private String getSimpleClassName(String className) {
        int lastDot = className.lastIndexOf('.');
        return lastDot > 0 ? className.substring(lastDot + 1) : className;
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
     * Gets the repository name.
     *
     * @return the repository name
     */
    public String getRepository() {
        return repository;
    }

    /**
     * Gets the repository mode (RAW or MAVEN).
     *
     * @return the Nexus repository mode
     */
    public NexusMode getMode() {
        return mode;
    }

    /**
     * Gets the authentication configuration.
     *
     * @return the authentication configuration
     */
    public AuthConfig getAuthConfig() {
        return authConfig;
    }
}
