package org.flossware.jclassloader;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Properties;

/**
 * ClassSource implementation for loading classes from SFTP servers.
 * Supports both password and private key authentication with retry logic.
 * Requires the JSch library dependency.
 * Implements AutoCloseable for proper resource management - call close() when done.
 */
public class SftpClassSource implements ClassSource, AutoCloseable {
    private static final int DEFAULT_SESSION_TIMEOUT_MS = 30000;
    private static final int DEFAULT_CHANNEL_TIMEOUT_MS = 10000;
    private static final int DEFAULT_BUFFER_SIZE = 8192;

    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String privateKeyPath;
    private final String basePath;
    private final String knownHostsFile;
    private final RetryPolicy retryPolicy;
    private JSch jsch;
    private Session session;
    private ChannelSftp sftpChannel;

    private SftpClassSource(String host, int port, String username, String password,
                           String privateKeyPath, String basePath, String knownHostsFile, RetryPolicy retryPolicy) {
        this.host = Objects.requireNonNull(host, "host cannot be null");
        this.port = port > 0 ? port : 22;
        this.username = Objects.requireNonNull(username, "username cannot be null");
        this.password = password;
        this.privateKeyPath = privateKeyPath;
        this.basePath = basePath != null ? basePath : "/";
        this.knownHostsFile = knownHostsFile;
        this.retryPolicy = retryPolicy != null ? retryPolicy : RetryPolicy.defaultPolicy();
    }

    private synchronized void ensureConnected() throws IOException {
        if (sftpChannel != null && sftpChannel.isConnected()) {
            return;
        }

        try {
            jsch = new JSch();

            if (knownHostsFile != null) {
                jsch.setKnownHosts(knownHostsFile);
            }

            if (privateKeyPath != null) {
                jsch.addIdentity(privateKeyPath);
            }

            session = jsch.getSession(username, host, port);

            if (password != null) {
                session.setPassword(password);
            }

            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "yes");
            session.setConfig(config);
            session.connect(DEFAULT_SESSION_TIMEOUT_MS);

            Channel channel = session.openChannel("sftp");
            channel.connect(DEFAULT_CHANNEL_TIMEOUT_MS);
            sftpChannel = (ChannelSftp) channel;

        } catch (JSchException e) {
            throw new IOException("Failed to connect to SFTP server: " + host, e);
        }
    }

    @Override
    public byte[] loadClassData(String className) throws IOException {
        return retryPolicy.execute(() -> {
            ensureConnected();

            String classPath = basePath + (basePath.endsWith("/") ? "" : "/") +
                              className.replace('.', '/') + ".class";

            try (InputStream in = sftpChannel.get(classPath);
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {

                byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }

                return out.toByteArray();

            } catch (SftpException e) {
                throw new IOException("Failed to load class from SFTP: " + classPath, e);
            }
        });
    }

    @Override
    public boolean canLoad(String className) {
        try {
            ensureConnected();
            String classPath = basePath + (basePath.endsWith("/") ? "" : "/") +
                              className.replace('.', '/') + ".class";
            SftpATTRS attrs = sftpChannel.stat(classPath);
            return attrs != null && !attrs.isDir();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getDescription() {
        return "SftpClassSource[" + username + "@" + host + ":" + port + basePath + "]";
    }

    /**
     * Gets the retry policy for this SFTP class source.
     *
     * @return The retry policy
     */
    public RetryPolicy getRetryPolicy() {
        return retryPolicy;
    }

    /**
     * Disconnects the SFTP session and channel.
     * Should be called when done using this class source to free resources.
     */
    public void disconnect() {
        if (sftpChannel != null && sftpChannel.isConnected()) {
            sftpChannel.disconnect();
        }
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
    }

    /**
     * Closes the SFTP connection and releases all resources.
     * Implementation of AutoCloseable interface.
     */
    @Override
    public void close() {
        disconnect();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String host;
        private int port = 22;
        private String username;
        private String password;
        private String privateKeyPath;
        private String basePath = "/";
        private String knownHostsFile;
        private RetryPolicy retryPolicy;

        public Builder host(String host) {
            this.host = host;
            return this;
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder password(String password) {
            this.password = password;
            return this;
        }

        public Builder privateKey(String privateKeyPath) {
            this.privateKeyPath = privateKeyPath;
            return this;
        }

        public Builder basePath(String basePath) {
            this.basePath = basePath;
            return this;
        }

        public Builder knownHostsFile(String knownHostsFile) {
            this.knownHostsFile = knownHostsFile;
            return this;
        }

        public Builder retryPolicy(RetryPolicy retryPolicy) {
            this.retryPolicy = retryPolicy;
            return this;
        }

        public SftpClassSource build() {
            Objects.requireNonNull(host, "host must be set");
            Objects.requireNonNull(username, "username must be set");
            if (password == null && privateKeyPath == null) {
                throw new IllegalStateException("Either password or privateKey must be set");
            }
            if (port <= 0 || port > 65535) {
                throw new IllegalArgumentException("port must be between 1 and 65535");
            }
            return new SftpClassSource(host, port, username, password, privateKeyPath, basePath, knownHostsFile, retryPolicy);
        }
    }
}
