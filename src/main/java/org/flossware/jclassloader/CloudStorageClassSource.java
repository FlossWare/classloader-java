package org.flossware.jclassloader;

import org.flossware.cloud.storage.CloudStorageClient;

import java.io.IOException;
import java.util.Objects;

/**
 * ClassSource implementation that wraps a CloudStorageClient.
 * This adapter allows any cloud storage provider (S3, Azure, GCS, Google Drive, Dropbox, OneDrive)
 * to be used as a class source by converting class names to file paths.
 *
 * <p>Requires the cloud-storage-client library and the provider-specific SDK.</p>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // AWS S3
 * CloudStorageClient s3 = S3CloudStorageClient.builder()
 *     .bucket("my-classes-bucket")
 *     .region(Region.US_EAST_1)
 *     .prefix("production/classes/")
 *     .build();
 *
 * JClassLoader loader = JClassLoader.builder()
 *     .addClassSource(new CloudStorageClassSource(s3))
 *     .build();
 *
 * // Azure Blob Storage
 * CloudStorageClient azure = AzureBlobCloudStorageClient.builder()
 *     .connectionString("DefaultEndpointsProtocol=https;...")
 *     .containerName("classes")
 *     .build();
 *
 * JClassLoader loader = JClassLoader.builder()
 *     .addClassSource(new CloudStorageClassSource(azure))
 *     .build();
 * }</pre>
 *
 * <p>The CloudStorageClassSource will be automatically closed when the JClassLoader is closed
 * (assuming the CloudStorageClient implements AutoCloseable).</p>
 *
 * @see org.flossware.cloud.storage.CloudStorageClient
 * @see org.flossware.cloud.storage.S3CloudStorageClient
 */
public class CloudStorageClassSource implements ClassSource, AutoCloseable {
    private final CloudStorageClient client;

    /**
     * Creates a cloud storage class source.
     *
     * @param client The cloud storage client to use
     * @throws NullPointerException if client is null
     */
    public CloudStorageClassSource(CloudStorageClient client) {
        this.client = Objects.requireNonNull(client, "client cannot be null");
    }

    @Override
    public byte[] loadClassData(String className) throws IOException {
        String path = classNameToPath(className);
        return client.readFile(path);
    }

    @Override
    public boolean canLoad(String className) {
        try {
            String path = classNameToPath(className);
            return client.exists(path);
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public String getDescription() {
        return "CloudStorageClassSource[" + client.getDescription() + "]";
    }

    @Override
    public void close() throws IOException {
        if (client != null) {
            client.close();
        }
    }

    /**
     * Converts a fully-qualified class name to a file path.
     *
     * @param className The class name (e.g., "com.example.MyClass")
     * @return The file path (e.g., "com/example/MyClass.class")
     */
    private String classNameToPath(String className) {
        return className.replace('.', '/') + ".class";
    }

    /**
     * Gets the underlying cloud storage client.
     *
     * @return The cloud storage client
     */
    public CloudStorageClient getClient() {
        return client;
    }
}
