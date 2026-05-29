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
    private final String baseUrl;
    private final String classPathTemplate;
    private final Map<String, String> headers;
    private final Map<String, String> queryParams;
    private final AuthConfig authConfig;
    private final ResponseFormat responseFormat;

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
                               AuthConfig authConfig, ResponseFormat responseFormat) {
        Objects.requireNonNull(baseUrl, "baseUrl cannot be null");
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        this.classPathTemplate = classPathTemplate != null ? classPathTemplate : "{package}/{class}.class";
        this.headers = new HashMap<>(headers);
        this.queryParams = new HashMap<>(queryParams);
        this.authConfig = authConfig != null ? authConfig : AuthConfig.none();
        this.responseFormat = responseFormat != null ? responseFormat : ResponseFormat.BINARY;
    }

    @Override
    public byte[] loadClassData(String className) throws IOException {
        String url = buildUrl(className);
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();

        try {
            connection.setRequestMethod("GET");
            configureConnection(connection);

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP error code: " + responseCode + " for URL: " + url);
            }

            try (InputStream in = connection.getInputStream();
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {

                byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }

                byte[] responseData = out.toByteArray();
                return processResponse(responseData);
            }
        } finally {
            connection.disconnect();
        }
    }

    @Override
    public boolean canLoad(String className) {
        try {
            String url = buildUrl(className);
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();

            try {
                connection.setRequestMethod("HEAD");
                configureConnection(connection);

                int responseCode = connection.getResponseCode();
                return responseCode == HttpURLConnection.HTTP_OK;
            } finally {
                connection.disconnect();
            }
        } catch (IOException e) {
            return false;
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
                try {
                    urlBuilder.append(URLEncoder.encode(key, StandardCharsets.UTF_8.name()))
                             .append("=")
                             .append(URLEncoder.encode(value, StandardCharsets.UTF_8.name()))
                             .append("&");
                } catch (Exception e) {
                }
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

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String baseUrl;
        private String classPathTemplate;
        private final Map<String, String> headers = new HashMap<>();
        private final Map<String, String> queryParams = new HashMap<>();
        private AuthConfig authConfig = AuthConfig.none();
        private ResponseFormat responseFormat = ResponseFormat.BINARY;

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl cannot be null");
            return this;
        }

        public Builder classPathTemplate(String template) {
            this.classPathTemplate = template;
            return this;
        }

        public Builder addHeader(String name, String value) {
            Objects.requireNonNull(name, "header name cannot be null");
            Objects.requireNonNull(value, "header value cannot be null");
            this.headers.put(name, value);
            return this;
        }

        public Builder addQueryParam(String name, String value) {
            Objects.requireNonNull(name, "query param name cannot be null");
            Objects.requireNonNull(value, "query param value cannot be null");
            this.queryParams.put(name, value);
            return this;
        }

        public Builder auth(AuthConfig authConfig) {
            this.authConfig = authConfig;
            return this;
        }

        public Builder responseFormat(ResponseFormat format) {
            this.responseFormat = format;
            return this;
        }

        public RestApiClassSource build() {
            return new RestApiClassSource(baseUrl, classPathTemplate, headers,
                                         queryParams, authConfig, responseFormat);
        }
    }
}
