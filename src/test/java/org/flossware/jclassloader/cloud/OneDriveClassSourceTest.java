package org.flossware.jclassloader.cloud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Tests for OneDriveClassSource builder and configuration.
 */
class OneDriveClassSourceTest {

    @Test
    void testBuilderWithAccessToken() {
        OneDriveClassSource source = OneDriveClassSource.builder()
                .accessToken("test-token")
                .build();

        assertNotNull(source);
        assertTrue(source.getDescription().contains("OneDriveClassSource"));
    }

    @Test
    void testBuilderWithBasePath() {
        OneDriveClassSource source = OneDriveClassSource.builder()
                .accessToken("test-token")
                .basePath("classes/")
                .build();

        assertTrue(source.getDescription().contains("basePath=classes/"));
    }

    @Test
    void testBuilderWithDriveId() {
        OneDriveClassSource source = OneDriveClassSource.builder()
                .accessToken("test-token")
                .driveId("my-drive-id")
                .build();

        assertTrue(source.getDescription().contains("drive=my-drive-id"));
    }

    @Test
    void testBuilderNullAccessTokenThrowsException() {
        assertThrows(NullPointerException.class, () -> {
            OneDriveClassSource.builder()
                    .basePath("classes/")
                    .build();
        });
    }

    @Test
    void testGetDescription() {
        OneDriveClassSource source = OneDriveClassSource.builder()
                .accessToken("token")
                .basePath("app/classes")
                .build();

        String description = source.getDescription();
        assertTrue(description.contains("OneDriveClassSource"));
        assertTrue(description.contains("basePath=app/classes"));
    }

    @Test
    void testBuilderChaining() {
        OneDriveClassSource.Builder builder = OneDriveClassSource.builder();
        assertSame(builder, builder.accessToken("token"));
        assertSame(builder, builder.basePath("path"));
        assertSame(builder, builder.driveId("drive"));
    }

    @Test
    void testBuilderDefaultBasePath() {
        OneDriveClassSource source = OneDriveClassSource.builder()
                .accessToken("token")
                .build();

        assertTrue(source.getDescription().contains("basePath="));
    }

    @Test
    void testBuilderDefaultDrive() {
        OneDriveClassSource source = OneDriveClassSource.builder()
                .accessToken("token")
                .build();

        assertTrue(source.getDescription().contains("drive=default"));
    }

    @Test
    void testMultipleInstances() {
        OneDriveClassSource source1 = OneDriveClassSource.builder()
                .accessToken("token1")
                .basePath("path1")
                .build();

        OneDriveClassSource source2 = OneDriveClassSource.builder()
                .accessToken("token2")
                .basePath("path2")
                .build();

        assertNotEquals(source1.getDescription(), source2.getDescription());
    }

    @Test
    void testBuilderWithAllOptions() {
        OneDriveClassSource source = OneDriveClassSource.builder()
                .accessToken("my-token")
                .basePath("/app/classes")
                .driveId("custom-drive")
                .build();

        assertNotNull(source);
        assertTrue(source.getDescription().contains("basePath=/app/classes"));
        assertTrue(source.getDescription().contains("drive=custom-drive"));
    }

    @Test
    void testBuilderWithNullBasePath() {
        OneDriveClassSource source = OneDriveClassSource.builder()
                .accessToken("token")
                .basePath(null)
                .build();

        assertNotNull(source);
    }

    @Test
    void testBuilderWithEmptyBasePath() {
        OneDriveClassSource source = OneDriveClassSource.builder()
                .accessToken("token")
                .basePath("")
                .build();

        assertNotNull(source);
    }
}
