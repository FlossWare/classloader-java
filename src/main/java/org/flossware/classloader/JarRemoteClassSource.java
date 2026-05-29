package org.flossware.classloader;

import org.flossware.classloader.util.ClassNameUtil;
import org.slf4j.Logger;

import static org.flossware.classloader.util.ClassLoaderConstants.DEFAULT_BUFFER_SIZE;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import javax.net.ssl.HttpsURLConnection;

/**
 * ClassSource implementation for loading classes from remote JAR files.
 * Downloads the JAR file once and caches it in a temporary location.
 * Supports HTTP/HTTPS with optional authentication.
 * Implements AutoCloseable - call close() to release resources and delete temp file.
 */
public class JarRemoteClassSource implements ClassSource, AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(JarRemoteClassSource.class);
    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 10000;
    private static final int DEFAULT_READ_TIMEOUT_MS = 30000;
    private static final long MAX_JAR_SIZE = 100 * 1024 * 1024; // 100MB default
    private static final long MAX_CLASS_SIZE = 10 * 1024 * 1024; // 10MB default

    private final String jarUrl;
    private final AuthConfig authConfig;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    private final RetryPolicy retryPolicy;

    private JarFile jarFile;
    private Path tempJarPath;
    private volatile boolean closed = false;

    /**
     * Creates a JAR remote class source with authentication, timeouts, and retry policy.
     *
     * @param jarUrl The URL of the JAR file to download
     * @param authConfig The authentication configuration (null for no authentication)
     * @param connectTimeoutMs Connection timeout in milliseconds
     * @param readTimeoutMs Read timeout in milliseconds
     * @param retryPolicy The retry policy for download failures (null for default policy)
     * @throws NullPointerException if jarUrl is null
     */
    public JarRemoteClassSource(String jarUrl, AuthConfig authConfig, int connectTimeoutMs,
                               int readTimeoutMs, RetryPolicy retryPolicy) {
        Objects.requireNonNull(jarUrl, "jarUrl cannot be null");
        this.jarUrl = jarUrl;
        this.authConfig = authConfig != null ? authConfig : AuthConfig.none();
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
        this.retryPolicy = retryPolicy != null ? retryPolicy : RetryPolicy.defaultPolicy();
    }

    /**
     * Creates a JAR remote class source with authentication.
     *
     * @param jarUrl The URL of the JAR file to download
     * @param authConfig The authentication configuration
     */
    public JarRemoteClassSource(String jarUrl, AuthConfig authConfig) {
        this(jarUrl, authConfig, DEFAULT_CONNECT_TIMEOUT_MS, DEFAULT_READ_TIMEOUT_MS, null);
    }

    /**
     * Creates a JAR remote class source without authentication.
     *
     * @param jarUrl The URL of the JAR file to download
     */
    public JarRemoteClassSource(String jarUrl) {
        this(jarUrl, null, DEFAULT_CONNECT_TIMEOUT_MS, DEFAULT_READ_TIMEOUT_MS, null);
    }

    /**
     * Ensures the JAR file has been downloaded and opened.
     * Thread-safe, downloads only once.
     *
     * @throws IOException if download or JAR opening fails
     */
    private synchronized void ensureJarReady() throws IOException {
        if (closed) {
            throw new IllegalStateException("JarRemoteClassSource is closed");
        }

        if (jarFile != null) {
            return;
        }

        // Download JAR to temp file
        tempJarPath = Files.createTempFile("jclassloader-jar-", ".jar");

        retryPolicy.execute(() -> {
            URL url = new URL(jarUrl);
            URLConnection connection = url.openConnection();
            connection.setConnectTimeout(connectTimeoutMs);
            connection.setReadTimeout(readTimeoutMs);

            if (connection instanceof HttpURLConnection) {
                HttpURLConnection httpConnection = (HttpURLConnection) connection;
                configureSSL(httpConnection);
                configureAuthentication(httpConnection);

                int responseCode = httpConnection.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw new IOException("HTTP error code: " + responseCode + " for URL: " + url);
                }

                // Check JAR size before downloading
                long contentLength = httpConnection.getContentLengthLong();
                if (contentLength > MAX_JAR_SIZE) {
                    throw new IOException(
                        "JAR file too large: " + contentLength + " bytes (max " + MAX_JAR_SIZE + ")"
                    );
                }
            }

            try (InputStream in = connection.getInputStream()) {
                Files.copy(in, tempJarPath, StandardCopyOption.REPLACE_EXISTING);
            }

            // Validate downloaded file size
            long actualSize = Files.size(tempJarPath);
            if (actualSize > MAX_JAR_SIZE) {
                Files.deleteIfExists(tempJarPath);
                throw new IOException(
                    "Downloaded JAR exceeds size limit: " + actualSize + " bytes"
                );
            }

            return null;
        });

        // Open the JAR file
        jarFile = new JarFile(tempJarPath.toFile());
    }

    @Override
    public byte[] loadClassData(String className) throws IOException {
        ensureJarReady();

        String entryName = ClassNameUtil.toClassFilePath(className);
        JarEntry entry = jarFile.getJarEntry(entryName);

        if (entry == null) {
            throw new IOException("Class not found in JAR: " + className);
        }

        long size = entry.getSize();
        if (size < 0) {
            // Unknown size - read with limit
            return readWithSizeLimit(jarFile.getInputStream(entry), MAX_CLASS_SIZE);
        }

        if (size > MAX_CLASS_SIZE) {
            throw new IOException(
                "Class file too large: " + size + " bytes (max " + MAX_CLASS_SIZE + ")"
            );
        }

        if (size > Integer.MAX_VALUE) {
            throw new IOException("Class file exceeds Java array limit: " + size);
        }

        try (InputStream in = jarFile.getInputStream(entry)) {
            byte[] data = new byte[(int)size];
            int totalRead = 0;

            while (totalRead < size) {
                int n = in.read(data, totalRead, (int)size - totalRead);
                if (n == -1) break;
                totalRead += n;
            }

            return data;
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

    /**
     * Checks if this source can load the specified class.
     *
     * <p><b>Performance Note:</b> The first call to this method (or loadClassData())
     * will download the entire JAR file. Subsequent calls use the cached JAR and are fast.
     * The download is synchronized and happens only once per instance.</p>
     *
     * @param className The fully qualified class name to check
     * @return true if the class exists in the JAR, false otherwise
     */
    @Override
    public boolean canLoad(String className) {
        try {
            ensureJarReady();
            String entryName = ClassNameUtil.toClassFilePath(className);
            return jarFile.getJarEntry(entryName) != null;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public String getDescription() {
        return "JarRemoteClassSource[" + jarUrl + ", auth=" + authConfig.getAuthType() + "]";
    }

    /**
     * Closes the JAR file and deletes the temporary file.
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

            if (jarFile != null) {
                try {
                    jarFile.close();
                } catch (IOException e) {
                    // Continue to delete temp file
                }
            }

            if (tempJarPath != null) {
                try {
                    Files.deleteIfExists(tempJarPath);
                } catch (IOException e) {
                    // Log but don't throw
                    logger.warn("Failed to delete temp file: {} - {}", tempJarPath, e.getMessage());
                }
            }
        }
    }

    private void configureSSL(HttpURLConnection connection) {
        if (connection instanceof HttpsURLConnection) {
            HttpsURLConnection httpsConnection = (HttpsURLConnection) connection;
            httpsConnection.setHostnameVerifier(HttpsURLConnection.getDefaultHostnameVerifier());
            httpsConnection.setSSLSocketFactory(HttpsURLConnection.getDefaultSSLSocketFactory());
        }
    }

    private void configureAuthentication(HttpURLConnection connection) {
        AuthHelper.configureAuth(connection, authConfig);
    }

    /**
     * Gets the JAR URL for this class source.
     *
     * @return The JAR URL
     */
    public String getJarUrl() {
        return jarUrl;
    }

    /**
     * Gets the authentication configuration for this class source.
     *
     * @return The authentication configuration
     */
    public AuthConfig getAuthConfig() {
        return authConfig;
    }

    /**
     * Gets the retry policy for this class source.
     *
     * @return The retry policy
     */
    public RetryPolicy getRetryPolicy() {
        return retryPolicy;
    }

    /**
     * Checks if this class source is closed.
     *
     * @return true if closed, false otherwise
     */
    public boolean isClosed() {
        return closed;
    }
}
