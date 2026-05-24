package org.flossware.jclassloader;

import org.flossware.messaging.MessageClient;
import org.flossware.jclassloader.util.ClassNameUtil;

import java.io.IOException;
import java.util.Objects;

/**
 * ClassSource implementation that wraps a MessageClient.
 * This adapter allows messaging systems (Kafka, RabbitMQ, Redis)
 * to be used as a class source by using class names as message keys.
 *
 * <p>Requires the jmessaging library and the messaging system SDK.</p>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // Kafka
 * MessageClient kafka = KafkaMessageClient.builder()
 *     .bootstrapServers("kafka:9092")
 *     .topic("class-definitions")
 *     .build();
 *
 * JClassLoader loader = JClassLoader.builder()
 *     .addClassSource(new MessageClientClassSource(kafka))
 *     .build();
 *
 * // Redis
 * MessageClient redis = RedisMessageClient.builder()
 *     .host("redis.example.com")
 *     .port(6379)
 *     .keyPrefix("classes:")
 *     .build();
 *
 * JClassLoader loader = JClassLoader.builder()
 *     .addClassSource(new MessageClientClassSource(redis))
 *     .build();
 * }</pre>
 *
 * <p>The MessageClientClassSource will be automatically closed when the JClassLoader is closed.</p>
 *
 * @see org.flossware.messaging.MessageClient
 * @see org.flossware.messaging.KafkaMessageClient
 */
public class MessageClientClassSource implements ClassSource, AutoCloseable {
    private final MessageClient client;

    /**
     * Creates a message client class source.
     *
     * @param client The message client to use
     * @throws NullPointerException if client is null
     */
    public MessageClientClassSource(MessageClient client) {
        this.client = Objects.requireNonNull(client, "client cannot be null");
    }

    @Override
    public byte[] loadClassData(String className) throws IOException {
        String key = classNameToKey(className);
        return client.read(key);
    }

    @Override
    public boolean canLoad(String className) {
        try {
            String key = classNameToKey(className);
            return client.exists(key);
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public String getDescription() {
        return "MessageClientClassSource[" + client.getDescription() + "]";
    }

    @Override
    public void close() throws IOException {
        if (client != null) {
            client.close();
        }
    }

    /**
     * Converts a fully-qualified class name to a message key.
     *
     * @param className The class name (e.g., "com.example.MyClass")
     * @return The message key (e.g., "com/example/MyClass.class")
     */
    private String classNameToKey(String className) {
        return ClassNameUtil.toClassFilePath(className);
    }

    /**
     * Gets the underlying message client.
     *
     * @return The message client
     */
    public MessageClient getClient() {
        return client;
    }
}
