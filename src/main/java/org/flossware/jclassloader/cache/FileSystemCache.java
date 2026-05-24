package org.flossware.jclassloader.cache;

import org.flossware.jclassloader.util.ClassNameUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

/**
 * File-system based implementation of ClassCache.
 * Caches class bytecode as .class files in a specified directory.
 * Thread-safe for concurrent read/write operations using atomic file operations.
 */
public class FileSystemCache implements ClassCache {
    /** Maximum class file size in bytes (100MB) - prevents OutOfMemoryError */
    private static final long MAX_CLASS_FILE_SIZE = 100 * 1024 * 1024;

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
                // Validate file size before loading to prevent OutOfMemoryError
                long fileSize = Files.size(classFile);
                if (fileSize > MAX_CLASS_FILE_SIZE) {
                    // File is too large - possibly corrupted or malicious
                    // Delete it and return null to force re-fetch from source
                    Files.deleteIfExists(classFile);
                    return null;
                }
                return Files.readAllBytes(classFile);
            }
        } catch (IOException e) {
            // Invalid class name, path traversal attempt, or I/O error
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
        // Use same lock as put() to prevent race conditions
        writeLock.lock();
        try {
            if (Files.exists(cacheDirectory)) {
                List<IOException> errors = new ArrayList<>();

                // Files.walk() returns a Stream that MUST be closed to prevent resource leaks
                try (Stream<Path> paths = Files.walk(cacheDirectory)) {
                    // Sort by depth (deepest first) to delete files before directories
                    paths.sorted((a, b) -> Integer.compare(b.getNameCount(), a.getNameCount()))
                         .forEach(path -> {
                             try {
                                 Files.deleteIfExists(path);
                             } catch (IOException e) {
                                 // Collect errors to report after attempting all deletions
                                 errors.add(e);
                             }
                         });
                }

                // Report any errors that occurred
                if (!errors.isEmpty()) {
                    IOException exception = new IOException(
                        "Failed to clear cache: " + errors.size() + " file(s) could not be deleted"
                    );
                    errors.forEach(exception::addSuppressed);
                    throw exception;
                }
            }
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void remove(String className) throws IOException {
        // Use same lock as put() to prevent race conditions
        writeLock.lock();
        try {
            Path classFile = getClassFilePath(className);
            Files.deleteIfExists(classFile);
        } finally {
            writeLock.unlock();
        }
    }

    private Path getClassFilePath(String className) throws IOException {
        // Validate class name format (must be valid Java fully-qualified class name)
        // Examples: "MyClass", "com.example.MyClass", "com.example.pkg.MyClass$Inner"
        if (className == null || className.isEmpty()) {
            throw new IOException("Class name cannot be null or empty");
        }

        // Check for obvious path traversal attempts before conversion
        // Valid class names use dots, not slashes or backslashes
        if (className.contains("..")) {
            throw new IOException("Invalid class name (contains '..'): " + className);
        }
        if (className.contains("/")) {
            throw new IOException("Invalid class name (contains '/'): " + className);
        }
        if (className.contains("\\")) {
            throw new IOException("Invalid class name (contains '\\'): " + className);
        }

        // Convert class name to file path (e.g., "com.example.MyClass" → "com/example/MyClass.class")
        String fileName = ClassNameUtil.toClassFilePath(className);
        Path resolvedPath = cacheDirectory.resolve(fileName).normalize();

        // Defense in depth: ensure the resolved path is within cacheDirectory
        // This catches any path traversal attempts that made it through validation
        if (!resolvedPath.normalize().startsWith(cacheDirectory.normalize())) {
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
