package org.flossware.jclassloader;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for WebDavClassSource construction and configuration.
 */
class WebDavClassSourceTest {

    @Test
    void testConstructorWithAuth() {
        WebDavClassSource source = new WebDavClassSource("https://webdav.example.com/classes", "user", "pass");

        assertEquals("https://webdav.example.com/classes/", source.getBaseUrl());
    }

    @Test
    void testConstructorWithoutAuth() {
        WebDavClassSource source = new WebDavClassSource("https://webdav.example.com/classes");

        assertEquals("https://webdav.example.com/classes/", source.getBaseUrl());
    }

    @Test
    void testConstructorAddsTrailingSlash() {
        WebDavClassSource source = new WebDavClassSource("https://webdav.example.com/classes");

        assertTrue(source.getBaseUrl().endsWith("/"));
        assertEquals("https://webdav.example.com/classes/", source.getBaseUrl());
    }

    @Test
    void testConstructorPreservesTrailingSlash() {
        WebDavClassSource source = new WebDavClassSource("https://webdav.example.com/classes/");

        assertEquals("https://webdav.example.com/classes/", source.getBaseUrl());
    }

    @Test
    void testConstructorNullBaseUrlThrowsException() {
        assertThrows(NullPointerException.class, () -> {
            new WebDavClassSource(null);
        });
    }

    @Test
    void testConstructorNullAuthIsAnonymous() {
        WebDavClassSource source = new WebDavClassSource("https://webdav.example.com", null, null);

        assertEquals("https://webdav.example.com/", source.getBaseUrl());
    }

    @Test
    void testGetDescription() {
        WebDavClassSource source = new WebDavClassSource("https://webdav.example.com/classes");

        String description = source.getDescription();
        assertTrue(description.contains("WebDavClassSource"));
        assertTrue(description.contains("https://webdav.example.com/classes/"));
        assertTrue(description.contains("authenticated=false"));
    }

    @Test
    void testGetDescriptionWithAuth() {
        WebDavClassSource source = new WebDavClassSource("https://webdav.example.com", "admin", "secret");

        String description = source.getDescription();
        assertTrue(description.contains("authenticated=true"));
        assertFalse(description.contains("admin"));
        assertFalse(description.contains("secret"));
    }

    @Test
    void testGetBaseUrl() {
        WebDavClassSource source = new WebDavClassSource("https://webdav.example.com/data/classes");

        assertEquals("https://webdav.example.com/data/classes/", source.getBaseUrl());
    }

    @Test
    void testHttpUrl() {
        WebDavClassSource source = new WebDavClassSource("http://webdav.example.com/classes");

        assertEquals("http://webdav.example.com/classes/", source.getBaseUrl());
    }

    @Test
    void testHttpsUrl() {
        WebDavClassSource source = new WebDavClassSource("https://secure.example.com/classes");

        assertEquals("https://secure.example.com/classes/", source.getBaseUrl());
    }

    @Test
    void testUrlWithPort() {
        WebDavClassSource source = new WebDavClassSource("https://webdav.example.com:8443/classes");

        assertEquals("https://webdav.example.com:8443/classes/", source.getBaseUrl());
    }

    @Test
    void testUrlWithDeepPath() {
        WebDavClassSource source = new WebDavClassSource("https://webdav.example.com/data/storage/classes/java");

        assertEquals("https://webdav.example.com/data/storage/classes/java/", source.getBaseUrl());
    }

    @Test
    void testShutdownDoesNotThrow() {
        WebDavClassSource source = new WebDavClassSource("https://webdav.example.com");

        assertDoesNotThrow(() -> source.shutdown());
    }

    @Test
    void testMultipleInstances() {
        WebDavClassSource source1 = new WebDavClassSource("https://server1.com/classes");
        WebDavClassSource source2 = new WebDavClassSource("https://server2.com/classes");

        assertNotEquals(source1.getBaseUrl(), source2.getBaseUrl());
        assertEquals("https://server1.com/classes/", source1.getBaseUrl());
        assertEquals("https://server2.com/classes/", source2.getBaseUrl());
    }

    @Test
    void testConstructorWithUsernameOnlyIsNotAuthenticated() {
        WebDavClassSource source = new WebDavClassSource("https://webdav.example.com", "user", null);

        assertNotNull(source);
        String description = source.getDescription();
        assertTrue(description.contains("WebDavClassSource"));
    }

    @Test
    void testConstructorWithPasswordOnly() {
        WebDavClassSource source = new WebDavClassSource("https://webdav.example.com", null, "pass");

        assertNotNull(source);
        assertTrue(source.getDescription().contains("authenticated=false"));
    }

    @Test
    void testAuthenticatedSource() {
        WebDavClassSource source = new WebDavClassSource("https://webdav.example.com", "admin", "secret");

        String description = source.getDescription();
        assertTrue(description.contains("authenticated=true"));
    }

    @Test
    void testAnonymousSource() {
        WebDavClassSource source = new WebDavClassSource("https://webdav.example.com");

        String description = source.getDescription();
        assertTrue(description.contains("authenticated=false"));
    }
}
