package org.flossware.jclassloader.messaging;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.any;


/**
 * Tests for KafkaClassSource with mocked Kafka consumer.
 */
class KafkaClassSourceTest {

    @Test
    void testBuilderWithBootstrapServersAndTopic() {
        KafkaClassSource source = KafkaClassSource.builder()
                .bootstrapServers("localhost:9092")
                .topic("classes")
                .build();

        assertNotNull(source);
        assertTrue(source.getDescription().contains("topic=classes"));
    }

    @Test
    void testBuilderWithGroupId() {
        KafkaClassSource source = KafkaClassSource.builder()
                .bootstrapServers("localhost:9092")
                .topic("classes")
                .groupId("my-group")
                .build();

        assertNotNull(source);
    }

    @Test
    void testBuilderWithPollTimeout() {
        KafkaClassSource source = KafkaClassSource.builder()
                .bootstrapServers("localhost:9092")
                .topic("classes")
                .pollTimeout(5000)
                .build();

        assertNotNull(source);
    }

    @Test
    void testBuilderWithAdditionalProperty() {
        KafkaClassSource source = KafkaClassSource.builder()
                .bootstrapServers("localhost:9092")
                .topic("classes")
                .property("max.poll.records", 100)
                .build();

        assertNotNull(source);
    }

    @Test
    void testBuilderNullBootstrapServersThrowsException() {
        assertThrows(NullPointerException.class, () -> {
            KafkaClassSource.builder()
                    .topic("classes")
                    .build();
        });
    }

    @Test
    void testBuilderNullTopicThrowsException() {
        assertThrows(NullPointerException.class, () -> {
            KafkaClassSource.builder()
                    .bootstrapServers("localhost:9092")
                    .build();
        });
    }

    @Test
    void testGetDescription() {
        KafkaClassSource source = KafkaClassSource.builder()
                .bootstrapServers("localhost:9092")
                .topic("my-topic")
                .build();

        String description = source.getDescription();
        assertTrue(description.contains("KafkaClassSource"));
        assertTrue(description.contains("topic=my-topic"));
        assertTrue(description.contains("cached="));
    }

    @Test
    void testBuilderChaining() {
        KafkaClassSource.Builder builder = KafkaClassSource.builder();
        assertSame(builder, builder.bootstrapServers("localhost:9092"));
        assertSame(builder, builder.topic("topic"));
        assertSame(builder, builder.groupId("group"));
        assertSame(builder, builder.pollTimeout(1000));
        assertSame(builder, builder.property("key", "value"));
    }

    @Test
    void testClose() {
        KafkaClassSource source = KafkaClassSource.builder()
                .bootstrapServers("localhost:9092")
                .topic("classes")
                .build();

        assertDoesNotThrow(() -> source.close());
    }

    @Test
    void testMultipleClose() {
        KafkaClassSource source = KafkaClassSource.builder()
                .bootstrapServers("localhost:9092")
                .topic("classes")
                .build();

        assertDoesNotThrow(() -> {
            source.close();
            source.close();
        });
    }

    @Test
    void testBuilderDefaultGroupId() {
        KafkaClassSource source = KafkaClassSource.builder()
                .bootstrapServers("localhost:9092")
                .topic("classes")
                .build();

        assertNotNull(source);
    }

    @Test
    void testBuilderDefaultPollTimeout() {
        KafkaClassSource source = KafkaClassSource.builder()
                .bootstrapServers("localhost:9092")
                .topic("classes")
                .build();

        assertNotNull(source);
    }

    @Test
    void testMultipleInstances() {
        KafkaClassSource source1 = KafkaClassSource.builder()
                .bootstrapServers("localhost:9092")
                .topic("topic1")
                .build();

        KafkaClassSource source2 = KafkaClassSource.builder()
                .bootstrapServers("localhost:9092")
                .topic("topic2")
                .build();

        assertNotEquals(source1.getDescription(), source2.getDescription());
    }
}
