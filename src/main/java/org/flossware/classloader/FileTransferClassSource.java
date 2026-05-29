package org.flossware.classloader;

import org.flossware.filetransfer.FileTransferClient;
import org.flossware.classloader.util.ClassNameUtil;

import java.io.IOException;
import java.util.Objects;

/**
 * ClassSource implementation that wraps a FileTransferClient.
 * This adapter allows file transfer protocols (SFTP, WebDAV, SMB/CIFS, FTP/FTPS)
 * to be used as a class source by converting class names to file paths.
 *
 * <p>Requires the jfiletransfer library and the protocol-specific SDK.</p>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // SFTP
 * FileTransferClient sftp = SftpFileTransferClient.builder()
 *     .host("sftp.example.com")
 *     .port(22)
 *     .username("deploy")
 *     .password("secret")
 *     .basePath("/opt/classes")
 *     .build();
 *
 * ApplicationClassLoader loader = ApplicationClassLoader.builder()
 *     .addClassSource(new FileTransferClassSource(sftp))
 *     .build();
 *
 * // WebDAV
 * FileTransferClient webdav = WebDavFileTransferClient.builder()
 *     .url("https://webdav.example.com/classes")
 *     .username("user")
 *     .password("pass")
 *     .build();
 *
 * ApplicationClassLoader loader = ApplicationClassLoader.builder()
 *     .addClassSource(new FileTransferClassSource(webdav))
 *     .build();
 * }</pre>
 *
 * <p>The FileTransferClassSource will be automatically closed when the ApplicationClassLoader is closed.</p>
 *
 * @see org.flossware.filetransfer.FileTransferClient
 * @see org.flossware.filetransfer.SftpFileTransferClient
 */
public class FileTransferClassSource implements ClassSource, AutoCloseable {
    private final FileTransferClient client;

    /**
     * Creates a file transfer class source.
     *
     * @param client The file transfer client to use
     * @throws NullPointerException if client is null
     */
    public FileTransferClassSource(FileTransferClient client) {
        this.client = Objects.requireNonNull(client, "client cannot be null");
    }

    @Override
    public byte[] loadClassData(String className) throws IOException {
        String path = classNameToPath(className);
        return client.readFile(path);
    }

    @Override
    public boolean canLoad(String className) {
        try {
            String path = classNameToPath(className);
            return client.exists(path);
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public String getDescription() {
        return "FileTransferClassSource[" + client.getDescription() + "]";
    }

    @Override
    public void close() throws IOException {
        if (client != null) {
            client.close();
        }
    }

    /**
     * Converts a fully-qualified class name to a file path.
     *
     * @param className The class name (e.g., "com.example.MyClass")
     * @return The file path (e.g., "com/example/MyClass.class")
     */
    private String classNameToPath(String className) {
        return ClassNameUtil.toClassFilePath(className);
    }

    /**
     * Gets the underlying file transfer client.
     *
     * @return The file transfer client
     */
    public FileTransferClient getClient() {
        return client;
    }
}
