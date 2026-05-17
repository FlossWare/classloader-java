package org.flossware.jclassloader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

public class NexusClassSource implements ClassSource {
    private final String nexusUrl;
    private final String repository;
    private final AuthConfig authConfig;
    private final NexusMode mode;
    private final Map<String, byte[]> jarCache;

    public enum NexusMode {
        RAW,
        MAVEN
    }

    public NexusClassSource(String nexusUrl, String repository, NexusMode mode, AuthConfig authConfig) {
        Objects.requireNonNull(nexusUrl, "nexusUrl cannot be null");
        this.nexusUrl = nexusUrl.endsWith("/") ? nexusUrl : nexusUrl + "/";
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
        this.mode = mode != null ? mode : NexusMode.MAVEN;
        this.authConfig = authConfig != null ? authConfig : AuthConfig.none();
        this.jarCache = new HashMap<>();
    }

    public NexusClassSource(String nexusUrl, String repository, NexusMode mode) {
        this(nexusUrl, repository, mode, AuthConfig.none());
    }

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
        String classPath = className.replace('.', '/') + ".class";
        String url = nexusUrl + "repository/" + repository + "/" + classPath;
        return fetchUrl(url);
    }

    private byte[] loadFromMaven(String className) throws IOException {
        String packagePath = getPackagePath(className);
        if (packagePath == null) {
            throw new IOException("Cannot determine Maven coordinates for class: " + className);
        }

        String cachedKey = packagePath;
        if (jarCache.containsKey(cachedKey)) {
            return jarCache.get(cachedKey);
        }

        String simpleClassName = getSimpleClassName(className);
        String classFileInJar = className.replace('.', '/') + ".class";

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

            byte[] buffer = new byte[8192];
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
                    byte[] buffer = new byte[8192];
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
        switch (authConfig.getAuthType()) {
            case BASIC:
                String credentials = authConfig.getUsername() + ":" + authConfig.getPassword();
                String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes());
                connection.setRequestProperty("Authorization", "Basic " + encodedCredentials);
                break;
            case BEARER:
                connection.setRequestProperty("Authorization", "Bearer " + authConfig.getToken());
                break;
            case NONE:
            default:
                break;
        }
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
