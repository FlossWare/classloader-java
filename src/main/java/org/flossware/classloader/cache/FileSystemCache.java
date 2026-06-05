package org.flossware.classloader.cache;

import org.flossware.classloader.util.ClassNameUtil;

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

    /**
     * {@inheritDoc}
     *
     * <p>Reads the cached class file from disk. Returns {@code null} if the class
     * is not cached, if the file exceeds the maximum allowed size, or if an I/O
     * error occurs during reading.</p>
     */
    @Override
    public byte[] get(String className) {
        Objects.requireNonNull(className, "className cannot be null");
        try {
            Path classFile = getClassFilePath(className);
            if (!Files.exists(classFile)) {
                return null;
            }
            return loadClassFileIfValid(classFile);
        } catch (IOException e) {
            // Invalid class name, path traversal attempt, or I/O error
            return null;
        }
    }

    private byte[] loadClassFileIfValid(Path classFile) throws IOException {
        Objects.requireNonNull(classFile, "classFile cannot be null");
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

    /**
     * {@inheritDoc}
     *
     * <p>Writes class bytecode to a file on disk using atomic file operations
     * (write to temp file, then atomic move) to prevent corruption from
     * concurrent writes. Thread-safe via an internal write lock.</p>
     */
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

    /**
     * {@inheritDoc}
     *
     * <p>Checks whether the corresponding class file exists on disk.</p>
     */
    @Override
    public boolean contains(String className) {
        Objects.requireNonNull(className, "className cannot be null");
        try {
            return Files.exists(getClassFilePath(className));
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Recursively deletes all files and subdirectories within the cache directory.
     * Thread-safe via an internal write lock shared with {@link #put} and {@link #remove}.</p>
     */
    @Override
    public void clear() throws IOException {
        // Use same lock as put() to prevent race conditions
        writeLock.lock();
        try {
            if (!Files.exists(cacheDirectory)) {
                return;
            }

            List<IOException> errors = deleteCacheFiles();
            reportDeletionErrors(errors);
        } finally {
            writeLock.unlock();
        }
    }

    private List<IOException> deleteCacheFiles() throws IOException {
        List<IOException> errors = new ArrayList<>();
        deletePathsRecursively(cacheDirectory, errors);
        return errors;
    }

    private void deletePathsRecursively(Path path, List<IOException> errors) {
        // Files.walk() returns a Stream that MUST be closed to prevent resource leaks
        try (Stream<Path> paths = Files.walk(path)) {
            // Sort by depth (deepest first) to delete files before directories
            // Filter out the root cache directory itself - only delete its contents
            paths.filter(p -> !p.equals(path))
                 .sorted((a, b) -> Integer.compare(b.getNameCount(), a.getNameCount()))
                 .forEach(p -> deletePathSafely(p, errors));
        } catch (IOException e) {
            errors.add(e);
        }
    }

    private void deletePathSafely(Path path, List<IOException> errors) {
        Objects.requireNonNull(path, "path cannot be null");
        Objects.requireNonNull(errors, "errors cannot be null");
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            // Collect errors to report after attempting all deletions
            errors.add(e);
        }
    }

    private void reportDeletionErrors(List<IOException> errors) throws IOException {
        Objects.requireNonNull(errors, "errors cannot be null");
        if (errors.isEmpty()) {
            return;
        }
        throw createDeletionException(errors);
    }

    private IOException createDeletionException(List<IOException> errors) {
        IOException exception = new IOException(
            "Failed to clear cache: " + errors.size() + " file(s) could not be deleted"
        );
        errors.forEach(exception::addSuppressed);
        return exception;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Deletes the class file from disk. Thread-safe via an internal write lock
     * shared with {@link #put} and {@link #clear}.</p>
     */
    @Override
    public void remove(String className) throws IOException {
        Objects.requireNonNull(className, "className cannot be null");
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
        validateClassNameFormat(className);
        validateNoPathTraversal(className);

        // Convert class name to file path (e.g., "com.example.MyClass" → "com/example/MyClass.class")
        String fileName = ClassNameUtil.toClassFilePath(className);
        Path resolvedPath = cacheDirectory.resolve(fileName).normalize();

        // Defense in depth: ensure the resolved path is within cacheDirectory
        // This catches any path traversal attempts that made it through validation
        validateResolvedPath(resolvedPath, className);

        return resolvedPath;
    }

    private void validateClassNameFormat(String className) throws IOException {
        if (className == null || className.isEmpty()) {
            throw new IOException("Class name cannot be null or empty");
        }
    }

    private void validateNoPathTraversal(String className) throws IOException {
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
    }

    private void validateResolvedPath(Path resolvedPath, String className) throws IOException {
        if (!resolvedPath.normalize().startsWith(cacheDirectory.normalize())) {
            throw new IOException("Path traversal attempt detected: " + className);
        }
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
