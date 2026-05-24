package org.flossware.jclassloader.messaging;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.flossware.jclassloader.ClassSource;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ClassSource implementation for loading classes from Apache Kafka topics.
 * Useful for dynamic class loading in microservices and event-driven systems.
 * Classes are published to Kafka with the class name as the key and bytecode as the value.
 * Requires the Apache Kafka clients dependency.
 */

/**
 * Loads classes from Kafka topics.
 * Classes are published to topics with key = fully qualified class name, value = class bytes.
 */
public class KafkaClassSource implements ClassSource {
    private final KafkaConsumer<String, byte[]> consumer;
    private final String topic;
    private final Map<String, byte[]> classCache;
    private final long pollTimeoutMs;

    private KafkaClassSource(KafkaConsumer<String, byte[]> consumer, String topic, long pollTimeoutMs) {
        this.consumer = Objects.requireNonNull(consumer, "consumer cannot be null");
        this.topic = Objects.requireNonNull(topic, "topic cannot be null");
        this.classCache = new ConcurrentHashMap<>();
        this.pollTimeoutMs = pollTimeoutMs;

        // Subscribe to topic
        consumer.subscribe(Collections.singletonList(topic));

        // Initial poll to load existing classes
        loadExistingClasses();
    }

    private void loadExistingClasses() {
        ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(pollTimeoutMs));
        for (ConsumerRecord<String, byte[]> record : records) {
            if (record.key() != null && record.value() != null) {
                classCache.put(record.key(), record.value());
            }
        }
    }

    @Override
    public byte[] loadClassData(String className) throws IOException {
        // Check cache first
        if (classCache.containsKey(className)) {
            return classCache.get(className);
        }

        // Poll for new messages
        ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(pollTimeoutMs));
        for (ConsumerRecord<String, byte[]> record : records) {
            if (record.key() != null && record.value() != null) {
                classCache.put(record.key(), record.value());
                if (record.key().equals(className)) {
                    return record.value();
                }
            }
        }

        throw new IOException("Class not found in Kafka topic: " + className);
    }

    @Override
    public boolean canLoad(String className) {
        return classCache.containsKey(className);
    }

    @Override
    public String getDescription() {
        return "KafkaClassSource[topic=" + topic + ", cached=" + classCache.size() + "]";
    }

    public void close() {
        if (consumer != null) {
            consumer.close();
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String bootstrapServers;
        private String topic;
        private String groupId = "jclassloader-" + UUID.randomUUID();
        private long pollTimeoutMs = 1000;
        private final Properties additionalProps = new Properties();

        public Builder bootstrapServers(String bootstrapServers) {
            this.bootstrapServers = bootstrapServers;
            return this;
        }

        public Builder topic(String topic) {
            this.topic = topic;
            return this;
        }

        public Builder groupId(String groupId) {
            this.groupId = groupId;
            return this;
        }

        public Builder pollTimeout(long timeoutMs) {
            this.pollTimeoutMs = timeoutMs;
            return this;
        }

        public Builder property(String key, Object value) {
            additionalProps.put(key, value);
            return this;
        }

        public KafkaClassSource build() {
            Objects.requireNonNull(bootstrapServers, "bootstrapServers must be set");
            Objects.requireNonNull(topic, "topic must be set");

            Properties props = new Properties();
            props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
            props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
            props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
            props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
            props.putAll(additionalProps);

            KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(props);
            return new KafkaClassSource(consumer, topic, pollTimeoutMs);
        }
    }
}
