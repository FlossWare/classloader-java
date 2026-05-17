package org.flossware.jclassloader.cache;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

public class FileSystemCache implements ClassCache {
    private final Path cacheDirectory;

    public FileSystemCache(Path cacheDirectory) throws IOException {
        this.cacheDirectory = Objects.requireNonNull(cacheDirectory, "cacheDirectory cannot be null");
        Files.createDirectories(cacheDirectory);
    }

    public FileSystemCache(String cacheDirectory) throws IOException {
        this(Paths.get(cacheDirectory));
    }

    @Override
    public byte[] get(String className) {
        Path classFile = getClassFilePath(className);
        if (Files.exists(classFile)) {
            try {
                return Files.readAllBytes(classFile);
            } catch (IOException e) {
                return null;
            }
        }
        return null;
    }

    @Override
    public void put(String className, byte[] classData) throws IOException {
        Objects.requireNonNull(className, "className cannot be null");
        Objects.requireNonNull(classData, "classData cannot be null");

        Path classFile = getClassFilePath(className);
        Files.createDirectories(classFile.getParent());
        Files.write(classFile, classData, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    @Override
    public boolean contains(String className) {
        return Files.exists(getClassFilePath(className));
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

    private Path getClassFilePath(String className) {
        String fileName = className.replace('.', '/') + ".class";
        return cacheDirectory.resolve(fileName);
    }

    public Path getCacheDirectory() {
        return cacheDirectory;
    }
}
