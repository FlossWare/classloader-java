package org.flossware.classloader;

import org.flossware.classloader.util.ClassNameUtil;

import java.io.ByteArrayOutputStream;

import static org.flossware.classloader.util.ClassLoaderConstants.DEFAULT_BUFFER_SIZE;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Objects;
import javax.net.ssl.HttpsURLConnection;

/**
 * ClassSource implementation for loading classes from remote HTTP/HTTPS servers.
 * Supports optional authentication (Basic or Bearer token), configurable timeouts, and retry logic.
 */
public class RemoteClassSource implements ClassSource {
    /** Default connection timeout in milliseconds (10 seconds) */
    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 10000;

    /** Default read timeout in milliseconds (30 seconds) */
    private static final int DEFAULT_READ_TIMEOUT_MS = 30000;

    /** Maximum class file size in bytes (100MB) - prevents OutOfMemoryError from malicious sources */
    private static final int MAX_CLASS_SIZE = 100 * 1024 * 1024;

    /** Start of the HTTP 2xx success status code range (inclusive). */
    private static final int HTTP_SUCCESS_MIN = 200;

    /** End of the HTTP 2xx success status code range (exclusive). */
    private static final int HTTP_SUCCESS_MAX = 300;

    private final String baseUrl;
    private final AuthConfig authConfig;

    /** Connection timeout in milliseconds */
    private final int connectTimeoutMs;

    /** Read timeout in milliseconds */
    private final int readTimeoutMs;

    /** Retry policy for handling transient failures */
    private final RetryPolicy retryPolicy;

    /**
     * Creates a remote class source with the specified base URL, authentication, timeouts, and retry policy.
     *
     * @param baseUrl The base URL for class files (e.g., "https://example.com/classes/")
     * @param authConfig The authentication configuration (null for no authentication)
     * @param connectTimeoutMs Connection timeout in milliseconds (0 for infinite)
     * @param readTimeoutMs Read timeout in milliseconds (0 for infinite)
     * @param retryPolicy The retry policy for handling transient failures (null for default policy)
     * @throws NullPointerException if baseUrl is null
     */
    public RemoteClassSource(String baseUrl, AuthConfig authConfig, int connectTimeoutMs, int readTimeoutMs, RetryPolicy retryPolicy) {
        Objects.requireNonNull(baseUrl, "baseUrl cannot be null");
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        this.authConfig = authConfig != null ? authConfig : AuthConfig.none();
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
        this.retryPolicy = retryPolicy != null ? retryPolicy : RetryPolicy.defaultPolicy();
    }

    /**
     * Creates a remote class source with the specified base URL, authentication, and timeouts using default retry policy.
     *
     * @param baseUrl The base URL for class files (e.g., "https://example.com/classes/")
     * @param authConfig The authentication configuration (null for no authentication)
     * @param connectTimeoutMs Connection timeout in milliseconds (0 for infinite)
     * @param readTimeoutMs Read timeout in milliseconds (0 for infinite)
     * @throws NullPointerException if baseUrl is null
     */
    public RemoteClassSource(String baseUrl, AuthConfig authConfig, int connectTimeoutMs, int readTimeoutMs) {
        this(baseUrl, authConfig, connectTimeoutMs, readTimeoutMs, null);
    }

    /**
     * Creates a remote class source with the specified base URL and authentication using default timeouts.
     *
     * @param baseUrl The base URL for class files (e.g., "https://example.com/classes/")
     * @param authConfig The authentication configuration (null for no authentication)
     * @throws NullPointerException if baseUrl is null
     */
    public RemoteClassSource(String baseUrl, AuthConfig authConfig) {
        this(baseUrl, authConfig, DEFAULT_CONNECT_TIMEOUT_MS, DEFAULT_READ_TIMEOUT_MS, null);
    }

    /**
     * Creates a remote class source with the specified base URL and no authentication.
     *
     * @param baseUrl The base URL for class files
     */
    public RemoteClassSource(String baseUrl) {
        this(baseUrl, AuthConfig.none(), DEFAULT_CONNECT_TIMEOUT_MS, DEFAULT_READ_TIMEOUT_MS, null);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Downloads the class file via HTTP/HTTPS with configurable timeouts,
     * authentication, and retry logic.</p>
     */
    @Override
    public byte[] loadClassData(String className) throws IOException {
        Objects.requireNonNull(className, "className cannot be null");
        return retryPolicy.execute(() -> {
            String classPath = ClassNameUtil.toClassFilePath(className);
            // Use URL(URL, String) constructor for proper path joining
            URL baseURL = new URL(baseUrl);
            URL url = new URL(baseURL, classPath);

            HttpURLConnection httpConnection = null;
            try {
                URLConnection connection = url.openConnection();
                connection.setConnectTimeout(connectTimeoutMs);
                connection.setReadTimeout(readTimeoutMs);

                if (connection instanceof HttpURLConnection) {
                    httpConnection = (HttpURLConnection) connection;
                    return downloadFromHttpConnection(httpConnection, connection, className, url);
                }

                return downloadFromUrlConnection(connection, className);
            } finally {
                safelyDisconnectHttpConnection(httpConnection);
            }
        });
    }

    private byte[] downloadFromHttpConnection(HttpURLConnection httpConnection, URLConnection connection, String className, URL url) throws IOException {
        configureSSL(httpConnection);
        configureAuthentication(httpConnection);
        validateHttpResponse(httpConnection, url);
        return readResponseData(connection, className);
    }

    private byte[] downloadFromUrlConnection(URLConnection connection, String className) throws IOException {
        return readResponseData(connection, className);
    }

    private void validateHttpResponse(HttpURLConnection httpConnection, URL url) throws IOException {
        int responseCode = httpConnection.getResponseCode();
        // Accept any 2xx success code
        if (responseCode < HTTP_SUCCESS_MIN || responseCode >= HTTP_SUCCESS_MAX) {
            throw new IOException("HTTP error code: " + responseCode + " for URL: " + url);
        }
    }

    private byte[] readResponseData(URLConnection connection, String className) throws IOException {
        // Use content length for initial capacity if available
        int contentLength = connection.getContentLength();
        int initialSize = (contentLength > 0 && contentLength < MAX_CLASS_SIZE) ?
                          contentLength : DEFAULT_BUFFER_SIZE;

        try (InputStream in = connection.getInputStream();
             ByteArrayOutputStream out = new ByteArrayOutputStream(initialSize)) {
            readWithSizeLimit(in, out, className);
            return out.toByteArray();
        }
    }

    private void readWithSizeLimit(InputStream in, ByteArrayOutputStream out, String className) throws IOException {
        byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
        int bytesRead;
        int totalBytes = 0;

        while ((bytesRead = in.read(buffer)) != -1) {
            totalBytes += bytesRead;
            if (totalBytes > MAX_CLASS_SIZE) {
                throw new IOException("Class file too large: " + totalBytes +
                                    " bytes (max: " + MAX_CLASS_SIZE + " bytes) for " + className);
            }
            out.write(buffer, 0, bytesRead);
        }
    }

    private void safelyDisconnectHttpConnection(HttpURLConnection httpConnection) {
        if (httpConnection != null) {
            try {
                httpConnection.disconnect();
            } catch (RuntimeException e) {
                // Suppress runtime exceptions during resource cleanup to avoid masking original exception
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Performs an HTTP HEAD request to check if the class file exists at the remote URL.</p>
     */
    @Override
    public boolean canLoad(String className) {
        Objects.requireNonNull(className, "className cannot be null");
        HttpURLConnection httpConnection = null;
        try {
            String classPath = ClassNameUtil.toClassFilePath(className);
            // Use URL(URL, String) constructor for proper path joining
            URL baseURL = new URL(baseUrl);
            URL url = new URL(baseURL, classPath);

            URLConnection connection = url.openConnection();
            connection.setConnectTimeout(connectTimeoutMs);
            connection.setReadTimeout(readTimeoutMs);

            if (connection instanceof HttpURLConnection) {
                httpConnection = (HttpURLConnection) connection;
                return checkHttpResourceExists(httpConnection);
            }

            // For non-HTTP connections, attempt to connect (rare case)
            connection.connect();
            return true;
        } catch (IOException e) {
            // Network errors, timeouts, or resource not found
            return false;
        } finally {
            // Ensure connection is properly closed
            safelyDisconnectHttpConnection(httpConnection);
        }
    }

    private boolean checkHttpResourceExists(HttpURLConnection httpConnection) throws IOException {
        httpConnection.setRequestMethod("HEAD");
        configureSSL(httpConnection);
        configureAuthentication(httpConnection);

        int responseCode = httpConnection.getResponseCode();
        // Accept any 2xx success code or 304 Not Modified
        return (responseCode >= HTTP_SUCCESS_MIN && responseCode < HTTP_SUCCESS_MAX) ||
               responseCode == HttpURLConnection.HTTP_NOT_MODIFIED;
    }

    /** {@inheritDoc} */
    @Override
    public String getDescription() {
        return "RemoteClassSource[" + baseUrl + ", auth=" + authConfig.getAuthType() + "]";
    }

    private void configureSSL(HttpURLConnection connection) {
        // Ensure SSL/TLS certificate validation for HTTPS connections
        if (connection instanceof HttpsURLConnection) {
            HttpsURLConnection httpsConnection = (HttpsURLConnection) connection;
            // Use default hostname verifier (performs proper hostname verification)
            httpsConnection.setHostnameVerifier(HttpsURLConnection.getDefaultHostnameVerifier());
            // Use default SSL socket factory (validates certificates against system trust store)
            httpsConnection.setSSLSocketFactory(HttpsURLConnection.getDefaultSSLSocketFactory());
        }
    }

    private void configureAuthentication(HttpURLConnection connection) {
        AuthHelper.configureAuth(connection, authConfig);
    }

    /**
     * Gets the base URL for this remote class source.
     *
     * @return The base URL
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * Gets the authentication configuration for this remote class source.
     *
     * @return The authentication configuration
     */
    public AuthConfig getAuthConfig() {
        return authConfig;
    }

    /**
     * Gets the retry policy for this remote class source.
     *
     * @return The retry policy
     */
    public RetryPolicy getRetryPolicy() {
        return retryPolicy;
    }
}
