package org.flossware.classloader.objectstore;

import io.minio.GetObjectArgs;

import static org.flossware.classloader.util.ClassLoaderConstants.DEFAULT_BUFFER_SIZE;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.MinioException;
import org.flossware.classloader.ClassSource;
import org.flossware.classloader.util.ClassNameUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

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
    private static final long MAX_CLASS_SIZE = 10 * 1024 * 1024; // 10MB default

    private final MinioClient minioClient;
    private final String bucketName;
    private final String prefix;
    private final long maxClassSize;

    private MinioClassSource(MinioClient minioClient, String bucketName, String prefix, long maxClassSize) {
        this.minioClient = Objects.requireNonNull(minioClient, "minioClient cannot be null");
        this.bucketName = Objects.requireNonNull(bucketName, "bucketName cannot be null");
        this.prefix = prefix != null ? prefix : "";
        this.maxClassSize = maxClassSize;
    }

    @Override
    public byte[] loadClassData(String className) throws IOException {
        String objectName = buildObjectName(className);

        // Check size first to prevent OOM
        StatObjectResponse stat;
        try {
            stat = minioClient.statObject(
                StatObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .build());
        } catch (MinioException e) {
            throw new IOException("MinIO error getting object stats: " + objectName, e);
        } catch (Exception e) {
            throw new IOException("Unexpected error getting object stats: " + objectName, e);
        }

        long size = stat.size();
        if (size > maxClassSize) {
            throw new IOException("Class file too large: " + size + " bytes (max " + maxClassSize + ")");
        }
        if (size > Integer.MAX_VALUE) {
            throw new IOException("Class file exceeds Java array limit: " + size);
        }

        // Safe to download - size is within limits
        try (InputStream stream = minioClient.getObject(
                GetObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .build())) {

            byte[] data = new byte[(int)size];
            int totalRead = 0;

            while (totalRead < size) {
                int n = stream.read(data, totalRead, (int)size - totalRead);
                if (n == -1) break;
                totalRead += n;
            }

            return data;

        } catch (MinioException e) {
            throw new IOException("MinIO error loading class: " + objectName, e);
        } catch (Exception e) {
            throw new IOException("Unexpected error loading class: " + objectName, e);
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
            // Object doesn't exist, auth failure, or other error
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
        private int port = 443;  // Default HTTPS port
        private long maxClassSize = MAX_CLASS_SIZE;

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
            // Adjust default port based on secure setting
            if (!secure && this.port == 443) {
                this.port = 9000;  // Default MinIO port for HTTP
            }
            return this;
        }

        public Builder port(int port) {
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("Port must be 1-65535");
            }
            this.port = port;
            return this;
        }

        public Builder maxClassSize(long maxBytes) {
            if (maxBytes <= 0) {
                throw new IllegalArgumentException("maxClassSize must be positive");
            }
            this.maxClassSize = maxBytes;
            return this;
        }

        public MinioClassSource build() {
            Objects.requireNonNull(endpoint, "endpoint must be set");
            Objects.requireNonNull(accessKey, "accessKey must be set");
            Objects.requireNonNull(secretKey, "secretKey must be set");
            Objects.requireNonNull(bucketName, "bucketName must be set");

            MinioClient.Builder clientBuilder = MinioClient.builder()
                .endpoint(endpoint, port, secure)
                .credentials(accessKey, secretKey);

            // Note: Timeout configuration depends on MinIO SDK version
            // Older versions don't support timeout methods directly
            // Configure timeouts via OkHttpClient if needed

            if (region != null) {
                clientBuilder.region(region);
            }

            MinioClient client = clientBuilder.build();
            return new MinioClassSource(client, bucketName, prefix, maxClassSize);
        }
    }
}
