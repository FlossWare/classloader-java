package org.flossware.jclassloader.cache;

import org.flossware.jclassloader.ClassSource;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.io.IOException;
import java.util.Objects;

/**
 * Loads classes from Redis.
 * Classes are stored with key = "class:{fully.qualified.ClassName}" and value = class bytes.
 */
public class RedisClassSource implements ClassSource, AutoCloseable {
    private final JedisPool jedisPool;
    private final String keyPrefix;

    private RedisClassSource(JedisPool jedisPool, String keyPrefix) {
        this.jedisPool = Objects.requireNonNull(jedisPool, "jedisPool cannot be null");
        this.keyPrefix = keyPrefix != null ? keyPrefix : "class:";
    }

    @Override
    public byte[] loadClassData(String className) throws IOException {
        String key = keyPrefix + className;

        try (Jedis jedis = jedisPool.getResource()) {
            byte[] classData = jedis.get(key.getBytes());
            if (classData == null) {
                throw new IOException("Class not found in Redis: " + className);
            }
            return classData;
        } catch (Exception e) {
            throw new IOException("Failed to load class from Redis: " + className, e);
        }
    }

    @Override
    public boolean canLoad(String className) {
        String key = keyPrefix + className;

        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.exists(key.getBytes());
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getDescription() {
        return "RedisClassSource[prefix=" + keyPrefix + "]";
    }

    @Override
    public void close() {
        if (jedisPool != null && !jedisPool.isClosed()) {
            jedisPool.close();
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String host = "localhost";
        private int port = 6379;
        private String password;
        private int database = 0;
        private String keyPrefix = "class:";
        private int timeout = 2000;
        private JedisPoolConfig poolConfig;

        public Builder host(String host) {
            this.host = host;
            return this;
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder password(String password) {
            this.password = password;
            return this;
        }

        public Builder database(int database) {
            this.database = database;
            return this;
        }

        public Builder keyPrefix(String keyPrefix) {
            this.keyPrefix = keyPrefix;
            return this;
        }

        public Builder timeout(int timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder poolConfig(JedisPoolConfig poolConfig) {
            this.poolConfig = poolConfig;
            return this;
        }

        public RedisClassSource build() {
            JedisPoolConfig config = poolConfig != null ? poolConfig : new JedisPoolConfig();

            JedisPool pool = password != null ?
                new JedisPool(config, host, port, timeout, password, database) :
                new JedisPool(config, host, port, timeout);

            return new RedisClassSource(pool, keyPrefix);
        }
    }
}
