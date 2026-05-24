package org.flossware.jclassloader.cloud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DropboxClassSource builder and configuration.
 */
class DropboxClassSourceTest {

    @Test
    void testBuilderWithAccessToken() {
        DropboxClassSource source = DropboxClassSource.builder()
                .accessToken("test-token")
                .build();

        assertNotNull(source);
        assertTrue(source.getDescription().contains("DropboxClassSource"));
    }

    @Test
    void testBuilderWithBasePath() {
        DropboxClassSource source = DropboxClassSource.builder()
                .accessToken("test-token")
                .basePath("classes/")
                .build();

        assertTrue(source.getDescription().contains("basePath=classes/"));
    }

    @Test
    void testBuilderWithClientIdentifier() {
        DropboxClassSource source = DropboxClassSource.builder()
                .accessToken("test-token")
                .clientIdentifier("MyApp")
                .build();

        assertNotNull(source);
    }

    @Test
    void testBuilderNullAccessTokenThrowsException() {
        assertThrows(NullPointerException.class, () -> {
            DropboxClassSource.builder()
                    .basePath("classes/")
                    .build();
        });
    }

    @Test
    void testGetDescription() {
        DropboxClassSource source = DropboxClassSource.builder()
                .accessToken("token")
                .basePath("app/classes")
                .build();

        String description = source.getDescription();
        assertTrue(description.contains("DropboxClassSource"));
        assertTrue(description.contains("basePath=app/classes"));
    }

    @Test
    void testBuilderChaining() {
        DropboxClassSource.Builder builder = DropboxClassSource.builder();
        assertSame(builder, builder.accessToken("token"));
        assertSame(builder, builder.basePath("path"));
        assertSame(builder, builder.clientIdentifier("client"));
    }

    @Test
    void testBuilderDefaultBasePath() {
        DropboxClassSource source = DropboxClassSource.builder()
                .accessToken("token")
                .build();

        assertNotNull(source);
    }

    @Test
    void testBuilderDefaultClientIdentifier() {
        DropboxClassSource source = DropboxClassSource.builder()
                .accessToken("token")
                .build();

        assertNotNull(source);
    }

    @Test
    void testMultipleInstances() {
        DropboxClassSource source1 = DropboxClassSource.builder()
                .accessToken("token1")
                .basePath("path1")
                .build();

        DropboxClassSource source2 = DropboxClassSource.builder()
                .accessToken("token2")
                .basePath("path2")
                .build();

        assertNotEquals(source1.getDescription(), source2.getDescription());
    }

    @Test
    void testBuilderWithAllOptions() {
        DropboxClassSource source = DropboxClassSource.builder()
                .accessToken("my-token")
                .basePath("/app/classes")
                .clientIdentifier("CustomClient")
                .build();

        assertNotNull(source);
        assertTrue(source.getDescription().contains("basePath=/app/classes"));
    }

    @Test
    void testBuilderWithNullBasePath() {
        DropboxClassSource source = DropboxClassSource.builder()
                .accessToken("token")
                .basePath(null)
                .build();

        assertNotNull(source);
    }

    @Test
    void testBuilderWithEmptyBasePath() {
        DropboxClassSource source = DropboxClassSource.builder()
                .accessToken("token")
                .basePath("")
                .build();

        assertNotNull(source);
    }

    @Test
    void testBuilderWithLeadingSlashBasePath() {
        DropboxClassSource source = DropboxClassSource.builder()
                .accessToken("token")
                .basePath("/classes")
                .build();

        assertTrue(source.getDescription().contains("basePath=/classes"));
    }

    @Test
    void testBuilderWithTrailingSlashBasePath() {
        DropboxClassSource source = DropboxClassSource.builder()
                .accessToken("token")
                .basePath("classes/")
                .build();

        assertTrue(source.getDescription().contains("basePath=classes/"));
    }
}
