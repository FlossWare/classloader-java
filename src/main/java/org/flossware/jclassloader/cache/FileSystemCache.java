package org.flossware.jclassloader.cache;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * File-system based implementation of ClassCache.
 * Caches class bytecode as .class files in a specified directory.
 * Thread-safe for concurrent read/write operations using atomic file operations.
 */
public class FileSystemCache implements ClassCache {
    private final Path cacheDirectory;
    private final Lock writeLock = new ReentrantLock();

    /**
     * Creates a file system cache with the specified directory.
     * The directory is created if it doesn't exist.
     *
     * @param cacheDirectory The directory path to use for caching
     * @throws IOException if the directory cannot be created
     * @throws NullPointerException if cacheDirectory is null
     */
    public FileSystemCache(Path cacheDirectory) throws IOException {
        this.cacheDirectory = Objects.requireNonNull(cacheDirectory, "cacheDirectory cannot be null")
                .toAbsolutePath().normalize();
        Files.createDirectories(this.cacheDirectory);
    }

    /**
     * Creates a file system cache with the specified directory path string.
     *
     * @param cacheDirectory The directory path string to use for caching
     * @throws IOException if the directory cannot be created
     */
    public FileSystemCache(String cacheDirectory) throws IOException {
        this(Paths.get(cacheDirectory));
    }

    @Override
    public byte[] get(String className) {
        try {
            Path classFile = getClassFilePath(className);
            if (Files.exists(classFile)) {
                return Files.readAllBytes(classFile);
            }
        } catch (IOException e) {
            // Invalid class name or path traversal attempt
            return null;
        }
        return null;
    }

    @Override
    public void put(String className, byte[] classData) throws IOException {
        Objects.requireNonNull(className, "className cannot be null");
        Objects.requireNonNull(classData, "classData cannot be null");

        Path classFile = getClassFilePath(className);

        // Use atomic write via temp file to prevent corruption from concurrent writes
        writeLock.lock();
        try {
            Files.createDirectories(classFile.getParent());

            // Write to temporary file first
            Path tempFile = Files.createTempFile(classFile.getParent(), ".tmp-", ".class");
            try {
                Files.write(tempFile, classData, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                // Atomically move temp file to final location
                Files.move(tempFile, classFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                // Clean up temp file on failure
                Files.deleteIfExists(tempFile);
                throw e;
            }
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public boolean contains(String className) {
        try {
            return Files.exists(getClassFilePath(className));
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public void clear() throws IOException {
        if (Files.exists(cacheDirectory)) {
            Files.walk(cacheDirectory)
                .sorted((a, b) -> b.compareTo(a))
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        // Ignore deletion errors to ensure we attempt to delete all files
                        // The cache directory will be recreated below
                    }
                });
            Files.createDirectories(cacheDirectory);
        }
    }

    @Override
    public void remove(String className) throws IOException {
        Path classFile = getClassFilePath(className);
        Files.deleteIfExists(classFile);
    }

    private Path getClassFilePath(String className) throws IOException {
        // Prevent path traversal attacks
        if (className.contains("..") || className.contains("/") || className.contains("\\")) {
            throw new IOException("Invalid class name (potential path traversal): " + className);
        }

        String fileName = className.replace('.', '/') + ".class";
        Path resolvedPath = cacheDirectory.resolve(fileName).normalize();

        // Ensure the resolved path is within cacheDirectory
        if (!resolvedPath.startsWith(cacheDirectory)) {
            throw new IOException("Path traversal attempt detected: " + className);
        }

        return resolvedPath;
    }

    /**
     * Gets the cache directory path.
     *
     * @return The cache directory path
     */
    public Path getCacheDirectory() {
        return cacheDirectory;
    }
}
