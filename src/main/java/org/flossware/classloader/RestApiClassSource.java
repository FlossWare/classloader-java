package org.flossware.classloader;

import java.io.ByteArrayOutputStream;

import static org.flossware.classloader.util.ClassLoaderConstants.DEFAULT_BUFFER_SIZE;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
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

    private final String baseUrl;
    private final String classPathTemplate;
    private final Map<String, String> headers;
    private final Map<String, String> queryParams;
    private final AuthConfig authConfig;
    private final ResponseFormat responseFormat;
    private final int connectTimeout;
    private final int readTimeout;
    private final boolean enableCanLoadCheck;

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
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        this.classPathTemplate = classPathTemplate != null ? classPathTemplate : "{package}/{class}.class";
        this.headers = new HashMap<>(headers);
        this.queryParams = new HashMap<>(queryParams);
        this.authConfig = authConfig != null ? authConfig : AuthConfig.none();
        this.responseFormat = responseFormat != null ? responseFormat : ResponseFormat.BINARY;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
        this.enableCanLoadCheck = enableCanLoadCheck;
    }

    @Override
    public byte[] loadClassData(String className) throws IOException {
        Objects.requireNonNull(className, "className cannot be null");
        String url = buildUrl(className);
        // HttpURLConnection does not implement AutoCloseable, so we use try/finally with disconnect()
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        try {
            connection.setConnectTimeout(connectTimeout);
            connection.setReadTimeout(readTimeout);
            connection.setRequestMethod("GET");
            configureConnection(connection);

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + responseCode + " for URL: " + url);
            }

            // Check response size before downloading
            long contentLength = connection.getContentLengthLong();
            if (contentLength > MAX_RESPONSE_SIZE) {
                throw new IOException(
                    "Response too large: " + contentLength + " bytes (max " + MAX_RESPONSE_SIZE + ")"
                );
            }

            try (InputStream in = connection.getInputStream();
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {

                // Download with size limit enforcement
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

                byte[] responseData = out.toByteArray();
                return processResponse(responseData);
            }
        } finally {
            connection.disconnect();
        }
    }

    /**
     * Checks if this source can load the specified class.
     *
     * <p><b>Performance Note:</b> This method makes a HEAD request to the API by default,
     * which doubles network traffic. Set enableCanLoadCheck=false in builder to skip this
     * expensive check and let loadClassData() fail if the class doesn't exist.</p>
     *
     * @param className The fully qualified class name to check
     * @return true if enableCanLoadCheck is false OR the class exists, false otherwise
     */
    @Override
    public boolean canLoad(String className) {
        Objects.requireNonNull(className, "className cannot be null");
        if (!enableCanLoadCheck) {
            return true;  // Skip expensive check, let loadClassData() fail if needed
        }

        // HttpURLConnection does not implement AutoCloseable, so we use try/finally with disconnect()
        HttpURLConnection connection = null;
        try {
            String url = buildUrl(className);
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(connectTimeout);
            connection.setReadTimeout(readTimeout);
            connection.setRequestMethod("HEAD");
            configureConnection(connection);

            int responseCode = connection.getResponseCode();
            return responseCode == HttpURLConnection.HTTP_OK;
        } catch (IOException e) {
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    @Override
    public String getDescription() {
        return "RestApiClassSource[" + baseUrl + ", format=" + responseFormat + ", auth=" + authConfig.getAuthType() + "]";
    }

    private String buildUrl(String className) {
        String packagePath = "";
        String simpleClassName = className;

        int lastDot = className.lastIndexOf('.');
        if (lastDot > 0) {
            packagePath = className.substring(0, lastDot).replace('.', '/');
            simpleClassName = className.substring(lastDot + 1);
        }

        String path = classPathTemplate
            .replace("{package}", packagePath)
            .replace("{class}", simpleClassName)
            .replace("{fullclass}", className.replace('.', '/'));

        StringBuilder urlBuilder = new StringBuilder(baseUrl);
        urlBuilder.append(path);

        if (!queryParams.isEmpty()) {
            urlBuilder.append("?");
            queryParams.forEach((key, value) -> {
                // Use StandardCharsets.UTF_8 directly (no exception thrown)
                urlBuilder.append(URLEncoder.encode(key, StandardCharsets.UTF_8))
                         .append("=")
                         .append(URLEncoder.encode(value, StandardCharsets.UTF_8))
                         .append("&");
            });
            urlBuilder.setLength(urlBuilder.length() - 1);
        }

        return urlBuilder.toString();
    }

    private void configureConnection(HttpURLConnection connection) {
        headers.forEach(connection::setRequestProperty);
        AuthHelper.configureAuth(connection, authConfig);
    }

    private byte[] processResponse(byte[] responseData) throws IOException {
        switch (responseFormat) {
            case BINARY:
            case DIRECT:
                return responseData;
            case BASE64_JSON_FIELD:
                String json = new String(responseData, StandardCharsets.UTF_8);
                String base64Data = extractBase64FromJson(json);
                return Base64.getDecoder().decode(base64Data);
            default:
                return responseData;
        }
    }

    private String extractBase64FromJson(String json) throws IOException {
        int start = json.indexOf("\"data\":\"");
        if (start == -1) {
            start = json.indexOf("\"content\":\"");
        }
        if (start == -1) {
            throw new IOException("Cannot find data field in JSON response");
        }

        start += 8;
        int end = json.indexOf("\"", start);
        if (end == -1) {
            throw new IOException("Malformed JSON response");
        }

        return json.substring(start, end);
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
     * <p>Configures a custom REST API for class loading with flexible URL templates,
     * headers, query parameters, authentication, and response formats.</p>
     *
     * <p><b>Basic Example (Binary Response):</b></p>
     * <pre>{@code
     * RestApiClassSource source = RestApiClassSource.builder()
     *     .baseUrl("https://classes.example.com/api/v1")
     *     .classPathTemplate("classes/{fullclass}")
     *     .auth(AuthConfig.bearer("token123"))
     *     .build();
     * }</pre>
     *
     * <p><b>Advanced Example (JSON Response with Base64):</b></p>
     * <pre>{@code
     * RestApiClassSource source = RestApiClassSource.builder()
     *     .baseUrl("https://api.example.com")
     *     .classPathTemplate("classes/{package}/{class}")
     *     .addHeader("X-API-Version", "2.0")
     *     .addQueryParam("env", "production")
     *     .auth(AuthConfig.apiKey("X-API-Key", "secret123"))
     *     .responseFormat(ResponseFormat.BASE64_JSON_FIELD)
     *     .connectTimeout(15000)
     *     .readTimeout(60000)
     *     .enableCanLoadCheck(true)  // Enable HEAD requests
     *     .build();
     * }</pre>
     *
     * <p><b>URL Template Variables:</b></p>
     * <ul>
     *   <li>{@code {package}} - Package path (e.g., "com/example")</li>
     *   <li>{@code {class}} - Simple class name (e.g., "MyClass")</li>
     *   <li>{@code {fullclass}} - Full class path (e.g., "com/example/MyClass")</li>
     * </ul>
     *
     * <p><b>Response Formats:</b></p>
     * <ul>
     *   <li><b>BINARY</b> - Raw class bytes in response body (default)</li>
     *   <li><b>BASE64_JSON_FIELD</b> - JSON with base64-encoded class in "data" or "content" field</li>
     *   <li><b>DIRECT</b> - Same as BINARY</li>
     * </ul>
     *
     * <p><b>Defaults:</b></p>
     * <ul>
     *   <li>classPathTemplate: "{package}/{class}.class"</li>
     *   <li>responseFormat: BINARY</li>
     *   <li>connectTimeout: 10000ms (10 seconds)</li>
     *   <li>readTimeout: 30000ms (30 seconds)</li>
     *   <li>enableCanLoadCheck: false (skip HEAD requests for performance)</li>
     *   <li>auth: none</li>
     * </ul>
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
        private boolean enableCanLoadCheck = false;  // Default: skip expensive checks

        /**
         * Sets the base URL of the REST API.
         *
         * <p>This is prepended to the classPathTemplate.</p>
         *
         * <p>Examples:</p>
         * <ul>
         *   <li>"https://classes.example.com/api/v1"</li>
         *   <li>"http://localhost:8080/classes"</li>
         *   <li>"https://cdn.example.com"</li>
         * </ul>
         *
         * @param baseUrl Base URL (automatically adds trailing slash if missing)
         * @return this builder
         * @throws NullPointerException if baseUrl is null
         */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl cannot be null");
            return this;
        }

        /**
         * Sets the URL template for class paths.
         *
         * <p>Uses placeholders: {package}, {class}, {fullclass}</p>
         *
         * <p>Examples:</p>
         * <ul>
         *   <li>"{package}/{class}.class" (default)</li>
         *   <li>"classes/{fullclass}" → "classes/com/example/MyClass"</li>
         *   <li>"v2/{package}/{class}.bin"</li>
         * </ul>
         *
         * @param template URL template (null to use default)
         * @return this builder
         */
        public Builder classPathTemplate(String template) {
            this.classPathTemplate = template;
            return this;
        }

        /**
         * Adds a custom HTTP header to all requests.
         *
         * <p>Useful for API versioning, custom authentication, or metadata.</p>
         *
         * <p>Example:</p>
         * <pre>{@code
         * builder.addHeader("X-API-Version", "2.0")
         *        .addHeader("Accept", "application/octet-stream")
         * }</pre>
         *
         * @param name Header name
         * @param value Header value
         * @return this builder
         * @throws NullPointerException if name or value is null
         */
        public Builder addHeader(String name, String value) {
            Objects.requireNonNull(name, "header name cannot be null");
            Objects.requireNonNull(value, "header value cannot be null");
            this.headers.put(name, value);
            return this;
        }

        /**
         * Adds a query parameter to all requests.
         *
         * <p>Example:</p>
         * <pre>{@code
         * builder.addQueryParam("env", "production")
         *        .addQueryParam("version", "1.0")
         * // URLs become: baseUrl/path?env=production&version=1.0
         * }</pre>
         *
         * @param name Parameter name
         * @param value Parameter value
         * @return this builder
         * @throws NullPointerException if name or value is null
         */
        public Builder addQueryParam(String name, String value) {
            Objects.requireNonNull(name, "query param name cannot be null");
            Objects.requireNonNull(value, "query param value cannot be null");
            this.queryParams.put(name, value);
            return this;
        }

        /**
         * Sets authentication configuration.
         *
         * <p>Supported types:</p>
         * <ul>
         *   <li>AuthConfig.none() - No authentication (default)</li>
         *   <li>AuthConfig.basic(username, password) - HTTP Basic</li>
         *   <li>AuthConfig.bearer(token) - Bearer token</li>
         *   <li>AuthConfig.apiKey(headerName, key) - API key header</li>
         * </ul>
         *
         * @param authConfig Authentication configuration (null = no auth)
         * @return this builder
         */
        public Builder auth(AuthConfig authConfig) {
            this.authConfig = authConfig;
            return this;
        }

        /**
         * Sets the response format.
         *
         * <p>Determines how to parse the API response:</p>
         * <ul>
         *   <li><b>BINARY</b> - Raw bytes (default, most efficient)</li>
         *   <li><b>BASE64_JSON_FIELD</b> - JSON with base64 in "data" or "content" field</li>
         *   <li><b>DIRECT</b> - Same as BINARY</li>
         * </ul>
         *
         * @param format Response format (null = BINARY)
         * @return this builder
         */
        public Builder responseFormat(ResponseFormat format) {
            this.responseFormat = format;
            return this;
        }

        /**
         * Sets the connection timeout.
         *
         * <p>Prevents hanging indefinitely when connecting to the API.</p>
         *
         * @param timeoutMs Timeout in milliseconds (default: 10000ms = 10 seconds, 0 = infinite)
         * @return this builder
         * @throws IllegalArgumentException if timeoutMs < 0
         */
        public Builder connectTimeout(int timeoutMs) {
            if (timeoutMs < 0) {
                throw new IllegalArgumentException("connectTimeout must be >= 0");
            }
            this.connectTimeout = timeoutMs;
            return this;
        }

        /**
         * Sets the read timeout.
         *
         * <p>Prevents hanging indefinitely when reading response data.</p>
         *
         * @param timeoutMs Timeout in milliseconds (default: 30000ms = 30 seconds, 0 = infinite)
         * @return this builder
         * @throws IllegalArgumentException if timeoutMs < 0
         */
        public Builder readTimeout(int timeoutMs) {
            if (timeoutMs < 0) {
                throw new IllegalArgumentException("readTimeout must be >= 0");
            }
            this.readTimeout = timeoutMs;
            return this;
        }

        /**
         * Enables or disables canLoad() HEAD requests.
         *
         * <p><b>Performance Impact:</b> When enabled, canLoad() makes a HEAD request
         * to check if a class exists, which <b>doubles network traffic</b>. When disabled
         * (default), canLoad() always returns true and loadClassData() fails if the
         * class doesn't exist.</p>
         *
         * <p><b>Recommendation:</b> Leave disabled (false) unless your application
         * specifically needs to check class existence before loading.</p>
         *
         * @param enable true to enable HEAD requests, false to skip (default: false)
         * @return this builder
         */
        public Builder enableCanLoadCheck(boolean enable) {
            this.enableCanLoadCheck = enable;
            return this;
        }

        /**
         * Builds the RestApiClassSource with configured settings.
         *
         * @return A new RestApiClassSource instance
         */
        public RestApiClassSource build() {
            return new RestApiClassSource(baseUrl, classPathTemplate, headers,
                                         queryParams, authConfig, responseFormat,
                                         connectTimeout, readTimeout, enableCanLoadCheck);
        }
    }
}
