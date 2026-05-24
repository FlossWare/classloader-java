package org.flossware.jclassloader;

/**
 * Interface for verifying bytecode before loading into the JVM.
 * Implementations can perform security checks, integrity validation, or authenticity verification.
 */
@FunctionalInterface
public interface BytecodeVerifier {
    /**
     * Verifies the bytecode for a class.
     *
     * @param className The fully-qualified class name (e.g., "com.example.MyClass")
     * @param bytecode The raw bytecode to verify
     * @throws SecurityException if verification fails
     */
    void verify(String className, byte[] bytecode) throws SecurityException;
}
