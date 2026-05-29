package org.flossware.classloader.objectstore;

import io.minio.GetObjectArgs;

import static org.flossware.classloader.util.ClassLoaderConstants.DEFAULT_BUFFER_SIZE;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import org.flossware.classloader.ClassSource;
import org.flossware.classloader.util.ClassNameUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * ClassSource implementation for loading classes from MinIO object storage.
 * MinIO is an S3-compatible object storage system.
 * Also compatible with other S3-compatible services like Backblaze B2, Cloudflare R2, etc.
 * Requires the MinIO SDK dependency.
 *
 * <p>This class implements AutoCloseable for consistency with other ClassSource implementations
 * and to support try-with-resources patterns. Note that MinioClient manages its own connection
 * pool lifecycle internally, so the close() method is provided for API consistency but does not
 * perform explicit cleanup.</p>
 */
public class MinioClassSource implements ClassSource, AutoCloseable {
    private final MinioClient minioClient;
    private final String bucketName;
    private final String prefix;

    private MinioClassSource(MinioClient minioClient, String bucketName, String prefix) {
        this.minioClient = Objects.requireNonNull(minioClient, "minioClient cannot be null");
        this.bucketName = Objects.requireNonNull(bucketName, "bucketName cannot be null");
        this.prefix = prefix != null ? prefix : "";
    }

    @Override
    public byte[] loadClassData(String className) throws IOException {
        String objectName = buildObjectName(className);

        try (InputStream stream = minioClient.getObject(
                GetObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .build());
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = stream.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }

            return out.toByteArray();

        } catch (Exception e) {
            throw new IOException("Failed to load class from MinIO: " + objectName, e);
        }
    }

    @Override
    public boolean canLoad(String className) {
        String objectName = buildObjectName(className);

        try {
            minioClient.statObject(
                StatObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .build()
            );
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getDescription() {
        return "MinioClassSource[bucket=" + bucketName + ", prefix=" + prefix + "]";
    }

    /**
     * Closes this MinioClassSource.
     *
     * <p>Note: MinioClient manages its own HTTP connection pool lifecycle internally.
     * This method is provided for API consistency with other ClassSource implementations
     * that implement AutoCloseable, but does not perform explicit cleanup.
     * The underlying connection pool will be cleaned up by the MinioClient when the
     * JVM shuts down or when garbage collection occurs.</p>
     */
    @Override
    public void close() throws IOException {
        // MinioClient does not provide a close() method in the current SDK
        // Connection pools are managed internally and cleaned up on JVM shutdown
    }

    private String buildObjectName(String className) {
        String classPath = ClassNameUtil.toClassFilePath(className);

        if (prefix.isEmpty()) {
            return classPath;
        }

        return prefix + (prefix.endsWith("/") ? "" : "/") + classPath;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String endpoint;
        private String accessKey;
        private String secretKey;
        private String bucketName;
        private String prefix;
        private String region;
        private boolean secure = true;

        public Builder endpoint(String endpoint) {
            this.endpoint = Objects.requireNonNull(endpoint, "endpoint cannot be null");
            return this;
        }

        public Builder accessKey(String accessKey) {
            this.accessKey = Objects.requireNonNull(accessKey, "accessKey cannot be null");
            return this;
        }

        public Builder secretKey(String secretKey) {
            this.secretKey = Objects.requireNonNull(secretKey, "secretKey cannot be null");
            return this;
        }

        public Builder bucket(String bucketName) {
            this.bucketName = Objects.requireNonNull(bucketName, "bucketName cannot be null");
            return this;
        }

        public Builder prefix(String prefix) {
            this.prefix = prefix;
            return this;
        }

        public Builder region(String region) {
            this.region = region;
            return this;
        }

        public Builder secure(boolean secure) {
            this.secure = secure;
            return this;
        }

        public MinioClassSource build() {
            Objects.requireNonNull(endpoint, "endpoint must be set");
            Objects.requireNonNull(accessKey, "accessKey must be set");
            Objects.requireNonNull(secretKey, "secretKey must be set");
            Objects.requireNonNull(bucketName, "bucketName must be set");

            MinioClient.Builder clientBuilder = MinioClient.builder()
                .endpoint(endpoint, 443, secure)
                .credentials(accessKey, secretKey);

            if (region != null) {
                clientBuilder.region(region);
            }

            MinioClient client = clientBuilder.build();
            return new MinioClassSource(client, bucketName, prefix);
        }
    }
}
