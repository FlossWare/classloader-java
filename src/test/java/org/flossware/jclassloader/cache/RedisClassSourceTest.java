package org.flossware.jclassloader.cache;

import org.flossware.jclassloader.cache.RedisClassSource;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.JedisPoolConfig;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Tests for RedisClassSource builder and configuration.
 */
class RedisClassSourceTest {

    @Test
    void testBuilderDefaults() {
        RedisClassSource source = RedisClassSource.builder()
                .build();

        assertNotNull(source);
        assertTrue(source.getDescription().contains("RedisClassSource"));
        assertTrue(source.getDescription().contains("prefix=class:"));
    }

    @Test
    void testBuilderHost() {
        RedisClassSource source = RedisClassSource.builder()
                .host("redis.example.com")
                .build();

        assertNotNull(source);
    }

    @Test
    void testBuilderPort() {
        RedisClassSource source = RedisClassSource.builder()
                .port(7000)
                .build();

        assertNotNull(source);
    }

    @Test
    void testBuilderPassword() {
        RedisClassSource source = RedisClassSource.builder()
                .password("secret")
                .build();

        assertNotNull(source);
    }

    @Test
    void testBuilderDatabase() {
        RedisClassSource source = RedisClassSource.builder()
                .database(5)
                .build();

        assertNotNull(source);
    }

    @Test
    void testBuilderKeyPrefix() {
        RedisClassSource source = RedisClassSource.builder()
                .keyPrefix("myapp:classes:")
                .build();

        assertTrue(source.getDescription().contains("prefix=myapp:classes:"));
    }

    @Test
    void testBuilderTimeout() {
        RedisClassSource source = RedisClassSource.builder()
                .timeout(5000)
                .build();

        assertNotNull(source);
    }

    @Test
    void testBuilderPoolConfig() {
        JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxTotal(20);

        RedisClassSource source = RedisClassSource.builder()
                .poolConfig(config)
                .build();

        assertNotNull(source);
    }

    @Test
    void testGetDescription() {
        RedisClassSource source = RedisClassSource.builder()
                .keyPrefix("test:")
                .build();

        String description = source.getDescription();
        assertTrue(description.contains("RedisClassSource"));
        assertTrue(description.contains("prefix=test:"));
    }

    @Test
    void testGetDescriptionDefaultPrefix() {
        RedisClassSource source = RedisClassSource.builder()
                .build();

        assertTrue(source.getDescription().contains("prefix=class:"));
    }

    @Test
    void testClose() {
        RedisClassSource source = RedisClassSource.builder()
                .build();

        assertDoesNotThrow(() -> source.close());
    }

    @Test
    void testMultipleClose() {
        RedisClassSource source = RedisClassSource.builder()
                .build();

        assertDoesNotThrow(() -> {
            source.close();
            source.close();
        });
    }

    @Test
    void testBuilderChaining() {
        RedisClassSource source = RedisClassSource.builder()
                .host("redis.example.com")
                .port(6380)
                .password("secret")
                .database(2)
                .keyPrefix("app:")
                .timeout(3000)
                .build();

        assertNotNull(source);
        assertTrue(source.getDescription().contains("prefix=app:"));
    }

    @Test
    void testBuilderWithAllOptions() {
        JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxTotal(50);
        config.setMaxIdle(10);

        RedisClassSource source = RedisClassSource.builder()
                .host("redis.example.com")
                .port(7000)
                .password("supersecret")
                .database(3)
                .keyPrefix("custom:")
                .timeout(5000)
                .poolConfig(config)
                .build();

        assertNotNull(source);
    }

    @Test
    void testBuilderWithoutPassword() {
        RedisClassSource source = RedisClassSource.builder()
                .host("localhost")
                .port(6379)
                .database(0)
                .build();

        assertNotNull(source);
    }

    @Test
    void testBuilderWithPassword() {
        RedisClassSource source = RedisClassSource.builder()
                .host("localhost")
                .password("mypassword")
                .build();

        assertNotNull(source);
    }

    @Test
    void testDifferentPrefixes() {
        RedisClassSource source1 = RedisClassSource.builder()
                .keyPrefix("app1:")
                .build();

        RedisClassSource source2 = RedisClassSource.builder()
                .keyPrefix("app2:")
                .build();

        assertTrue(source1.getDescription().contains("prefix=app1:"));
        assertTrue(source2.getDescription().contains("prefix=app2:"));
    }
}
