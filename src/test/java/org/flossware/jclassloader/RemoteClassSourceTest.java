package org.flossware.jclassloader;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RemoteClassSource including authentication and SSL/TLS configuration.
 */
class RemoteClassSourceTest {

    @Test
    void testConstructorWithAuth() {
        AuthConfig auth = AuthConfig.basic("user", "pass");
        RemoteClassSource source = new RemoteClassSource("https://example.com/classes", auth);

        assertEquals("https://example.com/classes/", source.getBaseUrl());
        assertEquals(auth, source.getAuthConfig());
    }

    @Test
    void testConstructorWithoutAuth() {
        RemoteClassSource source = new RemoteClassSource("https://example.com/classes");

        assertEquals("https://example.com/classes/", source.getBaseUrl());
        assertEquals(AuthConfig.AuthType.NONE, source.getAuthConfig().getAuthType());
    }

    @Test
    void testConstructorAddsTrailingSlash() {
        RemoteClassSource source = new RemoteClassSource("https://example.com/classes");

        assertTrue(source.getBaseUrl().endsWith("/"));
        assertEquals("https://example.com/classes/", source.getBaseUrl());
    }

    @Test
    void testConstructorPreservesTrailingSlash() {
        RemoteClassSource source = new RemoteClassSource("https://example.com/classes/");

        assertEquals("https://example.com/classes/", source.getBaseUrl());
    }

    @Test
    void testConstructorNullBaseUrlThrowsException() {
        assertThrows(NullPointerException.class, () -> {
            new RemoteClassSource(null);
        });
    }

    @Test
    void testConstructorNullAuthConfigUsesNone() {
        RemoteClassSource source = new RemoteClassSource("https://example.com", null);

        assertEquals(AuthConfig.AuthType.NONE, source.getAuthConfig().getAuthType());
    }

    @Test
    void testGetDescription() {
        RemoteClassSource source = new RemoteClassSource("https://example.com/classes");

        String description = source.getDescription();
        assertTrue(description.contains("RemoteClassSource"));
        assertTrue(description.contains("https://example.com/classes/"));
        assertTrue(description.contains("NONE"));
    }

    @Test
    void testGetDescriptionWithAuth() {
        AuthConfig auth = AuthConfig.bearer("token");
        RemoteClassSource source = new RemoteClassSource("https://example.com", auth);

        String description = source.getDescription();
        assertTrue(description.contains("BEARER"));
    }

    @Test
    void testHttpUrl() {
        RemoteClassSource source = new RemoteClassSource("http://example.com/classes");

        assertEquals("http://example.com/classes/", source.getBaseUrl());
    }

    @Test
    void testHttpsUrl() {
        RemoteClassSource source = new RemoteClassSource("https://secure.example.com/classes");

        assertEquals("https://secure.example.com/classes/", source.getBaseUrl());
    }

    @Test
    void testUrlWithPort() {
        RemoteClassSource source = new RemoteClassSource("https://example.com:8443/classes");

        assertEquals("https://example.com:8443/classes/", source.getBaseUrl());
    }

    @Test
    void testUrlWithQueryParameters() {
        RemoteClassSource source = new RemoteClassSource("https://example.com/classes?version=1.0");

        assertEquals("https://example.com/classes?version=1.0/", source.getBaseUrl());
    }

    @Test
    void testBasicAuth() {
        AuthConfig auth = AuthConfig.basic("admin", "secret");
        RemoteClassSource source = new RemoteClassSource("https://example.com", auth);

        assertEquals(AuthConfig.AuthType.BASIC, source.getAuthConfig().getAuthType());
        assertEquals("admin", source.getAuthConfig().getUsername());
        assertEquals("secret", source.getAuthConfig().getPassword());
    }

    @Test
    void testBearerAuth() {
        AuthConfig auth = AuthConfig.bearer("my-token-123");
        RemoteClassSource source = new RemoteClassSource("https://example.com", auth);

        assertEquals(AuthConfig.AuthType.BEARER, source.getAuthConfig().getAuthType());
        assertEquals("my-token-123", source.getAuthConfig().getToken());
    }

    @Test
    void testGetBaseUrl() {
        RemoteClassSource source = new RemoteClassSource("https://cdn.example.com/classes/");

        assertEquals("https://cdn.example.com/classes/", source.getBaseUrl());
    }

    @Test
    void testGetAuthConfig() {
        AuthConfig auth = AuthConfig.basic("user", "pass");
        RemoteClassSource source = new RemoteClassSource("https://example.com", auth);

        assertSame(auth, source.getAuthConfig());
    }

    @Test
    void testMultipleInstances() {
        RemoteClassSource source1 = new RemoteClassSource("https://server1.com/classes");
        RemoteClassSource source2 = new RemoteClassSource("https://server2.com/classes");

        assertNotEquals(source1.getBaseUrl(), source2.getBaseUrl());
        assertEquals("https://server1.com/classes/", source1.getBaseUrl());
        assertEquals("https://server2.com/classes/", source2.getBaseUrl());
    }
}
