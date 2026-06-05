package org.flossware.classloader.rest;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

/**
 * Builds REST API URLs with support for template variables and query parameters.
 * Encapsulates URL construction logic with proper encoding.
 */
public class UrlBuilder {
    private final String baseUrl;
    private final String classPathTemplate;
    private final Map<String, String> queryParams;

    /**
     * Creates a UrlBuilder with the specified base URL, path template, and query parameters.
     *
     * @param baseUrl the base URL for the REST API (e.g., "https://api.example.com")
     * @param classPathTemplate the path template with placeholders ({package}, {class}, {fullclass})
     * @param queryParams additional query parameters to append to the URL
     */
    public UrlBuilder(String baseUrl, String classPathTemplate, Map<String, String> queryParams) {
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl cannot be null");
        this.classPathTemplate = Objects.requireNonNull(classPathTemplate, "classPathTemplate cannot be null");
        this.queryParams = Objects.requireNonNull(queryParams, "queryParams cannot be null");
    }

    /**
     * Builds the complete URL for a given class name.
     *
     * @param className The fully qualified class name
     * @return The complete URL with query parameters
     */
    public String buildUrl(String className) {
        Objects.requireNonNull(className, "className cannot be null");
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
                urlBuilder.append(URLEncoder.encode(key, StandardCharsets.UTF_8))
                         .append("=")
                         .append(URLEncoder.encode(value, StandardCharsets.UTF_8))
                         .append("&");
            });
            urlBuilder.setLength(urlBuilder.length() - 1);
        }

        return urlBuilder.toString();
    }
}
