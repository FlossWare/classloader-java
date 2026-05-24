package org.flossware.jclassloader.cloud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for GcsClassSource builder and configuration.
 */
class GcsClassSourceTest {

    @Test
    void testBuilderWithProjectAndBucket() {
        GcsClassSource source = GcsClassSource.builder()
                .projectId("my-project")
                .bucket("my-bucket")
                .build();

        assertNotNull(source);
        assertTrue(source.getDescription().contains("bucket=my-bucket"));
    }

    @Test
    void testBuilderWithPrefix() {
        GcsClassSource source = GcsClassSource.builder()
                .projectId("my-project")
                .bucket("my-bucket")
                .prefix("classes/")
                .build();

        assertTrue(source.getDescription().contains("prefix=classes/"));
    }

    @Test
    void testBuilderNullBucketThrowsException() {
        assertThrows(NullPointerException.class, () -> {
            GcsClassSource.builder()
                    .projectId("project")
                    .build();
        });
    }

    @Test
    void testGetDescription() {
        GcsClassSource source = GcsClassSource.builder()
                .projectId("test-project")
                .bucket("test-bucket")
                .prefix("app/")
                .build();

        String description = source.getDescription();
        assertTrue(description.contains("GcsClassSource"));
        assertTrue(description.contains("bucket=test-bucket"));
        assertTrue(description.contains("prefix=app/"));
    }

    @Test
    void testBuilderChaining() {
        GcsClassSource.Builder builder = GcsClassSource.builder();
        assertSame(builder, builder.projectId("project"));
        assertSame(builder, builder.bucket("bucket"));
        assertSame(builder, builder.prefix("prefix/"));
    }

    @Test
    void testMultipleInstances() {
        GcsClassSource source1 = GcsClassSource.builder()
                .projectId("project1")
                .bucket("bucket1")
                .build();

        GcsClassSource source2 = GcsClassSource.builder()
                .projectId("project2")
                .bucket("bucket2")
                .build();

        assertNotEquals(source1.getDescription(), source2.getDescription());
    }

    @Test
    void testBuilderWithNullPrefix() {
        GcsClassSource source = GcsClassSource.builder()
                .projectId("project")
                .bucket("bucket")
                .prefix(null)
                .build();

        assertTrue(source.getDescription().contains("prefix="));
    }
}
