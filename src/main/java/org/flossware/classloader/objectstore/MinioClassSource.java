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

    /**
     * {@inheritDoc}
     *
     * <p>Downloads the class file from MinIO. Validates object size before
     * downloading to prevent out-of-memory errors.</p>
     */
    @Override
    public byte[] loadClassData(String className) throws IOException {
        Objects.requireNonNull(className, "className cannot be null");
        String objectName = buildObjectName(className);

        // Check size first to prevent OOM
        long size = getAndValidateObjectSize(objectName);

        // Safe to download - size is within limits
        return downloadClassData(objectName, size);
    }

    private long getAndValidateObjectSize(String objectName) throws IOException {
        StatObjectResponse stat = getObjectStats(objectName);
        long size = stat.size();

        if (size > maxClassSize) {
            throw new IOException("Class file too large: " + size + " bytes (max " + maxClassSize + ")");
        }
        if (size > Integer.MAX_VALUE) {
            throw new IOException("Class file exceeds Java array limit: " + size);
        }

        return size;
    }

    private StatObjectResponse getObjectStats(String objectName) throws IOException {
        try {
            return minioClient.statObject(
                StatObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .build());
        } catch (MinioException e) {
            throw new IOException("MinIO error getting object stats: " + objectName, e);
        } catch (IOException e) {
            throw new IOException("IO error getting object stats: " + objectName, e);
        }
    }

    private byte[] downloadClassData(String objectName, long size) throws IOException {
        try (InputStream stream = minioClient.getObject(
                GetObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .build())) {

            byte[] data = new byte[(int)size];
            readFully(stream, data, (int)size);
            return data;

        } catch (MinioException e) {
            throw new IOException("MinIO error loading class: " + objectName, e);
        } catch (IOException e) {
            throw new IOException("IO error loading class: " + objectName, e);
        }
    }

    private void readFully(InputStream stream, byte[] data, int size) throws IOException {
        Objects.requireNonNull(stream, "stream cannot be null");
        Objects.requireNonNull(data, "data cannot be null");
        int totalRead = readBytesFromStream(stream, data, size);
        validateBytesRead(size, totalRead);
    }

    private int readBytesFromStream(InputStream stream, byte[] data, int size) throws IOException {
        int totalRead = 0;
        while (totalRead < size) {
            int n = stream.read(data, totalRead, size - totalRead);
            if (n == -1) {
                return totalRead;
            }
            totalRead += n;
        }
        return totalRead;
    }

    private void validateBytesRead(int expected, int actual) throws IOException {
        if (actual != expected) {
            throw new IOException("Expected " + expected + " bytes but read " + actual);
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean canLoad(String className) {
        Objects.requireNonNull(className, "className cannot be null");
        String objectName = buildObjectName(className);

        try {
            minioClient.statObject(
                StatObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .build()
            );
            return true;
        } catch (MinioException | IOException e) {
            // Object doesn't exist, auth failure, or other error
            return false;
        }
    }

    /** {@inheritDoc} */
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

    /**
     * Creates a new Builder for constructing MinioClassSource instances.
     *
     * @return A new Builder with default configuration
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for constructing MinioClassSource instances with fluent API.
     *
     * <p>Configures MinIO object storage connection for class loading.</p>
     *
     * <p><b>Basic Example:</b></p>
     * <pre>{@code
     * MinioClassSource source = MinioClassSource.builder()
     *     .endpoint("minio.example.com")
     *     .accessKey("minioadmin")
     *     .secretKey("minioadmin")
     *     .bucket("classes")
     *     .build();
     * }</pre>
     *
     * <p><b>Advanced Example with Prefix and Custom Port:</b></p>
     * <pre>{@code
     * MinioClassSource source = MinioClassSource.builder()
     *     .endpoint("localhost")
     *     .port(9000)              // Custom port
     *     .secure(false)            // HTTP instead of HTTPS
     *     .accessKey("mykey")
     *     .secretKey("mysecret")
     *     .bucket("app-classes")
     *     .prefix("production/v2")  // Object key prefix
     *     .region("us-east-1")
     *     .maxClassSize(20 * 1024 * 1024)  // 20MB limit
     *     .build();
     * }</pre>
     *
     * <p><b>S3-Compatible Services:</b></p>
     * <p>Works with any S3-compatible service:</p>
     * <ul>
     *   <li>MinIO</li>
     *   <li>Amazon S3</li>
     *   <li>Backblaze B2</li>
     *   <li>Cloudflare R2</li>
     *   <li>DigitalOcean Spaces</li>
     * </ul>
     *
     * <p><b>Defaults:</b></p>
     * <ul>
     *   <li>secure: true (HTTPS)</li>
     *   <li>port: 443 (HTTPS) or 9000 (HTTP if secure=false)</li>
     *   <li>maxClassSize: 10MB</li>
     *   <li>prefix: "" (bucket root)</li>
     *   <li>region: null (auto-detect)</li>
     * </ul>
     */
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

        /**
         * Sets the MinIO/S3 endpoint hostname.
         *
         * <p>Examples:</p>
         * <ul>
         *   <li>MinIO: "minio.example.com" or "localhost"</li>
         *   <li>Amazon S3: "s3.amazonaws.com"</li>
         *   <li>Backblaze B2: "s3.us-west-002.backblazeb2.com"</li>
         *   <li>Cloudflare R2: "ACCOUNT_ID.r2.cloudflarestorage.com"</li>
         * </ul>
         *
         * @param endpoint Hostname (without http:// or https://)
         * @return this builder
         * @throws NullPointerException if endpoint is null
         */
        public Builder endpoint(String endpoint) {
            this.endpoint = Objects.requireNonNull(endpoint, "endpoint cannot be null");
            return this;
        }

        /**
         * Sets the access key (username) for authentication.
         *
         * @param accessKey Access key ID
         * @return this builder
         * @throws NullPointerException if accessKey is null
         */
        public Builder accessKey(String accessKey) {
            this.accessKey = Objects.requireNonNull(accessKey, "accessKey cannot be null");
            return this;
        }

        /**
         * Sets the secret key (password) for authentication.
         *
         * @param secretKey Secret access key
         * @return this builder
         * @throws NullPointerException if secretKey is null
         */
        public Builder secretKey(String secretKey) {
            this.secretKey = Objects.requireNonNull(secretKey, "secretKey cannot be null");
            return this;
        }

        /**
         * Sets the bucket name containing class files.
         *
         * @param bucketName Bucket name (e.g., "classes", "app-binaries")
         * @return this builder
         * @throws NullPointerException if bucketName is null
         */
        public Builder bucket(String bucketName) {
            this.bucketName = Objects.requireNonNull(bucketName, "bucketName cannot be null");
            return this;
        }

        /**
         * Sets an optional object key prefix.
         *
         * <p>Useful for organizing classes in subdirectories within a bucket.</p>
         *
         * <p>Example: If prefix is "production/v2" and loading class "com.example.MyClass",
         * the object key will be "production/v2/com/example/MyClass.class"</p>
         *
         * @param prefix Object key prefix (null or "" for bucket root)
         * @return this builder
         */
        public Builder prefix(String prefix) {
            this.prefix = prefix;
            return this;
        }

        /**
         * Sets the region (optional, auto-detected if not specified).
         *
         * @param region AWS region code (e.g., "us-east-1", "eu-west-1")
         * @return this builder
         */
        public Builder region(String region) {
            this.region = region;
            return this;
        }

        /**
         * Sets whether to use HTTPS (true) or HTTP (false).
         *
         * <p>Automatically adjusts port: 443 for HTTPS, 9000 for HTTP (unless explicitly set).</p>
         *
         * @param secure true for HTTPS (default), false for HTTP
         * @return this builder
         */
        public Builder secure(boolean secure) {
            this.secure = secure;
            // Adjust default port based on secure setting
            if (!secure && this.port == 443) {
                this.port = 9000;  // Default MinIO port for HTTP
            }
            return this;
        }

        /**
         * Sets a custom port number.
         *
         * <p>Common ports:</p>
         * <ul>
         *   <li>443: HTTPS (default)</li>
         *   <li>9000: MinIO HTTP (default when secure=false)</li>
         *   <li>9001: MinIO Console</li>
         * </ul>
         *
         * @param port Port number (1-65535)
         * @return this builder
         * @throws IllegalArgumentException if port is outside valid range
         */
        public Builder port(int port) {
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("Port must be 1-65535");
            }
            this.port = port;
            return this;
        }

        /**
         * Sets the maximum allowed class file size.
         *
         * <p>Prevents OOM attacks by rejecting files larger than this limit.</p>
         *
         * @param maxBytes Maximum size in bytes (default: 10MB)
         * @return this builder
         * @throws IllegalArgumentException if maxBytes <= 0
         */
        public Builder maxClassSize(long maxBytes) {
            if (maxBytes <= 0) {
                throw new IllegalArgumentException("maxClassSize must be positive");
            }
            this.maxClassSize = maxBytes;
            return this;
        }

        /**
         * Builds the MinioClassSource with configured settings.
         *
         * @return A new MinioClassSource instance
         * @throws NullPointerException if endpoint, accessKey, secretKey, or bucketName not set
         */
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
