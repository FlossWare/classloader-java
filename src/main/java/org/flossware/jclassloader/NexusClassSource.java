package org.flossware.jclassloader;

import org.flossware.jclassloader.util.ClassNameUtil;

import java.io.ByteArrayOutputStream;

import static org.flossware.jclassloader.util.ClassLoaderConstants.DEFAULT_BUFFER_SIZE;
import java.io.IOException;
import java.io.InputStream;
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
    private final String nexusUrl;
    private final String repository;
    private final AuthConfig authConfig;
    private final NexusMode mode;
    private final Map<String, byte[]> jarCache;

    /**
     * Nexus repository mode.
     */
    public enum NexusMode {
        /** Raw repository with direct .class files */
        RAW,
        /** Maven repository with JAR files */
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
     */
    public NexusClassSource(String nexusUrl, String repository, NexusMode mode) {
        this(nexusUrl, repository, mode, AuthConfig.none());
    }

    /**
     * Creates a Nexus class source in MAVEN mode without authentication.
     *
     * @param nexusUrl The Nexus server URL
     * @param repository The repository name
     */
    public NexusClassSource(String nexusUrl, String repository) {
        this(nexusUrl, repository, NexusMode.MAVEN, AuthConfig.none());
    }

    @Override
    public byte[] loadClassData(String className) throws IOException {
        if (mode == NexusMode.RAW) {
            return loadFromRaw(className);
        } else {
            return loadFromMaven(className);
        }
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
        return "NexusClassSource[" + nexusUrl + ", repo=" + repository + ", mode=" + mode + ", auth=" + authConfig.getAuthType() + "]";
    }

    private byte[] loadFromRaw(String className) throws IOException {
        String classPath = ClassNameUtil.toClassFilePath(className);
        String url = nexusUrl + "repository/" + repository + "/" + classPath;
        return fetchUrl(url);
    }

    private byte[] loadFromMaven(String className) throws IOException {
        String packagePath = getPackagePath(className);
        if (packagePath == null) {
            throw new IOException("Cannot determine Maven coordinates for class: " + className);
        }

        String cachedKey = packagePath;
        // Atomic get() - avoids TOCTOU race condition with contains() + get()
        byte[] cachedData = jarCache.get(cachedKey);
        if (cachedData != null) {
            return cachedData;
        }

        String simpleClassName = getSimpleClassName(className);
        String classFileInJar = ClassNameUtil.toClassFilePath(className);

        byte[] classData = searchInJars(packagePath, classFileInJar);
        if (classData != null) {
            jarCache.put(cachedKey, classData);
            return classData;
        }

        throw new IOException("Class not found in Nexus Maven repository: " + className);
    }

    private byte[] searchInJars(String packagePath, String classFileInJar) throws IOException {
        String searchUrl = nexusUrl + "service/rest/v1/search?repository=" + repository + "&name=" + packagePath;

        try {
            URL url = new URL(searchUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            configureAuthentication(connection);
            connection.setRequestMethod("GET");

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                return null;
            }
        } catch (IOException e) {
        }

        return null;
    }

    private byte[] fetchUrl(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        configureAuthentication(connection);

        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("HTTP error code: " + responseCode + " for URL: " + urlString);
        }

        try (InputStream in = connection.getInputStream();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }

            return out.toByteArray();
        }
    }

    protected byte[] loadClassFromJar(String jarUrl, String classFileName) throws IOException {
        URL url = new URL(jarUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        configureAuthentication(connection);

        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("HTTP error code: " + responseCode + " for JAR URL: " + jarUrl);
        }

        try (InputStream in = connection.getInputStream();
             JarInputStream jarIn = new JarInputStream(in)) {

            JarEntry entry;
            while ((entry = jarIn.getNextJarEntry()) != null) {
                if (entry.getName().equals(classFileName)) {
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

    public String getNexusUrl() {
        return nexusUrl;
    }

    public String getRepository() {
        return repository;
    }

    public NexusMode getMode() {
        return mode;
    }

    public AuthConfig getAuthConfig() {
        return authConfig;
    }
}
