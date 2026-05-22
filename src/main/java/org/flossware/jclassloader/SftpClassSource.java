package org.flossware.jclassloader;

import com.jcraft.jsch.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Properties;

/**
 * ClassSource implementation for loading classes from SFTP servers.
 * Supports both password and private key authentication.
 * Requires the JSch library dependency.
 */
public class SftpClassSource implements ClassSource {
    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String privateKeyPath;
    private final String basePath;
    private JSch jsch;
    private Session session;
    private ChannelSftp sftpChannel;

    private SftpClassSource(String host, int port, String username, String password,
                           String privateKeyPath, String basePath) {
        this.host = Objects.requireNonNull(host, "host cannot be null");
        this.port = port > 0 ? port : 22;
        this.username = Objects.requireNonNull(username, "username cannot be null");
        this.password = password;
        this.privateKeyPath = privateKeyPath;
        this.basePath = basePath != null ? basePath : "/";
    }

    private synchronized void ensureConnected() throws IOException {
        if (sftpChannel != null && sftpChannel.isConnected()) {
            return;
        }

        try {
            jsch = new JSch();

            if (privateKeyPath != null) {
                jsch.addIdentity(privateKeyPath);
            }

            session = jsch.getSession(username, host, port);

            if (password != null) {
                session.setPassword(password);
            }

            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            session.connect(30000);

            Channel channel = session.openChannel("sftp");
            channel.connect(10000);
            sftpChannel = (ChannelSftp) channel;

        } catch (JSchException e) {
            throw new IOException("Failed to connect to SFTP server: " + host, e);
        }
    }

    @Override
    public byte[] loadClassData(String className) throws IOException {
        ensureConnected();

        String classPath = basePath + (basePath.endsWith("/") ? "" : "/") +
                          className.replace('.', '/') + ".class";

        try (InputStream in = sftpChannel.get(classPath);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }

            return out.toByteArray();

        } catch (SftpException e) {
            throw new IOException("Failed to load class from SFTP: " + classPath, e);
        }
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

    public void disconnect() {
        if (sftpChannel != null && sftpChannel.isConnected()) {
            sftpChannel.disconnect();
        }
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
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

        public SftpClassSource build() {
            return new SftpClassSource(host, port, username, password, privateKeyPath, basePath);
        }
    }
}
