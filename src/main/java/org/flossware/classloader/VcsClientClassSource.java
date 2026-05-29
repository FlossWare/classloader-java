package org.flossware.classloader;

import org.flossware.vcs.VcsClient;
import org.flossware.classloader.util.ClassNameUtil;

import java.io.IOException;
import java.util.Objects;

/**
 * ClassSource implementation that wraps a VcsClient.
 * This adapter allows version control systems (Git)
 * to be used as a class source by converting class names to file paths.
 *
 * <p>Requires the jvcs library and the VCS system SDK.</p>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // Local Git repository
 * VcsClient git = GitVcsClient.builder()
 *     .repositoryPath("/opt/app-repo")
 *     .branch("release/v1.0")
 *     .basePath("build/classes")
 *     .build();
 *
 * ApplicationClassLoader loader = ApplicationClassLoader.builder()
 *     .addClassSource(new VcsClientClassSource(git))
 *     .build();
 *
 * // Remote Git repository (auto-cloned)
 * VcsClient git = GitVcsClient.builder()
 *     .remoteUrl("https://github.com/example/app.git")
 *     .branch("main")
 *     .cloneDirectory(new File("/tmp/app-clone"))
 *     .basePath("target/classes")
 *     .build();
 *
 * ApplicationClassLoader loader = ApplicationClassLoader.builder()
 *     .addClassSource(new VcsClientClassSource(git))
 *     .build();
 * }</pre>
 *
 * <p>The VcsClientClassSource will be automatically closed when the ApplicationClassLoader is closed.</p>
 *
 * @see org.flossware.vcs.VcsClient
 * @see org.flossware.vcs.GitVcsClient
 */
public class VcsClientClassSource implements ClassSource, AutoCloseable {
    private final VcsClient client;

    /**
     * Creates a VCS client class source.
     *
     * @param client The VCS client to use
     * @throws NullPointerException if client is null
     */
    public VcsClientClassSource(VcsClient client) {
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
        return "VcsClientClassSource[" + client.getDescription() + "]";
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
     * Gets the underlying VCS client.
     *
     * @return The VCS client
     */
    public VcsClient getClient() {
        return client;
    }
}
