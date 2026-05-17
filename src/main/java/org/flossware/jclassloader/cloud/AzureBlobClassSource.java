package org.flossware.jclassloader.cloud;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.common.StorageSharedKeyCredential;
import org.flossware.jclassloader.ClassSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Objects;

public class AzureBlobClassSource implements ClassSource {
    private final BlobContainerClient containerClient;
    private final String prefix;

    private AzureBlobClassSource(BlobContainerClient containerClient, String prefix) {
        this.containerClient = Objects.requireNonNull(containerClient, "containerClient cannot be null");
        this.prefix = prefix != null ? prefix : "";
    }

    @Override
    public byte[] loadClassData(String className) throws IOException {
        String blobName = buildBlobName(className);
        BlobClient blobClient = containerClient.getBlobClient(blobName);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            blobClient.downloadStream(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IOException("Failed to load class from Azure Blob: " + blobName, e);
        }
    }

    @Override
    public boolean canLoad(String className) {
        String blobName = buildBlobName(className);
        BlobClient blobClient = containerClient.getBlobClient(blobName);

        try {
            return blobClient.exists();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getDescription() {
        return "AzureBlobClassSource[container=" + containerClient.getBlobContainerName() +
               ", prefix=" + prefix + "]";
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
        private String accountName;
        private String accountKey;
        private String connectionString;
        private String containerName;
        private String prefix;
        private String endpoint;

        public Builder accountName(String accountName) {
            this.accountName = accountName;
            return this;
        }

        public Builder accountKey(String accountKey) {
            this.accountKey = accountKey;
            return this;
        }

        public Builder connectionString(String connectionString) {
            this.connectionString = connectionString;
            return this;
        }

        public Builder container(String containerName) {
            this.containerName = containerName;
            return this;
        }

        public Builder prefix(String prefix) {
            this.prefix = prefix;
            return this;
        }

        public Builder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        public AzureBlobClassSource build() {
            Objects.requireNonNull(containerName, "containerName must be set");

            BlobServiceClient serviceClient;

            if (connectionString != null) {
                serviceClient = new BlobServiceClientBuilder()
                    .connectionString(connectionString)
                    .buildClient();
            } else if (accountName != null && accountKey != null) {
                String endpointUrl = endpoint != null ? endpoint :
                    "https://" + accountName + ".blob.core.windows.net";

                StorageSharedKeyCredential credential =
                    new StorageSharedKeyCredential(accountName, accountKey);

                serviceClient = new BlobServiceClientBuilder()
                    .endpoint(endpointUrl)
                    .credential(credential)
                    .buildClient();
            } else {
                throw new IllegalStateException(
                    "Either connectionString or (accountName + accountKey) must be provided");
            }

            BlobContainerClient containerClient = serviceClient.getBlobContainerClient(containerName);
            return new AzureBlobClassSource(containerClient, prefix);
        }
    }
}
