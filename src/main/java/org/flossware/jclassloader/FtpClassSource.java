package org.flossware.jclassloader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Base64;
import java.util.Objects;

/**
 * ClassSource implementation for loading classes from FTP/FTPS servers.
 * Supports both anonymous and authenticated access.
 */
public class FtpClassSource implements ClassSource {
    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 10000;
    private static final int DEFAULT_READ_TIMEOUT_MS = 30000;
    private static final int DEFAULT_BUFFER_SIZE = 8192;

    private final String baseUrl;
    private final String username;
    private final String password;

    /**
     * Creates an FTP class source with authentication.
     *
     * @param baseUrl The base FTP/FTPS URL (must start with ftp:// or ftps://)
     * @param username The username for authentication (null for anonymous)
     * @param password The password for authentication (null for anonymous)
     * @throws NullPointerException if baseUrl is null
     * @throws IllegalArgumentException if baseUrl doesn't start with ftp:// or ftps://
     */
    public FtpClassSource(String baseUrl, String username, String password) {
        Objects.requireNonNull(baseUrl, "baseUrl cannot be null");

        if (!baseUrl.startsWith("ftp://") && !baseUrl.startsWith("ftps://")) {
            throw new IllegalArgumentException("baseUrl must start with ftp:// or ftps://");
        }

        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        this.username = username;
        this.password = password;
    }

    /**
     * Creates an FTP class source for anonymous access.
     *
     * @param baseUrl The base FTP/FTPS URL
     */
    public FtpClassSource(String baseUrl) {
        this(baseUrl, null, null);
    }

    @Override
    public byte[] loadClassData(String className) throws IOException {
        String classPath = className.replace('.', '/') + ".class";
        URL url = buildUrl(classPath);

        URLConnection connection = url.openConnection();
        configureConnection(connection);

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

    @Override
    public boolean canLoad(String className) {
        try {
            String classPath = className.replace('.', '/') + ".class";
            URL url = buildUrl(classPath);
            URLConnection connection = url.openConnection();
            configureConnection(connection);

            try (InputStream in = connection.getInputStream()) {
                return in.read() != -1;
            }
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public String getDescription() {
        return "FtpClassSource[" + baseUrl + ", authenticated=" + (username != null) + "]";
    }

    private URL buildUrl(String classPath) throws IOException {
        if (username != null && password != null) {
            String protocol = baseUrl.startsWith("ftps://") ? "ftps://" : "ftp://";
            String hostAndPath = baseUrl.substring(protocol.length());
            return new URL(protocol + username + ":" + password + "@" + hostAndPath + classPath);
        } else {
            return new URL(baseUrl + classPath);
        }
    }

    private void configureConnection(URLConnection connection) {
        connection.setConnectTimeout(DEFAULT_CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(DEFAULT_READ_TIMEOUT_MS);
    }

    /**
     * Gets the base FTP URL for this class source.
     *
     * @return The base URL
     */
    public String getBaseUrl() {
        return baseUrl;
    }
}
