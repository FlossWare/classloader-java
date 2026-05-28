package org.flossware.classloader;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Tests for RestApiClassSource builder and configuration.
 */
class RestApiClassSourceTest {

    @Test
    void testBuilderBasic() {
        RestApiClassSource source = RestApiClassSource.builder()
                .baseUrl("https://api.example.com/classes")
                .build();

        assertNotNull(source);
        assertTrue(source.getDescription().contains("https://api.example.com/classes/"));
    }

    @Test
    void testBuilderWithAuth() {
        AuthConfig auth = AuthConfig.basic("user", "pass");
        RestApiClassSource source = RestApiClassSource.builder()
                .baseUrl("https://api.example.com")
                .auth(auth)
                .build();

        assertTrue(source.getDescription().contains("BASIC"));
    }

    @Test
    void testBuilderWithBearerAuth() {
        AuthConfig auth = AuthConfig.bearer("token123");
        RestApiClassSource source = RestApiClassSource.builder()
                .baseUrl("https://api.example.com")
                .auth(auth)
                .build();

        assertTrue(source.getDescription().contains("BEARER"));
    }

    @Test
    void testBuilderWithHeaders() {
        RestApiClassSource source = RestApiClassSource.builder()
                .baseUrl("https://api.example.com")
                .addHeader("X-Custom-Header", "value")
                .addHeader("Accept", "application/octet-stream")
                .build();

        assertNotNull(source);
    }

    @Test
    void testBuilderWithQueryParams() {
        RestApiClassSource source = RestApiClassSource.builder()
                .baseUrl("https://api.example.com")
                .addQueryParam("version", "1.0")
                .addQueryParam("format", "binary")
                .build();

        assertNotNull(source);
    }

    @Test
    void testBuilderWithClassPathTemplate() {
        RestApiClassSource source = RestApiClassSource.builder()
                .baseUrl("https://api.example.com")
                .classPathTemplate("api/v1/{fullclass}")
                .build();

        assertNotNull(source);
    }

    @Test
    void testBuilderWithBinaryResponseFormat() {
        RestApiClassSource source = RestApiClassSource.builder()
                .baseUrl("https://api.example.com")
                .responseFormat(RestApiClassSource.ResponseFormat.BINARY)
                .build();

        assertTrue(source.getDescription().contains("BINARY"));
    }

    @Test
    void testBuilderWithBase64JsonResponseFormat() {
        RestApiClassSource source = RestApiClassSource.builder()
                .baseUrl("https://api.example.com")
                .responseFormat(RestApiClassSource.ResponseFormat.BASE64_JSON_FIELD)
                .build();

        assertTrue(source.getDescription().contains("BASE64_JSON_FIELD"));
    }

    @Test
    void testBuilderWithDirectResponseFormat() {
        RestApiClassSource source = RestApiClassSource.builder()
                .baseUrl("https://api.example.com")
                .responseFormat(RestApiClassSource.ResponseFormat.DIRECT)
                .build();

        assertTrue(source.getDescription().contains("DIRECT"));
    }

    @Test
    void testBuilderAddsTrailingSlash() {
        RestApiClassSource source = RestApiClassSource.builder()
                .baseUrl("https://api.example.com/classes")
                .build();

        assertTrue(source.getDescription().contains("https://api.example.com/classes/"));
    }

    @Test
    void testBuilderPreservesTrailingSlash() {
        RestApiClassSource source = RestApiClassSource.builder()
                .baseUrl("https://api.example.com/classes/")
                .build();

        assertTrue(source.getDescription().contains("https://api.example.com/classes/"));
    }

    @Test
    void testGetDescription() {
        RestApiClassSource source = RestApiClassSource.builder()
                .baseUrl("https://api.example.com")
                .responseFormat(RestApiClassSource.ResponseFormat.BINARY)
                .auth(AuthConfig.none())
                .build();

        String description = source.getDescription();
        assertTrue(description.contains("RestApiClassSource"));
        assertTrue(description.contains("https://api.example.com/"));
        assertTrue(description.contains("format=BINARY"));
        assertTrue(description.contains("auth=NONE"));
    }

    @Test
    void testDefaultResponseFormat() {
        RestApiClassSource source = RestApiClassSource.builder()
                .baseUrl("https://api.example.com")
                .build();

        assertTrue(source.getDescription().contains("format=BINARY"));
    }

    @Test
    void testDefaultAuthConfig() {
        RestApiClassSource source = RestApiClassSource.builder()
                .baseUrl("https://api.example.com")
                .build();

        assertTrue(source.getDescription().contains("auth=NONE"));
    }

    @Test
    void testBuilderNullBaseUrlThrowsException() {
        assertThrows(NullPointerException.class, () -> {
            RestApiClassSource.builder()
                    .baseUrl(null)
                    .build();
        });
    }

    @Test
    void testBuilderWithAllOptions() {
        RestApiClassSource source = RestApiClassSource.builder()
                .baseUrl("https://api.example.com")
                .classPathTemplate("v1/classes/{package}/{class}")
                .addHeader("X-API-Key", "secret")
                .addHeader("Accept", "application/octet-stream")
                .addQueryParam("version", "1.0")
                .addQueryParam("env", "prod")
                .auth(AuthConfig.basic("admin", "pass"))
                .responseFormat(RestApiClassSource.ResponseFormat.BASE64_JSON_FIELD)
                .build();

        String description = source.getDescription();
        assertTrue(description.contains("BASE64_JSON_FIELD"));
        assertTrue(description.contains("BASIC"));
    }

    @Test
    void testMultipleHeaders() {
        RestApiClassSource source = RestApiClassSource.builder()
                .baseUrl("https://api.example.com")
                .addHeader("Header1", "value1")
                .addHeader("Header2", "value2")
                .addHeader("Header3", "value3")
                .build();

        assertNotNull(source);
    }

    @Test
    void testMultipleQueryParams() {
        RestApiClassSource source = RestApiClassSource.builder()
                .baseUrl("https://api.example.com")
                .addQueryParam("param1", "value1")
                .addQueryParam("param2", "value2")
                .addQueryParam("param3", "value3")
                .build();

        assertNotNull(source);
    }

    @Test
    void testResponseFormatEnum() {
        assertEquals(3, RestApiClassSource.ResponseFormat.values().length);
        assertNotNull(RestApiClassSource.ResponseFormat.valueOf("BINARY"));
        assertNotNull(RestApiClassSource.ResponseFormat.valueOf("BASE64_JSON_FIELD"));
        assertNotNull(RestApiClassSource.ResponseFormat.valueOf("DIRECT"));
    }

    @Test
    void testBuilderChaining() {
        RestApiClassSource source = RestApiClassSource.builder()
                .baseUrl("https://api.example.com")
                .classPathTemplate("{fullclass}")
                .addHeader("X-Custom", "value")
                .addQueryParam("key", "value")
                .auth(AuthConfig.bearer("token"))
                .responseFormat(RestApiClassSource.ResponseFormat.BINARY)
                .build();

        assertNotNull(source);
        assertTrue(source.getDescription().contains("BINARY"));
        assertTrue(source.getDescription().contains("BEARER"));
    }
}
