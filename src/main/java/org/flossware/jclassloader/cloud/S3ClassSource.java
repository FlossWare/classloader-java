package org.flossware.jclassloader.cloud;

import org.flossware.jclassloader.ClassSource;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Objects;

/**
 * ClassSource implementation for loading classes from AWS S3.
 * Supports IAM role authentication, access key authentication, and regional buckets.
 * Requires the AWS SDK for Java 2.x dependency.
 */
public class S3ClassSource implements ClassSource {
    private final S3Client s3Client;
    private final String bucketName;
    private final String prefix;

    private S3ClassSource(S3Client s3Client, String bucketName, String prefix) {
        this.s3Client = Objects.requireNonNull(s3Client, "s3Client cannot be null");
        this.bucketName = Objects.requireNonNull(bucketName, "bucketName cannot be null");
        this.prefix = prefix != null ? prefix : "";
    }

    @Override
    public byte[] loadClassData(String className) throws IOException {
        String key = buildKey(className);

        try {
            GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

            try (ResponseInputStream<GetObjectResponse> response = s3Client.getObject(request);
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {

                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = response.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }

                return out.toByteArray();
            }
        } catch (Exception e) {
            throw new IOException("Failed to load class from S3: " + key, e);
        }
    }

    @Override
    public boolean canLoad(String className) {
        String key = buildKey(className);

        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

            s3Client.headObject(request);
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getDescription() {
        return "S3ClassSource[bucket=" + bucketName + ", prefix=" + prefix + "]";
    }

    private String buildKey(String className) {
        String classPath = className.replace('.', '/') + ".class";
        if (prefix.isEmpty()) {
            return classPath;
        }
        return prefix + (prefix.endsWith("/") ? "" : "/") + classPath;
    }

    public void close() {
        if (s3Client != null) {
            s3Client.close();
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Region region = Region.US_EAST_1;
        private String bucketName;
        private String prefix;
        private AwsCredentialsProvider credentialsProvider;
        private String accessKeyId;
        private String secretAccessKey;

        public Builder region(Region region) {
            this.region = region;
            return this;
        }

        public Builder region(String regionName) {
            this.region = Region.of(regionName);
            return this;
        }

        public Builder bucket(String bucketName) {
            this.bucketName = bucketName;
            return this;
        }

        public Builder prefix(String prefix) {
            this.prefix = prefix;
            return this;
        }

        public Builder credentials(String accessKeyId, String secretAccessKey) {
            this.accessKeyId = accessKeyId;
            this.secretAccessKey = secretAccessKey;
            return this;
        }

        public Builder credentialsProvider(AwsCredentialsProvider provider) {
            this.credentialsProvider = provider;
            return this;
        }

        public S3ClassSource build() {
            Objects.requireNonNull(bucketName, "bucketName must be set");

            AwsCredentialsProvider provider;
            if (credentialsProvider != null) {
                provider = credentialsProvider;
            } else if (accessKeyId != null && secretAccessKey != null) {
                provider = StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKeyId, secretAccessKey)
                );
            } else {
                provider = DefaultCredentialsProvider.create();
            }

            S3Client client = S3Client.builder()
                .region(region)
                .credentialsProvider(provider)
                .build();

            return new S3ClassSource(client, bucketName, prefix);
        }
    }
}
