package org.flossware.classloader.jar;

import org.flossware.classloader.util.ClassLoaderConstants;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Handles extraction of class data from JAR files with size validation and resource management.
 * Encapsulates the logic for reading class files from JAR entries with proper stream handling.
 */
public class JarClassExtractor {
    private static final long MAX_CLASS_SIZE = 10 * 1024 * 1024; // 10MB default max class size

    /**
     * Extracts class data from a cached JAR file.
     *
     * @param jarFile The JAR file to extract from
     * @param classFileName The class file name (e.g., "com/example/MyClass.class")
     * @param jarUrl The URL of the JAR (for error messages)
     * @return The class bytecode
     * @throws IOException if class not found or read fails
     */
    public byte[] extractClassFromJar(JarFile jarFile, String classFileName, String jarUrl) throws IOException {
        Objects.requireNonNull(jarFile, "jarFile cannot be null");
        Objects.requireNonNull(classFileName, "classFileName cannot be null");
        Objects.requireNonNull(jarUrl, "jarUrl cannot be null");
        JarEntry entry = jarFile.getJarEntry(classFileName);
        if (entry == null) {
            throw new IOException("Class not found in JAR: " + classFileName + " (URL: " + jarUrl + ")");
        }

        long size = entry.getSize();
        return extractClassDataFromEntry(jarFile, entry, size);
    }

    private byte[] extractClassDataFromEntry(JarFile jarFile, JarEntry entry, long size) throws IOException {
        if (size < 0) {
            try (InputStream entryIn = jarFile.getInputStream(entry)) {
                return readWithSizeLimit(entryIn, MAX_CLASS_SIZE);
            }
        }

        validateClassSize(size);
        return readClassDataWithKnownSize(jarFile, entry, size);
    }

    private void validateClassSize(long size) throws IOException {
        if (size > MAX_CLASS_SIZE) {
            throw new IOException(
                "Class file too large: " + size + " bytes (max " + MAX_CLASS_SIZE + ")"
            );
        }

        if (size > Integer.MAX_VALUE) {
            throw new IOException("Class file exceeds Java array limit: " + size);
        }
    }

    private byte[] readClassDataWithKnownSize(JarFile jarFile, JarEntry entry, long size) throws IOException {
        try (InputStream in = jarFile.getInputStream(entry)) {
            byte[] data = new byte[(int)size];
            readFully(in, data, (int)size);
            return data;
        }
    }

    private void readFully(InputStream in, byte[] data, int size) throws IOException {
        int totalRead = 0;
        while (totalRead < size) {
            int n = in.read(data, totalRead, size - totalRead);
            if (n == -1) {
                throw new IOException("Incomplete read: expected " + size + " bytes, but got " + totalRead);
            }
            totalRead += n;
        }
    }

    private byte[] readWithSizeLimit(InputStream in, long maxSize) throws IOException {
        Objects.requireNonNull(in, "in cannot be null");
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[ClassLoaderConstants.DEFAULT_BUFFER_SIZE];
            long totalRead = 0;
            int bytesRead;

            while ((bytesRead = in.read(buffer)) != -1) {
                totalRead += bytesRead;
                validateEntrySize(totalRead, maxSize);
                out.write(buffer, 0, bytesRead);
            }

            return out.toByteArray();
        }
    }

    private void validateEntrySize(long totalRead, long maxSize) throws IOException {
        if (totalRead > maxSize) {
            throw new IOException("Entry exceeds maximum size: " + totalRead);
        }
    }
}
