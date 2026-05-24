package org.flossware.jclassloader.cloud;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.regions.Region;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for S3ClassSource builder and configuration.
 */
class S3ClassSourceTest {

    @Test
    void testBuilderWithBucket() {
        S3ClassSource source = S3ClassSource.builder()
                .bucket("my-bucket")
                .build();

        assertNotNull(source);
        assertTrue(source.getDescription().contains("bucket=my-bucket"));
    }

    @Test
    void testBuilderWithPrefix() {
        S3ClassSource source = S3ClassSource.builder()
                .bucket("my-bucket")
                .prefix("classes/")
                .build();

        assertTrue(source.getDescription().contains("prefix=classes/"));
    }

    @Test
    void testBuilderWithRegion() {
        S3ClassSource source = S3ClassSource.builder()
                .bucket("my-bucket")
                .region(Region.US_WEST_2)
                .build();

        assertNotNull(source);
    }

    @Test
    void testBuilderWithRegionString() {
        S3ClassSource source = S3ClassSource.builder()
                .bucket("my-bucket")
                .region("us-east-1")
                .build();

        assertNotNull(source);
    }

    @Test
    void testBuilderWithCredentials() {
        S3ClassSource source = S3ClassSource.builder()
                .bucket("my-bucket")
                .credentials("AKIAIOSFODNN7EXAMPLE", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY")
                .build();

        assertNotNull(source);
    }

    @Test
    void testBuilderNullBucketThrowsException() {
        assertThrows(NullPointerException.class, () -> {
            S3ClassSource.builder().build();
        });
    }

    @Test
    void testGetDescription() {
        S3ClassSource source = S3ClassSource.builder()
                .bucket("test-bucket")
                .prefix("classes/")
                .build();

        String description = source.getDescription();
        assertTrue(description.contains("S3ClassSource"));
        assertTrue(description.contains("bucket=test-bucket"));
        assertTrue(description.contains("prefix=classes/"));
    }

    @Test
    void testGetDescriptionWithoutPrefix() {
        S3ClassSource source = S3ClassSource.builder()
                .bucket("test-bucket")
                .build();

        assertTrue(source.getDescription().contains("prefix="));
    }

    @Test
    void testClose() {
        S3ClassSource source = S3ClassSource.builder()
                .bucket("my-bucket")
                .build();

        assertDoesNotThrow(() -> source.close());
    }

    @Test
    void testMultipleClose() {
        S3ClassSource source = S3ClassSource.builder()
                .bucket("my-bucket")
                .build();

        assertDoesNotThrow(() -> {
            source.close();
            source.close();
        });
    }

    @Test
    void testBuilderDefaultRegion() {
        S3ClassSource source = S3ClassSource.builder()
                .bucket("my-bucket")
                .build();

        assertNotNull(source);
    }

    @Test
    void testBuilderWithAllOptions() {
        S3ClassSource source = S3ClassSource.builder()
                .bucket("production-bucket")
                .prefix("app/classes/")
                .region(Region.EU_WEST_1)
                .credentials("key", "secret")
                .build();

        String description = source.getDescription();
        assertTrue(description.contains("bucket=production-bucket"));
        assertTrue(description.contains("prefix=app/classes/"));
    }

    @Test
    void testBuilderChaining() {
        S3ClassSource.Builder builder = S3ClassSource.builder();
        assertSame(builder, builder.bucket("test"));
        assertSame(builder, builder.prefix("prefix/"));
        assertSame(builder, builder.region(Region.US_EAST_1));
        assertSame(builder, builder.credentials("key", "secret"));
    }

    @Test
    void testMultipleInstances() {
        S3ClassSource source1 = S3ClassSource.builder()
                .bucket("bucket1")
                .build();

        S3ClassSource source2 = S3ClassSource.builder()
                .bucket("bucket2")
                .build();

        assertNotEquals(source1.getDescription(), source2.getDescription());
    }

    @Test
    void testBuilderWithDifferentRegions() {
        S3ClassSource source1 = S3ClassSource.builder()
                .bucket("bucket")
                .region(Region.US_EAST_1)
                .build();

        S3ClassSource source2 = S3ClassSource.builder()
                .bucket("bucket")
                .region(Region.AP_SOUTHEAST_1)
                .build();

        assertNotNull(source1);
        assertNotNull(source2);
    }

    @Test
    void testBuilderWithEmptyPrefix() {
        S3ClassSource source = S3ClassSource.builder()
                .bucket("my-bucket")
                .prefix("")
                .build();

        assertTrue(source.getDescription().contains("prefix="));
    }

    @Test
    void testBuilderWithNullPrefix() {
        S3ClassSource source = S3ClassSource.builder()
                .bucket("my-bucket")
                .prefix(null)
                .build();

        assertTrue(source.getDescription().contains("prefix="));
    }

    @Test
    void testBuilderWithLongPrefix() {
        S3ClassSource source = S3ClassSource.builder()
                .bucket("my-bucket")
                .prefix("very/long/path/to/classes/directory/")
                .build();

        assertTrue(source.getDescription().contains("prefix=very/long/path/to/classes/directory/"));
    }
}
