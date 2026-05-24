package org.flossware.jclassloader;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Tests for NexusClassSource construction and configuration.
 */
class NexusClassSourceTest {

    @Test
    void testConstructorFullConfiguration() {
        AuthConfig auth = AuthConfig.basic("user", "pass");
        NexusClassSource source = new NexusClassSource("https://nexus.example.com", "releases",
                NexusClassSource.NexusMode.MAVEN, auth);

        assertEquals("https://nexus.example.com/", source.getNexusUrl());
        assertEquals("releases", source.getRepository());
        assertEquals(NexusClassSource.NexusMode.MAVEN, source.getMode());
        assertEquals(auth, source.getAuthConfig());
    }

    @Test
    void testConstructorWithoutAuth() {
        NexusClassSource source = new NexusClassSource("https://nexus.example.com", "releases",
                NexusClassSource.NexusMode.RAW);

        assertEquals("https://nexus.example.com/", source.getNexusUrl());
        assertEquals("releases", source.getRepository());
        assertEquals(NexusClassSource.NexusMode.RAW, source.getMode());
        assertEquals(AuthConfig.AuthType.NONE, source.getAuthConfig().getAuthType());
    }

    @Test
    void testConstructorDefaultMode() {
        NexusClassSource source = new NexusClassSource("https://nexus.example.com", "releases");

        assertEquals(NexusClassSource.NexusMode.MAVEN, source.getMode());
    }

    @Test
    void testConstructorAddsTrailingSlash() {
        NexusClassSource source = new NexusClassSource("https://nexus.example.com", "releases");

        assertTrue(source.getNexusUrl().endsWith("/"));
        assertEquals("https://nexus.example.com/", source.getNexusUrl());
    }

    @Test
    void testConstructorPreservesTrailingSlash() {
        NexusClassSource source = new NexusClassSource("https://nexus.example.com/", "releases");

        assertEquals("https://nexus.example.com/", source.getNexusUrl());
    }

    @Test
    void testConstructorNullUrlThrowsException() {
        assertThrows(NullPointerException.class, () -> {
            new NexusClassSource(null, "releases");
        });
    }

    @Test
    void testConstructorNullRepositoryThrowsException() {
        assertThrows(NullPointerException.class, () -> {
            new NexusClassSource("https://nexus.example.com", null);
        });
    }

    @Test
    void testGetDescription() {
        NexusClassSource source = new NexusClassSource("https://nexus.example.com", "releases",
                NexusClassSource.NexusMode.RAW);

        String description = source.getDescription();
        assertTrue(description.contains("NexusClassSource"));
        assertTrue(description.contains("https://nexus.example.com/"));
        assertTrue(description.contains("repo=releases"));
        assertTrue(description.contains("mode=RAW"));
        assertTrue(description.contains("auth=NONE"));
    }

    @Test
    void testGetDescriptionWithAuth() {
        AuthConfig auth = AuthConfig.bearer("token123");
        NexusClassSource source = new NexusClassSource("https://nexus.example.com", "releases",
                NexusClassSource.NexusMode.MAVEN, auth);

        String description = source.getDescription();
        assertTrue(description.contains("auth=BEARER"));
    }

    @Test
    void testNexusModeEnum() {
        assertEquals(2, NexusClassSource.NexusMode.values().length);
        assertNotNull(NexusClassSource.NexusMode.valueOf("RAW"));
        assertNotNull(NexusClassSource.NexusMode.valueOf("MAVEN"));
    }

    @Test
    void testRawMode() {
        NexusClassSource source = new NexusClassSource("https://nexus.example.com", "raw-repo",
                NexusClassSource.NexusMode.RAW);

        assertEquals(NexusClassSource.NexusMode.RAW, source.getMode());
    }

    @Test
    void testMavenMode() {
        NexusClassSource source = new NexusClassSource("https://nexus.example.com", "maven-repo",
                NexusClassSource.NexusMode.MAVEN);

        assertEquals(NexusClassSource.NexusMode.MAVEN, source.getMode());
    }

    @Test
    void testGetNexusUrl() {
        NexusClassSource source = new NexusClassSource("https://nexus.example.com:8081", "releases");

        assertEquals("https://nexus.example.com:8081/", source.getNexusUrl());
    }

    @Test
    void testGetRepository() {
        NexusClassSource source = new NexusClassSource("https://nexus.example.com", "my-repo");

        assertEquals("my-repo", source.getRepository());
    }

    @Test
    void testGetMode() {
        NexusClassSource source1 = new NexusClassSource("https://nexus.example.com", "releases",
                NexusClassSource.NexusMode.RAW);
        NexusClassSource source2 = new NexusClassSource("https://nexus.example.com", "releases",
                NexusClassSource.NexusMode.MAVEN);

        assertEquals(NexusClassSource.NexusMode.RAW, source1.getMode());
        assertEquals(NexusClassSource.NexusMode.MAVEN, source2.getMode());
    }

    @Test
    void testGetAuthConfig() {
        AuthConfig auth = AuthConfig.basic("admin", "secret");
        NexusClassSource source = new NexusClassSource("https://nexus.example.com", "releases",
                NexusClassSource.NexusMode.MAVEN, auth);

        assertSame(auth, source.getAuthConfig());
    }

    @Test
    void testConstructorNullModeDefaultsToMaven() {
        NexusClassSource source = new NexusClassSource("https://nexus.example.com", "releases",
                null, AuthConfig.none());

        assertEquals(NexusClassSource.NexusMode.MAVEN, source.getMode());
    }

    @Test
    void testConstructorNullAuthConfigDefaultsToNone() {
        NexusClassSource source = new NexusClassSource("https://nexus.example.com", "releases",
                NexusClassSource.NexusMode.MAVEN, null);

        assertEquals(AuthConfig.AuthType.NONE, source.getAuthConfig().getAuthType());
    }

    @Test
    void testMultipleInstances() {
        NexusClassSource source1 = new NexusClassSource("https://nexus1.example.com", "repo1");
        NexusClassSource source2 = new NexusClassSource("https://nexus2.example.com", "repo2");

        assertNotEquals(source1.getNexusUrl(), source2.getNexusUrl());
        assertNotEquals(source1.getRepository(), source2.getRepository());
    }
}
