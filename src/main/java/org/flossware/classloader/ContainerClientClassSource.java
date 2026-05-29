package org.flossware.classloader;

import org.flossware.container.ContainerClient;
import org.flossware.classloader.util.ClassNameUtil;

import java.io.IOException;
import java.util.Objects;

/**
 * ClassSource implementation that wraps a ContainerClient.
 * This adapter allows container systems (Kubernetes, Docker, Hazelcast)
 * to be used as a class source by using resource names and class paths.
 *
 * <p>Requires the jcontainer library and the container system SDK.</p>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // Kubernetes ConfigMap
 * ContainerClient k8s = KubernetesContainerClient.builder()
 *     .namespace("production")
 *     .build();
 *
 * ApplicationClassLoader loader = ApplicationClassLoader.builder()
 *     .addClassSource(new ContainerClientClassSource(k8s, "app-classes"))
 *     .build();
 *
 * // Hazelcast
 * ContainerClient hazelcast = HazelcastContainerClient.builder()
 *     .clusterName("prod")
 *     .addAddress("hazelcast:5701")
 *     .build();
 *
 * ApplicationClassLoader loader = ApplicationClassLoader.builder()
 *     .addClassSource(new ContainerClientClassSource(hazelcast, "class-map"))
 *     .build();
 * }</pre>
 *
 * <p>The ContainerClientClassSource will be automatically closed when the ApplicationClassLoader is closed.</p>
 *
 * @see org.flossware.container.ContainerClient
 * @see org.flossware.container.KubernetesContainerClient
 */
public class ContainerClientClassSource implements ClassSource, AutoCloseable {
    private final ContainerClient client;
    private final String resourceName;

    /**
     * Creates a container client class source.
     *
     * @param client The container client to use
     * @param resourceName The resource name (ConfigMap name, container ID, map name)
     * @throws NullPointerException if client or resourceName is null
     */
    public ContainerClientClassSource(ContainerClient client, String resourceName) {
        this.client = Objects.requireNonNull(client, "client cannot be null");
        this.resourceName = Objects.requireNonNull(resourceName, "resourceName cannot be null");
    }

    @Override
    public byte[] loadClassData(String className) throws IOException {
        Objects.requireNonNull(className, "className cannot be null");
        String key = classNameToKey(className);
        return client.read(resourceName, key);
    }

    @Override
    public boolean canLoad(String className) {
        Objects.requireNonNull(className, "className cannot be null");
        try {
            String key = classNameToKey(className);
            return client.exists(resourceName, key);
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public String getDescription() {
        return "ContainerClientClassSource[" + client.getDescription() + ", resource=" + resourceName + "]";
    }

    @Override
    public void close() throws IOException {
        if (client != null) {
            client.close();
        }
    }

    /**
     * Converts a fully-qualified class name to a container key.
     *
     * @param className The class name (e.g., "com.example.MyClass")
     * @return The container key (e.g., "com/example/MyClass.class")
     */
    private String classNameToKey(String className) {
        return ClassNameUtil.toClassFilePath(className);
    }

    /**
     * Gets the underlying container client.
     *
     * @return The container client
     */
    public ContainerClient getClient() {
        return client;
    }

    /**
     * Gets the resource name used by this class source.
     *
     * @return The resource name
     */
    public String getResourceName() {
        return resourceName;
    }
}
