package org.flossware.jclassloader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

public class LocalClassSource implements ClassSource {
    private final Path basePath;

    public LocalClassSource(Path basePath) {
        this.basePath = Objects.requireNonNull(basePath, "basePath cannot be null");
    }

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

    public Path getBasePath() {
        return basePath;
    }
}
