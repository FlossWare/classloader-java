package org.flossware.jclassloader;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for FtpClassSource including authentication and URL validation.
 */
class FtpClassSourceTest {

    @Test
    void testConstructorWithAuth() {
        FtpClassSource source = new FtpClassSource("ftp://example.com/classes", "user", "pass");

        assertEquals("ftp://example.com/classes/", source.getBaseUrl());
    }

    @Test
    void testConstructorWithoutAuth() {
        FtpClassSource source = new FtpClassSource("ftp://example.com/classes");

        assertEquals("ftp://example.com/classes/", source.getBaseUrl());
    }

    @Test
    void testConstructorAddsTrailingSlash() {
        FtpClassSource source = new FtpClassSource("ftp://example.com/classes");

        assertTrue(source.getBaseUrl().endsWith("/"));
        assertEquals("ftp://example.com/classes/", source.getBaseUrl());
    }

    @Test
    void testConstructorPreservesTrailingSlash() {
        FtpClassSource source = new FtpClassSource("ftp://example.com/classes/");

        assertEquals("ftp://example.com/classes/", source.getBaseUrl());
    }

    @Test
    void testConstructorNullBaseUrlThrowsException() {
        assertThrows(NullPointerException.class, () -> {
            new FtpClassSource(null);
        });
    }

    @Test
    void testConstructorInvalidProtocolThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            new FtpClassSource("http://example.com/classes");
        });
    }

    @Test
    void testConstructorInvalidProtocolHttpsThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            new FtpClassSource("https://example.com/classes");
        });
    }

    @Test
    void testFtpProtocol() {
        FtpClassSource source = new FtpClassSource("ftp://example.com/classes");

        assertEquals("ftp://example.com/classes/", source.getBaseUrl());
    }

    @Test
    void testFtpsProtocol() {
        FtpClassSource source = new FtpClassSource("ftps://secure.example.com/classes");

        assertEquals("ftps://secure.example.com/classes/", source.getBaseUrl());
    }

    @Test
    void testGetDescription() {
        FtpClassSource source = new FtpClassSource("ftp://example.com/classes");

        String description = source.getDescription();
        assertTrue(description.contains("FtpClassSource"));
        assertTrue(description.contains("ftp://example.com/classes/"));
        assertTrue(description.contains("authenticated=false"));
    }

    @Test
    void testGetDescriptionWithAuth() {
        FtpClassSource source = new FtpClassSource("ftp://example.com", "user", "pass");

        String description = source.getDescription();
        assertTrue(description.contains("authenticated=true"));
    }

    @Test
    void testGetDescriptionAnonymous() {
        FtpClassSource source = new FtpClassSource("ftp://example.com", null, null);

        String description = source.getDescription();
        assertTrue(description.contains("authenticated=false"));
    }

    @Test
    void testUrlWithPort() {
        FtpClassSource source = new FtpClassSource("ftp://example.com:2121/classes");

        assertEquals("ftp://example.com:2121/classes/", source.getBaseUrl());
    }

    @Test
    void testUrlWithDeepPath() {
        FtpClassSource source = new FtpClassSource("ftp://example.com/data/classes/java");

        assertEquals("ftp://example.com/data/classes/java/", source.getBaseUrl());
    }

    @Test
    void testGetBaseUrl() {
        FtpClassSource source = new FtpClassSource("ftps://secure.example.com/classes/");

        assertEquals("ftps://secure.example.com/classes/", source.getBaseUrl());
    }

    @Test
    void testConstructorWithUsernameOnly() {
        FtpClassSource source = new FtpClassSource("ftp://example.com", "user", null);

        assertNotNull(source);
        assertEquals("ftp://example.com/", source.getBaseUrl());
    }

    @Test
    void testConstructorWithPasswordOnly() {
        FtpClassSource source = new FtpClassSource("ftp://example.com", null, "pass");

        assertNotNull(source);
        assertEquals("ftp://example.com/", source.getBaseUrl());
    }

    @Test
    void testMultipleInstances() {
        FtpClassSource source1 = new FtpClassSource("ftp://server1.com/classes");
        FtpClassSource source2 = new FtpClassSource("ftps://server2.com/classes");

        assertNotEquals(source1.getBaseUrl(), source2.getBaseUrl());
        assertEquals("ftp://server1.com/classes/", source1.getBaseUrl());
        assertEquals("ftps://server2.com/classes/", source2.getBaseUrl());
    }

    @Test
    void testFtpWithAuthentication() {
        FtpClassSource source = new FtpClassSource("ftp://example.com/classes", "admin", "secret");

        String description = source.getDescription();
        assertTrue(description.contains("authenticated=true"));
        assertFalse(description.contains("admin"));
        assertFalse(description.contains("secret"));
    }

    @Test
    void testEmptyBaseUrlThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            new FtpClassSource("");
        });
    }

    @Test
    void testInvalidSchemeThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            new FtpClassSource("file:///classes");
        });
    }
}
