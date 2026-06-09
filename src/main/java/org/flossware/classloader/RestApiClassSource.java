package org.flossware.classloader;

import org.flossware.classloader.rest.ResponseProcessor;
import org.flossware.classloader.rest.UrlBuilder;

import java.io.ByteArrayOutputStream;

import static org.flossware.classloader.util.ClassLoaderConstants.DEFAULT_BUFFER_SIZE;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * ClassSource implementation for loading classes from custom REST APIs.
 * Provides flexible configuration for URL templates, headers, query parameters, and response formats.
 * Useful for integrating with custom class distribution services.
 */
public class RestApiClassSource implements ClassSource {
    private static final long MAX_RESPONSE_SIZE = 10 * 1024 * 1024; // 10MB default
    private static final int DEFAULT_CONNECT_TIMEOUT = 10000; // 10 seconds
    private static final int DEFAULT_READ_TIMEOUT = 30000; // 30 seconds

    private final Map<String, String> headers;
    private final AuthConfig authConfig;
    private final ResponseFormat responseFormat;
    private final int connectTimeout;
    private final int readTimeout;
    private final boolean enableCanLoadCheck;

    // Helper components for URL building and response processing
    private final UrlBuilder urlBuilder;
    private final ResponseProcessor responseProcessor;

    /**
     * Response format for the REST API.
     */
    public enum ResponseFormat {
        /** Binary class bytes directly in response body */
        BINARY,
        /** Class bytes as Base64 in a JSON field */
        BASE64_JSON_FIELD,
        /** Direct response (same as BINARY) */
        DIRECT
    }

    private RestApiClassSource(String baseUrl, String classPathTemplate,
                               Map<String, String> headers, Map<String, String> queryParams,
                               AuthConfig authConfig, ResponseFormat responseFormat,
                               int connectTimeout, int readTimeout, boolean enableCanLoadCheck) {
        Objects.requireNonNull(baseUrl, "baseUrl cannot be null");
        String normalizedBaseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        String normalizedTemplate = classPathTemplate != null ? classPathTemplate : "{package}/{class}.class";
        this.headers = new HashMap<>(headers);
        this.authConfig = authConfig != null ? authConfig : AuthConfig.none();
        this.responseFormat = responseFormat != null ? responseFormat : ResponseFormat.BINARY;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
        this.enableCanLoadCheck = enableCanLoadCheck;
        this.urlBuilder = new UrlBuilder(normalizedBaseUrl, normalizedTemplate, new HashMap<>(queryParams));
        this.responseProcessor = new ResponseProcessor();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Sends an HTTP GET request to the REST API using the configured URL template,
     * headers, query parameters, and authentication. The response is processed
     * according to the configured response format.</p>
     */
    @Override
    public byte[] loadClassData(String className) throws IOException {
        Objects.requireNonNull(className, "className cannot be null");
        String url = urlBuilder.buildUrl(className);
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        try {
            connection.setConnectTimeout(connectTimeout);
            connection.setReadTimeout(readTimeout);
            connection.setRequestMethod("GET");
            configureConnection(connection);
            return fetchAndProcessResponse(connection, url);
        } finally {
            safelyDisconnect(connection);
        }
    }

    private byte[] fetchAndProcessResponse(HttpURLConnection connection, String url) throws IOException {
        validateResponseCode(connection, url);
        validateContentLength(connection);
        return downloadResponseData(connection);
    }

    private void validateResponseCode(HttpURLConnection connection, String url) throws IOException {
        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("HTTP " + responseCode + " for URL: " + url);
        }
    }

    private void validateContentLength(HttpURLConnection connection) throws IOException {
        long contentLength = connection.getContentLengthLong();
        if (contentLength > MAX_RESPONSE_SIZE) {
            throw new IOException(
                "Response too large: " + contentLength + " bytes (max " + MAX_RESPONSE_SIZE + ")"
            );
        }
    }

    private byte[] downloadResponseData(HttpURLConnection connection) throws IOException {
        try (InputStream in = connection.getInputStream();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            readWithSizeLimit(in, out);
            ResponseProcessor.ResponseFormat format = toProcessorFormat(responseFormat);
            return responseProcessor.processResponse(out.toByteArray(), format);
        }
    }

    private void readWithSizeLimit(InputStream in, ByteArrayOutputStream out) throws IOException {
        long totalRead = 0;
        byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
        int bytesRead;

        while ((bytesRead = in.read(buffer)) != -1) {
            totalRead += bytesRead;
            if (totalRead > MAX_RESPONSE_SIZE) {
                throw new IOException(
                    "Response exceeded maximum size: " + totalRead + " bytes"
                );
            }
            out.write(buffer, 0, bytesRead);
        }
    }

    private void safelyDisconnect(HttpURLConnection connection) {
        if (connection != null) {
            try {
                connection.disconnect();
            } catch (RuntimeException e) {
                // Suppress runtime exceptions during resource cleanup
            }
        }
    }

    /**
     * Checks if this source can load the specified class.
     *
     * @param className The fully qualified class name to check
     * @return true if enableCanLoadCheck is false OR the class exists, false otherwise
     */
    @Override
    public boolean canLoad(String className) {
        Objects.requireNonNull(className, "className cannot be null");
        if (!enableCanLoadCheck) {
            return true;
        }

        HttpURLConnection connection = null;
        try {
            String url = urlBuilder.buildUrl(className);
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(connectTimeout);
            connection.setReadTimeout(readTimeout);
            connection.setRequestMethod("HEAD");
            configureConnection(connection);
            return connection.getResponseCode() == HttpURLConnection.HTTP_OK;
        } catch (IOException e) {
            return false;
        } finally {
            safelyDisconnect(connection);
        }
    }

    /** {@inheritDoc} */
    @Override
    public String getDescription() {
        return "RestApiClassSource[format=" + responseFormat + ", auth=" + authConfig.getAuthType() + "]";
    }

    private void configureConnection(HttpURLConnection connection) {
        headers.forEach(connection::setRequestProperty);
        AuthHelper.configureAuth(connection, authConfig);
    }

    private static ResponseProcessor.ResponseFormat toProcessorFormat(ResponseFormat format) {
        switch (format) {
            case BINARY: return ResponseProcessor.ResponseFormat.BINARY;
            case BASE64_JSON_FIELD: return ResponseProcessor.ResponseFormat.BASE64_JSON_FIELD;
            case DIRECT: return ResponseProcessor.ResponseFormat.DIRECT;
            default: return ResponseProcessor.ResponseFormat.BINARY;
        }
    }

    /**
     * Creates a new Builder for constructing RestApiClassSource instances.
     *
     * @return A new Builder with default configuration
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for constructing RestApiClassSource instances with fluent API.
     *
     * <p><b>Example (Binary Response):</b></p>
     * <pre>{@code
     * RestApiClassSource source = RestApiClassSource.builder()
     *     .baseUrl("https://classes.example.com/api/v1")
     *     .classPathTemplate("classes/{fullclass}")
     *     .auth(AuthConfig.bearer("token123"))
     *     .build();
     * }</pre>
     */
    public static class Builder {
        private String baseUrl;
        private String classPathTemplate;
        private final Map<String, String> headers = new HashMap<>();
        private final Map<String, String> queryParams = new HashMap<>();
        private AuthConfig authConfig = AuthConfig.none();
        private ResponseFormat responseFormat = ResponseFormat.BINARY;
        private int connectTimeout = DEFAULT_CONNECT_TIMEOUT;
        private int readTimeout = DEFAULT_READ_TIMEOUT;
        private boolean enableCanLoadCheck = false;

        /** Sets the base URL of the REST API.
         * @param baseUrl Base URL
         * @return this builder */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl cannot be null");
            return this;
        }

        /** Sets the URL template for class paths.
         * @param template URL template (null to use default)
         * @return this builder */
        public Builder classPathTemplate(String template) {
            this.classPathTemplate = template;
            return this;
        }

        /** Adds a custom HTTP header to all requests.
         * @param name Header name
         * @param value Header value
         * @return this builder */
        public Builder addHeader(String name, String value) {
            Objects.requireNonNull(name, "header name cannot be null");
            Objects.requireNonNull(value, "header value cannot be null");
            this.headers.put(name, value);
            return this;
        }

        /** Adds a query parameter to all requests.
         * @param name Parameter name
         * @param value Parameter value
         * @return this builder */
        public Builder addQueryParam(String name, String value) {
            Objects.requireNonNull(name, "query param name cannot be null");
            Objects.requireNonNull(value, "query param value cannot be null");
            this.queryParams.put(name, value);
            return this;
        }

        /** Sets authentication configuration.
         * @param authConfig Authentication configuration (null = no auth)
         * @return this builder */
        public Builder auth(AuthConfig authConfig) {
            this.authConfig = authConfig;
            return this;
        }

        /** Sets the response format.
         * @param format Response format (null = BINARY)
         * @return this builder */
        public Builder responseFormat(ResponseFormat format) {
            this.responseFormat = format;
            return this;
        }

        /** Sets the connection timeout.
         * @param timeoutMs Timeout in milliseconds (default: 10000ms, 0 = infinite)
         * @return this builder */
        public Builder connectTimeout(int timeoutMs) {
            if (timeoutMs < 0) {
                throw new IllegalArgumentException("connectTimeout must be >= 0");
            }
            this.connectTimeout = timeoutMs;
            return this;
        }

        /** Sets the read timeout.
         * @param timeoutMs Timeout in milliseconds (default: 30000ms, 0 = infinite)
         * @return this builder */
        public Builder readTimeout(int timeoutMs) {
            if (timeoutMs < 0) {
                throw new IllegalArgumentException("readTimeout must be >= 0");
            }
            this.readTimeout = timeoutMs;
            return this;
        }

        /** Enables or disables canLoad() HEAD requests.
         * @param enable true to enable HEAD requests, false to skip (default: false)
         * @return this builder */
        public Builder enableCanLoadCheck(boolean enable) {
            this.enableCanLoadCheck = enable;
            return this;
        }

        /** Builds the RestApiClassSource with configured settings.
         * @return A new RestApiClassSource instance */
        public RestApiClassSource build() {
            return new RestApiClassSource(baseUrl, classPathTemplate, headers,
                                         queryParams, authConfig, responseFormat,
                                         connectTimeout, readTimeout, enableCanLoadCheck);
        }
    }
}
