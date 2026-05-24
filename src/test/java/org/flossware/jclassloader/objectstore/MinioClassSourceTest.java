package org.flossware.jclassloader.objectstore;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MinioClassSource builder and configuration.
 */
class MinioClassSourceTest {

    @Test
    void testBuilderWithRequiredFields() {
        MinioClassSource source = MinioClassSource.builder()
                .endpoint("https://minio.example.com")
                .accessKey("minioadmin")
                .secretKey("minioadmin")
                .bucket("classes")
                .build();

        assertNotNull(source);
        assertTrue(source.getDescription().contains("bucket=classes"));
    }

    @Test
    void testBuilderWithPrefix() {
        MinioClassSource source = MinioClassSource.builder()
                .endpoint("https://minio.example.com")
                .accessKey("minioadmin")
                .secretKey("minioadmin")
                .bucket("classes")
                .prefix("java/compiled")
                .build();

        assertTrue(source.getDescription().contains("prefix=java/compiled"));
    }

    @Test
    void testBuilderWithRegion() {
        MinioClassSource source = MinioClassSource.builder()
                .endpoint("https://minio.example.com")
                .accessKey("minioadmin")
                .secretKey("minioadmin")
                .bucket("classes")
                .region("us-east-1")
                .build();

        assertNotNull(source);
    }

    @Test
    void testBuilderWithSecureFalse() {
        MinioClassSource source = MinioClassSource.builder()
                .endpoint("http://localhost:9000")
                .accessKey("minioadmin")
                .secretKey("minioadmin")
                .bucket("classes")
                .secure(false)
                .build();

        assertNotNull(source);
    }

    @Test
    void testBuilderMissingEndpointThrowsException() {
        assertThrows(NullPointerException.class, () -> {
            MinioClassSource.builder()
                    .accessKey("minioadmin")
                    .secretKey("minioadmin")
                    .bucket("classes")
                    .build();
        });
    }

    @Test
    void testBuilderMissingAccessKeyThrowsException() {
        assertThrows(NullPointerException.class, () -> {
            MinioClassSource.builder()
                    .endpoint("https://minio.example.com")
                    .secretKey("minioadmin")
                    .bucket("classes")
                    .build();
        });
    }

    @Test
    void testBuilderMissingSecretKeyThrowsException() {
        assertThrows(NullPointerException.class, () -> {
            MinioClassSource.builder()
                    .endpoint("https://minio.example.com")
                    .accessKey("minioadmin")
                    .bucket("classes")
                    .build();
        });
    }

    @Test
    void testBuilderMissingBucketThrowsException() {
        assertThrows(NullPointerException.class, () -> {
            MinioClassSource.builder()
                    .endpoint("https://minio.example.com")
                    .accessKey("minioadmin")
                    .secretKey("minioadmin")
                    .build();
        });
    }

    @Test
    void testGetDescription() {
        MinioClassSource source = MinioClassSource.builder()
                .endpoint("https://minio.example.com")
                .accessKey("minioadmin")
                .secretKey("minioadmin")
                .bucket("my-classes")
                .prefix("classes/")
                .build();

        String description = source.getDescription();
        assertTrue(description.contains("MinioClassSource"));
        assertTrue(description.contains("bucket=my-classes"));
        assertTrue(description.contains("prefix=classes/"));
    }

    @Test
    void testGetDescriptionWithoutPrefix() {
        MinioClassSource source = MinioClassSource.builder()
                .endpoint("https://minio.example.com")
                .accessKey("minioadmin")
                .secretKey("minioadmin")
                .bucket("classes")
                .build();

        String description = source.getDescription();
        assertTrue(description.contains("prefix="));
    }

    @Test
    void testDefaultSecureIsTrue() {
        MinioClassSource source = MinioClassSource.builder()
                .endpoint("https://minio.example.com")
                .accessKey("minioadmin")
                .secretKey("minioadmin")
                .bucket("classes")
                .build();

        assertNotNull(source);
    }

    @Test
    void testDefaultPrefixIsEmpty() {
        MinioClassSource source = MinioClassSource.builder()
                .endpoint("https://minio.example.com")
                .accessKey("minioadmin")
                .secretKey("minioadmin")
                .bucket("classes")
                .build();

        assertTrue(source.getDescription().contains("prefix="));
    }

    @Test
    void testBuilderWithAllOptions() {
        MinioClassSource source = MinioClassSource.builder()
                .endpoint("https://s3.example.com")
                .accessKey("accesskey123")
                .secretKey("secretkey456")
                .bucket("java-classes")
                .prefix("compiled/")
                .region("us-west-2")
                .secure(true)
                .build();

        String description = source.getDescription();
        assertTrue(description.contains("bucket=java-classes"));
        assertTrue(description.contains("prefix=compiled/"));
    }

    @Test
    void testBuilderReturnsBuilder() {
        MinioClassSource.Builder builder = MinioClassSource.builder();
        assertSame(builder, builder.endpoint("https://minio.example.com"));
        assertSame(builder, builder.accessKey("key"));
        assertSame(builder, builder.secretKey("secret"));
        assertSame(builder, builder.bucket("bucket"));
        assertSame(builder, builder.prefix("prefix"));
        assertSame(builder, builder.region("region"));
        assertSame(builder, builder.secure(true));
    }

    @Test
    void testBuilderWithNullPrefix() {
        MinioClassSource source = MinioClassSource.builder()
                .endpoint("https://minio.example.com")
                .accessKey("minioadmin")
                .secretKey("minioadmin")
                .bucket("classes")
                .prefix(null)
                .build();

        assertTrue(source.getDescription().contains("prefix="));
    }

    @Test
    void testBuilderWithEmptyPrefix() {
        MinioClassSource source = MinioClassSource.builder()
                .endpoint("https://minio.example.com")
                .accessKey("minioadmin")
                .secretKey("minioadmin")
                .bucket("classes")
                .prefix("")
                .build();

        assertTrue(source.getDescription().contains("prefix="));
    }

    @Test
    void testMultipleInstances() {
        MinioClassSource source1 = MinioClassSource.builder()
                .endpoint("https://minio1.example.com")
                .accessKey("admin1")
                .secretKey("secret1")
                .bucket("bucket1")
                .build();

        MinioClassSource source2 = MinioClassSource.builder()
                .endpoint("https://minio2.example.com")
                .accessKey("admin2")
                .secretKey("secret2")
                .bucket("bucket2")
                .build();

        assertNotEquals(source1.getDescription(), source2.getDescription());
    }

    @Test
    void testBuilderWithLocalhost() {
        MinioClassSource source = MinioClassSource.builder()
                .endpoint("http://localhost:9000")
                .accessKey("minioadmin")
                .secretKey("minioadmin")
                .bucket("classes")
                .secure(false)
                .build();

        assertNotNull(source);
    }

    @Test
    void testBuilderWithS3CompatibleService() {
        MinioClassSource source = MinioClassSource.builder()
                .endpoint("https://s3.amazonaws.com")
                .accessKey("AKIAIOSFODNN7EXAMPLE")
                .secretKey("wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY")
                .bucket("my-bucket")
                .region("us-east-1")
                .build();

        assertNotNull(source);
    }
}
