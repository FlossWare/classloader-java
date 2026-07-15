package org.flossware.classloader.jar;

import org.flossware.classloader.AuthConfig;
import org.flossware.classloader.AuthHelper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/**
 * Manages downloading and validating JAR files from HTTP sources.
 * Encapsulates HTTP connection setup, validation, and file transfer.
 */
public class JarDownloadManager {
    private static final long MAX_JAR_SIZE = 100 * 1024 * 1024; // 100MB default
    private final int connectTimeout;
    private final int readTimeout;
    private final AuthConfig authConfig;

    /**
     * Creates a JarDownloadManager with the specified connection and read timeouts
     * and authentication configuration.
     *
     * @param connectTimeout connection timeout in milliseconds (0 = infinite)
     * @param readTimeout read timeout in milliseconds (0 = infinite)
     * @param authConfig authentication configuration (null for no authentication)
     */
    public JarDownloadManager(int connectTimeout, int readTimeout, AuthConfig authConfig) {
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
        this.authConfig = authConfig;
    }

    /**
     * Creates a JarDownloadManager with the specified connection and read timeouts
     * and no authentication.
     *
     * @param connectTimeout connection timeout in milliseconds (0 = infinite)
     * @param readTimeout read timeout in milliseconds (0 = infinite)
     */
    public JarDownloadManager(int connectTimeout, int readTimeout) {
        this(connectTimeout, readTimeout, null);
    }

    /**
     * Downloads a JAR file from the given URL to a temporary path.
     *
     * @param jarUrl The URL to download from
     * @param tempJarPath The path to save the JAR to
     * @throws IOException if download fails
     */
    public void downloadJarFile(String jarUrl, Path tempJarPath) throws IOException {
        Objects.requireNonNull(jarUrl, "jarUrl cannot be null");
        Objects.requireNonNull(tempJarPath, "tempJarPath cannot be null");
        URL url = new URL(jarUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        try {
            connection.setConnectTimeout(connectTimeout);
            connection.setReadTimeout(readTimeout);
            connection.setRequestMethod("GET");
            if (authConfig != null) {
                AuthHelper.configureAuth(connection, authConfig);
            }

            validateJarResponse(connection, jarUrl);

            try (InputStream in = connection.getInputStream();
                 OutputStream out = Files.newOutputStream(tempJarPath,
                     StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                byte[] buffer = new byte[8192];
                long totalRead = 0;
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    totalRead += bytesRead;
                    if (totalRead > MAX_JAR_SIZE) {
                        throw new IOException(
                            "JAR download exceeds size limit: " + totalRead + " bytes (max " + MAX_JAR_SIZE + ")");
                    }
                    out.write(buffer, 0, bytesRead);
                }
            }
        } finally {
            safelyDisconnect(connection);
        }
    }

    private void validateJarResponse(HttpURLConnection connection, String jarUrl) throws IOException {
        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("HTTP " + responseCode + " for JAR: " + jarUrl);
        }

        long contentLength = connection.getContentLengthLong();
        if (contentLength > MAX_JAR_SIZE) {
            throw new IOException(
                "JAR too large: " + contentLength + " bytes (max " + MAX_JAR_SIZE + ")"
            );
        }
    }

    /**
     * Safely disconnects an HTTP connection, suppressing specific exceptions.
     *
     * <p>This method is used in finally blocks during resource cleanup.
     * Exception suppression here is appropriate because disconnect() is a cleanup
     * operation and should not propagate errors that could mask the original
     * exception from the download.</p>
     *
     * @param connection the HTTP connection to disconnect
     */
    private void safelyDisconnect(HttpURLConnection connection) {
        if (connection != null) {
            try {
                connection.disconnect();
            } catch (IllegalStateException | UncheckedIOException e) {
                // Suppress exceptions during resource cleanup to avoid masking
                // the original download exception
            }
        }
    }
}
