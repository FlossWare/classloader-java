package org.flossware.jclassloader.cloud;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.flossware.jclassloader.ClassSource;

import java.io.IOException;
import java.util.Objects;

public class GcsClassSource implements ClassSource {
    private final Storage storage;
    private final String bucketName;
    private final String prefix;

    private GcsClassSource(Storage storage, String bucketName, String prefix) {
        this.storage = Objects.requireNonNull(storage, "storage cannot be null");
        this.bucketName = Objects.requireNonNull(bucketName, "bucketName cannot be null");
        this.prefix = prefix != null ? prefix : "";
    }

    @Override
    public byte[] loadClassData(String className) throws IOException {
        String blobName = buildBlobName(className);
        BlobId blobId = BlobId.of(bucketName, blobName);
        Blob blob = storage.get(blobId);

        if (blob == null) {
            throw new IOException("Class not found in GCS: " + blobName);
        }

        try {
            return blob.getContent();
        } catch (Exception e) {
            throw new IOException("Failed to load class from GCS: " + blobName, e);
        }
    }

    @Override
    public boolean canLoad(String className) {
        String blobName = buildBlobName(className);
        BlobId blobId = BlobId.of(bucketName, blobName);

        try {
            Blob blob = storage.get(blobId);
            return blob != null && blob.exists();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getDescription() {
        return "GcsClassSource[bucket=" + bucketName + ", prefix=" + prefix + "]";
    }

    private String buildBlobName(String className) {
        String classPath = className.replace('.', '/') + ".class";
        if (prefix.isEmpty()) {
            return classPath;
        }
        return prefix + (prefix.endsWith("/") ? "" : "/") + classPath;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String projectId;
        private String bucketName;
        private String prefix;
        private Storage storage;

        public Builder projectId(String projectId) {
            this.projectId = projectId;
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

        public Builder storage(Storage storage) {
            this.storage = storage;
            return this;
        }

        public GcsClassSource build() {
            Objects.requireNonNull(bucketName, "bucketName must be set");

            Storage storageClient;
            if (storage != null) {
                storageClient = storage;
            } else if (projectId != null) {
                storageClient = StorageOptions.newBuilder()
                    .setProjectId(projectId)
                    .build()
                    .getService();
            } else {
                storageClient = StorageOptions.getDefaultInstance().getService();
            }

            return new GcsClassSource(storageClient, bucketName, prefix);
        }
    }
}
