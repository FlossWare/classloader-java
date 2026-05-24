package org.flossware.jclassloader;

import org.flossware.jclassloader.util.ClassNameUtil;

import java.io.ByteArrayOutputStream;

import static org.flossware.jclassloader.util.ClassLoaderConstants.DEFAULT_BUFFER_SIZE;
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

    @Override
    public byte[] loadClassData(String className) throws IOException {
        return retryPolicy.execute(() -> {
            String classPath = ClassNameUtil.toClassFilePath(className);
            URL url = new URL(baseUrl + classPath);

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
        });
    }

    @Override
    public boolean canLoad(String className) {
        HttpURLConnection httpConnection = null;
        try {
            String classPath = ClassNameUtil.toClassFilePath(className);
            URL url = new URL(baseUrl + classPath);
            URLConnection connection = url.openConnection();
            connection.setConnectTimeout(connectTimeoutMs);
            connection.setReadTimeout(readTimeoutMs);

            if (connection instanceof HttpURLConnection) {
                httpConnection = (HttpURLConnection) connection;
                httpConnection.setRequestMethod("HEAD");
                configureSSL(httpConnection);
                configureAuthentication(httpConnection);

                int responseCode = httpConnection.getResponseCode();
                return responseCode == HttpURLConnection.HTTP_OK;
            }

            connection.connect();
            return true;
        } catch (IOException e) {
            return false;
        } finally {
            if (httpConnection != null) {
                httpConnection.disconnect();
            }
        }
    }

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
