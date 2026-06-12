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
    /** Default maximum IPC connection retries before failing. */
    private static final int DEFAULT_IPC_CONNECT_MAX_RETRIES = 3;
    /** Default IPC ping interval in milliseconds (10 seconds). */
    private static final int DEFAULT_IPC_PING_INTERVAL_MS = 10000;

    private final FileSystem hdfs;
    private final String basePath;
    private final long maxClassSize;

    private HdfsClassSource(FileSystem hdfs, String basePath, long maxClassSize) {
        this.hdfs = Objects.requireNonNull(hdfs, "hdfs cannot be null");
        this.basePath = basePath != null ? basePath : "/";
        this.maxClassSize = maxClassSize;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Reads class bytecode from HDFS. Validates file size before downloading
     * to prevent out-of-memory errors.</p>
     */
    @Override
    public byte[] loadClassData(String className) throws IOException {
        Objects.requireNonNull(className, "className cannot be null");
        Path classPath = getClassPath(className);

        // Check size BEFORE downloading to prevent OOM
        long size = getAndValidateFileSize(classPath);

        // Safe to download - size is within limits
        try (InputStream in = hdfs.open(classPath)) {
            byte[] data = new byte[(int)size];
            readFully(in, data, (int)size);
            return data;
        }
    }

    private long getAndValidateFileSize(Path classPath) throws IOException {
        Objects.requireNonNull(classPath, "classPath cannot be null");
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

        return size;
    }

    private void readFully(InputStream in, byte[] data, int size) throws IOException {
        Objects.requireNonNull(in, "in cannot be null");
        Objects.requireNonNull(data, "data cannot be null");
        int totalRead = readBytesFromStream(in, data, size);
        validateBytesRead(size, totalRead);
    }

    private int readBytesFromStream(InputStream in, byte[] data, int size) throws IOException {
        Objects.requireNonNull(in, "in cannot be null");
        Objects.requireNonNull(data, "data cannot be null");
        int totalRead = 0;
        while (totalRead < size) {
            int n = in.read(data, totalRead, size - totalRead);
            if (n == -1) {
                validateCompleteRead(totalRead, size);
                return totalRead;
            }
            totalRead += n;
        }
        return totalRead;
    }

    private void validateCompleteRead(int totalRead, int expected) throws IOException {
        if (totalRead < expected) {
            throw new IOException("Incomplete read: expected " + expected + " bytes, got " + totalRead);
        }
    }

    private void validateBytesRead(int expected, int actual) throws IOException {
        if (actual != expected) {
            throw new IOException("Expected " + expected + " bytes but read " + actual);
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean canLoad(String className) {
        Objects.requireNonNull(className, "className cannot be null");
        try {
            Path classPath = getClassPath(className);
            return hdfs.exists(classPath) && hdfs.isFile(classPath);
        } catch (IOException e) {
            return false;
        }
    }

    /** {@inheritDoc} */
    @Override
    public String getDescription() {
        return "HdfsClassSource[basePath=" + basePath + "]";
    }

    private Path getClassPath(String className) {
        Objects.requireNonNull(className, "className cannot be null");
        String classFile = ClassNameUtil.toClassFilePath(className);
        String fullPath = basePath.endsWith("/") ?
            basePath + classFile :
            basePath + "/" + classFile;
        return new Path(fullPath);
    }

    /**
     * Closes the underlying HDFS FileSystem and releases resources.
     *
     * @throws IOException if an I/O error occurs during closing
     */
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

        /**
         * Sets the HDFS NameNode URI.
         *
         * <p>Format: {@code hdfs://hostname:port}</p>
         *
         * <p>Examples:</p>
         * <ul>
         *   <li>Single NameNode: "hdfs://namenode.example.com:9000"</li>
         *   <li>HA NameNode: "hdfs://mycluster" (requires core-site.xml configuration)</li>
         *   <li>Local testing: "hdfs://localhost:9000"</li>
         * </ul>
         *
         * <p>If not set, uses fs.defaultFS from Hadoop configuration files.</p>
         *
         * @param nameNodeUri NameNode URI (null to use configuration files)
         * @return this builder
         */
        public Builder nameNodeUri(String nameNodeUri) {
            // nameNodeUri can be null to use configuration defaults - don't require non-null
            this.nameNodeUri = nameNodeUri;
            return this;
        }

        /**
         * Sets the base directory path in HDFS.
         *
         * <p>Classes are loaded from: {@code basePath + "/" + className.replace('.', '/') + ".class"}</p>
         *
         * <p>Example: If basePath is "/app/classes" and loading "com.example.MyClass",
         * the HDFS path will be "/app/classes/com/example/MyClass.class"</p>
         *
         * @param basePath Base directory path (default: "/")
         * @return this builder
         * @throws NullPointerException if basePath is null
         */
        public Builder basePath(String basePath) {
            this.basePath = Objects.requireNonNull(basePath, "basePath cannot be null");
            return this;
        }

        /**
         * Sets a custom Hadoop Configuration.
         *
         * <p>Use this to override default Hadoop settings or provide configuration
         * for HA NameNodes, Kerberos authentication, etc.</p>
         *
         * <p>If not set, creates a new Configuration() which loads from classpath.</p>
         *
         * @param configuration Hadoop Configuration (null to use default)
         * @return this builder
         */
        public Builder configuration(Configuration configuration) {
            this.configuration = configuration;
            return this;
        }

        /**
         * Sets the maximum allowed class file size.
         *
         * <p>Prevents OOM attacks by rejecting files larger than this limit.</p>
         *
         * @param maxBytes Maximum size in bytes (default: 10MB)
         * @return this builder
         * @throws IllegalArgumentException if maxBytes <= 0
         */
        public Builder maxClassSize(long maxBytes) {
            if (maxBytes <= 0) {
                throw new IllegalArgumentException("maxClassSize must be positive");
            }
            this.maxClassSize = maxBytes;
            return this;
        }

        /**
         * Sets the socket timeout for HDFS read operations.
         *
         * <p>Prevents hanging indefinitely when reading class files from HDFS.</p>
         *
         * @param timeoutMs Timeout in milliseconds (default: 30000ms = 30 seconds, 0 = infinite)
         * @return this builder
         * @throws IllegalArgumentException if timeoutMs < 0
         */
        public Builder socketTimeout(int timeoutMs) {
            if (timeoutMs < 0) {
                throw new IllegalArgumentException("socketTimeout must be >= 0");
            }
            this.socketTimeout = timeoutMs;
            return this;
        }

        /**
         * Sets the connection timeout for HDFS NameNode connections.
         *
         * <p>Prevents hanging indefinitely when connecting to HDFS.</p>
         *
         * @param timeoutMs Timeout in milliseconds (default: 10000ms = 10 seconds, 0 = infinite)
         * @return this builder
         * @throws IllegalArgumentException if timeoutMs < 0
         */
        public Builder connectTimeout(int timeoutMs) {
            if (timeoutMs < 0) {
                throw new IllegalArgumentException("connectTimeout must be >= 0");
            }
            this.connectTimeout = timeoutMs;
            return this;
        }

        /**
         * Builds the HdfsClassSource with configured settings.
         *
         * <p>Establishes connection to HDFS FileSystem.</p>
         *
         * @return A new HdfsClassSource instance
         * @throws IOException if HDFS connection fails
         */
        public HdfsClassSource build() throws IOException {
            Configuration conf = configuration != null ? configuration : new Configuration();

            if (nameNodeUri != null) {
                conf.set("fs.defaultFS", nameNodeUri);
            }

            // Configure timeouts to prevent hanging
            conf.setInt("ipc.client.connect.timeout", connectTimeout);
            conf.setInt("ipc.client.connect.max.retries", DEFAULT_IPC_CONNECT_MAX_RETRIES);
            conf.setInt("ipc.ping.interval", DEFAULT_IPC_PING_INTERVAL_MS);
            conf.setInt("dfs.client.socket-timeout", socketTimeout);

            FileSystem hdfs = FileSystem.get(conf);
            return new HdfsClassSource(hdfs, basePath, maxClassSize);
        }
    }
}
