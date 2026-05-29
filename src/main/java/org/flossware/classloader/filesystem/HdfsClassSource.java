package org.flossware.classloader.filesystem;

import org.apache.hadoop.conf.Configuration;

import static org.flossware.classloader.util.ClassLoaderConstants.DEFAULT_BUFFER_SIZE;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.flossware.classloader.ClassSource;
import org.flossware.classloader.util.ClassNameUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * ClassSource implementation for loading classes from Hadoop HDFS.
 * Supports distributed file system access for class loading in Hadoop clusters.
 * Requires the Hadoop client dependency.
 */
public class HdfsClassSource implements ClassSource, AutoCloseable {
    private static final long MAX_CLASS_SIZE = 10 * 1024 * 1024; // 10MB default
    private static final int DEFAULT_SOCKET_TIMEOUT = 30000; // 30 seconds
    private static final int DEFAULT_CONNECT_TIMEOUT = 10000; // 10 seconds

    private final FileSystem hdfs;
    private final String basePath;
    private final long maxClassSize;

    private HdfsClassSource(FileSystem hdfs, String basePath, long maxClassSize) {
        this.hdfs = Objects.requireNonNull(hdfs, "hdfs cannot be null");
        this.basePath = basePath != null ? basePath : "/";
        this.maxClassSize = maxClassSize;
    }

    @Override
    public byte[] loadClassData(String className) throws IOException {
        Path classPath = getClassPath(className);

        // Check size BEFORE downloading to prevent OOM
        FileStatus status = hdfs.getFileStatus(classPath);
        long size = status.getLen();

        if (size > maxClassSize) {
            throw new IOException(
                "Class file too large: " + size + " bytes (max " + maxClassSize + ")"
            );
        }

        if (size > Integer.MAX_VALUE) {
            throw new IOException(
                "Class file exceeds Java array limit: " + size + " bytes"
            );
        }

        // Safe to download - size is within limits
        try (InputStream in = hdfs.open(classPath)) {
            byte[] data = new byte[(int)size];
            int totalRead = 0;

            while (totalRead < size) {
                int n = in.read(data, totalRead, (int)size - totalRead);
                if (n == -1) break;
                totalRead += n;
            }

            if (totalRead != size) {
                throw new IOException(
                    "Expected " + size + " bytes but read " + totalRead
                );
            }

            return data;
        }
    }

    @Override
    public boolean canLoad(String className) {
        try {
            Path classPath = getClassPath(className);
            return hdfs.exists(classPath) && hdfs.isFile(classPath);
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public String getDescription() {
        return "HdfsClassSource[basePath=" + basePath + "]";
    }

    private Path getClassPath(String className) {
        String classFile = ClassNameUtil.toClassFilePath(className);
        String fullPath = basePath.endsWith("/") ?
            basePath + classFile :
            basePath + "/" + classFile;
        return new Path(fullPath);
    }

    @Override
    public void close() throws IOException {
        if (hdfs != null) {
            hdfs.close();
        }
    }

    /**
     * Creates a new Builder for constructing HdfsClassSource instances.
     *
     * @return A new Builder with default configuration
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for constructing HdfsClassSource instances with fluent API.
     *
     * <p>Configures Hadoop HDFS connection for class loading from distributed storage.</p>
     *
     * <p><b>Basic Example:</b></p>
     * <pre>{@code
     * HdfsClassSource source = HdfsClassSource.builder()
     *     .nameNodeUri("hdfs://namenode.example.com:9000")
     *     .basePath("/app/classes")
     *     .build();
     * }</pre>
     *
     * <p><b>Advanced Example with Timeouts and Size Limits:</b></p>
     * <pre>{@code
     * HdfsClassSource source = HdfsClassSource.builder()
     *     .nameNodeUri("hdfs://namenode:9000")
     *     .basePath("/production/classes/v2")
     *     .socketTimeout(60000)           // 60 seconds
     *     .connectTimeout(15000)          // 15 seconds
     *     .maxClassSize(20 * 1024 * 1024) // 20MB
     *     .build();
     * }</pre>
     *
     * <p><b>Using Custom Hadoop Configuration:</b></p>
     * <pre>{@code
     * Configuration conf = new Configuration();
     * conf.set("dfs.replication", "2");
     * conf.set("dfs.nameservices", "mycluster");
     *
     * HdfsClassSource source = HdfsClassSource.builder()
     *     .configuration(conf)
     *     .basePath("/classes")
     *     .build();
     * }</pre>
     *
     * <p><b>Defaults:</b></p>
     * <ul>
     *   <li>basePath: "/" (root)</li>
     *   <li>socketTimeout: 30000ms (30 seconds)</li>
     *   <li>connectTimeout: 10000ms (10 seconds)</li>
     *   <li>maxClassSize: 10MB</li>
     *   <li>configuration: new Configuration() (from classpath)</li>
     * </ul>
     */
    public static class Builder {
        private String nameNodeUri;
        private String basePath = "/";
        private Configuration configuration;
        private long maxClassSize = MAX_CLASS_SIZE;
        private int socketTimeout = DEFAULT_SOCKET_TIMEOUT;
        private int connectTimeout = DEFAULT_CONNECT_TIMEOUT;

        public Builder nameNodeUri(String nameNodeUri) {
            this.nameNodeUri = nameNodeUri;
            return this;
        }

        public Builder basePath(String basePath) {
            this.basePath = Objects.requireNonNull(basePath, "basePath cannot be null");
            return this;
        }

        public Builder configuration(Configuration configuration) {
            this.configuration = configuration;
            return this;
        }

        public Builder maxClassSize(long maxBytes) {
            if (maxBytes <= 0) {
                throw new IllegalArgumentException("maxClassSize must be positive");
            }
            this.maxClassSize = maxBytes;
            return this;
        }

        public Builder socketTimeout(int timeoutMs) {
            if (timeoutMs < 0) {
                throw new IllegalArgumentException("socketTimeout must be >= 0");
            }
            this.socketTimeout = timeoutMs;
            return this;
        }

        public Builder connectTimeout(int timeoutMs) {
            if (timeoutMs < 0) {
                throw new IllegalArgumentException("connectTimeout must be >= 0");
            }
            this.connectTimeout = timeoutMs;
            return this;
        }

        public HdfsClassSource build() throws IOException {
            Configuration conf = configuration != null ? configuration : new Configuration();

            if (nameNodeUri != null) {
                conf.set("fs.defaultFS", nameNodeUri);
            }

            // Configure timeouts to prevent hanging
            conf.setInt("ipc.client.connect.timeout", connectTimeout);
            conf.setInt("ipc.client.connect.max.retries", 3);
            conf.setInt("ipc.ping.interval", 10000);
            conf.setInt("dfs.client.socket-timeout", socketTimeout);

            FileSystem hdfs = FileSystem.get(conf);
            return new HdfsClassSource(hdfs, basePath, maxClassSize);
        }
    }
}
