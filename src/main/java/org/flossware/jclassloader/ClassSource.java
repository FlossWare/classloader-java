package org.flossware.jclassloader;

import java.io.IOException;

/**
 * Interface for class loading sources.
 * Implementations provide class bytecode from various sources (local files, HTTP, cloud storage, etc.).
 * This is the core abstraction that enables JClassLoader's multi-protocol support.
 */
public interface ClassSource {
    /**
     * Loads the bytecode for the specified class.
     *
     * @param className The fully qualified class name (e.g., "com.example.MyClass")
     * @return The class bytecode as a byte array
     * @throws IOException if the class cannot be loaded from this source
     */
    byte[] loadClassData(String className) throws IOException;

    /**
     * Checks if this source can load the specified class.
     * This is a lighter-weight check than actually loading the class.
     *
     * @param className The fully qualified class name to check
     * @return true if this source can load the class, false otherwise
     */
    boolean canLoad(String className);

    /**
     * Returns a human-readable description of this class source.
     * Used for debugging and logging.
     *
     * @return A description string (e.g., "LocalClassSource[/opt/classes]")
     */
    String getDescription();
}
