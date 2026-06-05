package org.flossware.classloader;

import org.flossware.classloader.util.ClassNameUtil;

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
    private static final long MAX_CLASS_SIZE = 10 * 1024 * 1024; // 10MB default max size
    private final Path basePath;
    private final long maxClassSize;

    /**
     * Creates a local class source with the specified base path and max class size.
     *
     * @param basePath The base directory path containing class files
     * @param maxClassSize Maximum size of a class file in bytes
     * @throws NullPointerException if basePath is null
     * @throws IllegalArgumentException if maxClassSize is not positive
     */
    public LocalClassSource(Path basePath, long maxClassSize) {
        this.basePath = Objects.requireNonNull(basePath, "basePath cannot be null").toAbsolutePath().normalize();
        if (maxClassSize <= 0) {
            throw new IllegalArgumentException("maxClassSize must be positive");
        }
        this.maxClassSize = maxClassSize;
    }

    /**
     * Creates a local class source with the specified base path and default max size (10MB).
     *
     * @param basePath The base directory path containing class files
     * @throws NullPointerException if basePath is null
     */
    public LocalClassSource(Path basePath) {
        this(basePath, MAX_CLASS_SIZE);
    }

    /**
     * Creates a local class source with the specified base path string and default max size (10MB).
     *
     * @param basePath The base directory path string containing class files
     */
    public LocalClassSource(String basePath) {
        this(Paths.get(basePath));
    }

    /** {@inheritDoc} */
    @Override
    public byte[] loadClassData(String className) throws IOException {
        Objects.requireNonNull(className, "className cannot be null");
        Path classFile = getClassFilePath(className);

        if (!Files.exists(classFile)) {
            throw new IOException("Class file not found: " + classFile);
        }

        if (!Files.isRegularFile(classFile)) {
            throw new IOException("Not a regular file: " + classFile);
        }

        // Check size before reading to prevent OOM on large files
        long size = Files.size(classFile);
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

        return Files.readAllBytes(classFile);
    }

    /** {@inheritDoc} */
    @Override
    public boolean canLoad(String className) {
        try {
            Path classFile = getClassFilePath(className);
            return Files.exists(classFile) && Files.isRegularFile(classFile);
        } catch (IOException e) {
            return false;
        }
    }

    /** {@inheritDoc} */
    @Override
    public String getDescription() {
        return "LocalClassSource[" + basePath + "]";
    }

    private Path getClassFilePath(String className) throws IOException {
        // Convert class name to file path
        String fileName = ClassNameUtil.toClassFilePath(className);
        Path resolvedPath = basePath.resolve(fileName).normalize();

        // Ensure the resolved path is within basePath (this is the real protection)
        if (!resolvedPath.startsWith(basePath)) {
            throw new IOException("Path traversal attempt detected: " + className);
        }

        return resolvedPath;
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
