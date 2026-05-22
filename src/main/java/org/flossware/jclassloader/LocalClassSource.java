package org.flossware.jclassloader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

/**
 * ClassSource implementation for loading classes from the local file system.
 * This is the fastest class loading source as it reads directly from disk.
 */
public class LocalClassSource implements ClassSource {
    private final Path basePath;

    /**
     * Creates a local class source with the specified base path.
     *
     * @param basePath The base directory path containing class files
     * @throws NullPointerException if basePath is null
     */
    public LocalClassSource(Path basePath) {
        this.basePath = Objects.requireNonNull(basePath, "basePath cannot be null");
    }

    /**
     * Creates a local class source with the specified base path string.
     *
     * @param basePath The base directory path string containing class files
     */
    public LocalClassSource(String basePath) {
        this(Paths.get(basePath));
    }

    @Override
    public byte[] loadClassData(String className) throws IOException {
        Path classFile = getClassFilePath(className);
        if (Files.exists(classFile) && Files.isRegularFile(classFile)) {
            return Files.readAllBytes(classFile);
        }
        throw new IOException("Class file not found: " + classFile);
    }

    @Override
    public boolean canLoad(String className) {
        Path classFile = getClassFilePath(className);
        return Files.exists(classFile) && Files.isRegularFile(classFile);
    }

    @Override
    public String getDescription() {
        return "LocalClassSource[" + basePath + "]";
    }

    private Path getClassFilePath(String className) {
        String fileName = className.replace('.', '/') + ".class";
        return basePath.resolve(fileName);
    }

    /**
     * Gets the base directory path for this class source.
     *
     * @return The base path
     */
    public Path getBasePath() {
        return basePath;
    }
}
