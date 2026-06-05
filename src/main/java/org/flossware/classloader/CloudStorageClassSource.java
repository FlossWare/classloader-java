package org.flossware.classloader;

import org.flossware.cloud.storage.CloudStorageClient;
import org.flossware.classloader.util.ClassNameUtil;

import java.io.IOException;
import java.util.Objects;

/**
 * ClassSource implementation that wraps a CloudStorageClient.
 * This adapter allows any cloud storage provider (S3, Azure, GCS, Google Drive, Dropbox, OneDrive)
 * to be used as a class source by converting class names to file paths.
 *
 * <p>Requires the jcloudstorage library and the provider-specific SDK.</p>
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
 * ApplicationClassLoader loader = ApplicationClassLoader.builder()
 *     .addClassSource(new CloudStorageClassSource(s3))
 *     .build();
 *
 * // Azure Blob Storage
 * CloudStorageClient azure = AzureBlobCloudStorageClient.builder()
 *     .connectionString("DefaultEndpointsProtocol=https;...")
 *     .containerName("classes")
 *     .build();
 *
 * ApplicationClassLoader loader = ApplicationClassLoader.builder()
 *     .addClassSource(new CloudStorageClassSource(azure))
 *     .build();
 * }</pre>
 *
 * <p>The CloudStorageClassSource will be automatically closed when the ApplicationClassLoader is closed
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

    /** {@inheritDoc} */
    @Override
    public byte[] loadClassData(String className) throws IOException {
        Objects.requireNonNull(className, "className cannot be null");
        String path = classNameToPath(className);
        return client.readFile(path);
    }

    /** {@inheritDoc} */
    @Override
    public boolean canLoad(String className) {
        Objects.requireNonNull(className, "className cannot be null");
        try {
            String path = classNameToPath(className);
            return client.exists(path);
        } catch (IOException e) {
            return false;
        }
    }

    /** {@inheritDoc} */
    @Override
    public String getDescription() {
        return "CloudStorageClassSource[" + client.getDescription() + "]";
    }

    /**
     * Closes the underlying cloud storage client and releases resources.
     *
     * @throws IOException if an I/O error occurs during closing
     */
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
        return ClassNameUtil.toClassFilePath(className);
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
