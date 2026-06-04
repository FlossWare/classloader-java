package org.flossware.classloader;

import org.flossware.classloader.cache.ClassCache;
import org.flossware.classloader.lifecycle.ClassLoadEvent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Coordinates the class loading process including validation, verification, and caching.
 *
 * Responsibilities:
 * - Orchestrating class loading from multiple sources
 * - Validating class bytecode (magic number and size checks)
 * - Verifying bytecode security (if verifier configured)
 * - Managing class caching
 * - Tracking load events
 */
class ClassLoadingCoordinator {
    /** Minimum size of a valid Java class file in bytes (for magic number check) */
    private static final int JAVA_CLASS_MIN_SIZE = 4;

    /** Java class file magic number (0xCAFEBABE) - first byte */
    private static final byte CLASS_MAGIC_BYTE_0 = (byte) 0xCA;

    /** Java class file magic number (0xCAFEBABE) - second byte */
    private static final byte CLASS_MAGIC_BYTE_1 = (byte) 0xFE;

    /** Java class file magic number (0xCAFEBABE) - third byte */
    private static final byte CLASS_MAGIC_BYTE_2 = (byte) 0xBA;

    /** Java class file magic number (0xCAFEBABE) - fourth byte */
    private static final byte CLASS_MAGIC_BYTE_3 = (byte) 0xBE;

    private final List<ClassSource> classSources;
    private final ClassCache cache;
    private final boolean useCache;
    private final BytecodeVerifier bytecodeVerifier;
    private final ClassLoaderEventDispatcher eventDispatcher;

    ClassLoadingCoordinator(List<ClassSource> classSources, ClassCache cache, boolean useCache,
                           BytecodeVerifier bytecodeVerifier, ClassLoaderEventDispatcher eventDispatcher) {
        this.classSources = classSources;
        this.cache = cache;
        this.useCache = useCache;
        this.bytecodeVerifier = bytecodeVerifier;
        this.eventDispatcher = eventDispatcher;
    }

    /**
     * Loads a class from cache or available sources.
     *
     * @param name The fully qualified class name
     * @return The class bytecode, or null if not found
     * @throws ClassNotFoundException if validation or verification fails
     */
    byte[] loadClass(String name) throws ClassNotFoundException {
        // Check cache first
        if (useCache && cache != null) {
            byte[] cachedData = cache.get(name);
            if (cachedData != null) {
                eventDispatcher.fireClassCacheHit(name);
                verifyBytecode(name, cachedData);
                return cachedData;
            }
        }

        // Load from sources
        List<String> attemptedSources = new ArrayList<>();
        List<String> failureReasons = new ArrayList<>();

        for (ClassSource source : classSources) {
            if (!source.canLoad(name)) {
                continue;
            }

            attemptedSources.add(source.getDescription());

            try {
                long loadStartTime = System.nanoTime();
                byte[] classData = source.loadClassData(name);

                if (classData != null) {
                    long loadTime = System.nanoTime() - loadStartTime;

                    validateClassData(name, classData);
                    verifyBytecode(name, classData);

                    if (useCache && cache != null) {
                        tryCacheClassData(name, classData);
                    }

                    eventDispatcher.fireClassLoaded(new ClassLoadEvent(name, source, loadTime, classData.length));
                    return classData;
                }
            } catch (IOException e) {
                failureReasons.add(source.getDescription() + ": " + e.getMessage());
            }
        }

        // Build detailed error message
        String errorMsg = "Class not found: " + name +
                         " (tried " + attemptedSources.size() + " sources";
        if (!failureReasons.isEmpty()) {
            errorMsg += ", failures: " + String.join("; ", failureReasons);
        }
        errorMsg += ")";

        ClassNotFoundException ex = new ClassNotFoundException(errorMsg);
        eventDispatcher.fireClassLoadFailed(name, ex);
        throw ex;
    }

    /**
     * Validates class bytecode data (magic number and minimum size).
     */
    private void validateClassData(String name, byte[] classData) throws ClassNotFoundException {
        if (classData.length < JAVA_CLASS_MIN_SIZE) {
            throw new ClassNotFoundException(
                name + ": Invalid class data (too small: " + classData.length + " bytes)");
        }

        // Check magic number (0xCAFEBABE)
        if (classData[0] != CLASS_MAGIC_BYTE_0 || classData[1] != CLASS_MAGIC_BYTE_1 ||
            classData[2] != CLASS_MAGIC_BYTE_2 || classData[3] != CLASS_MAGIC_BYTE_3) {
            throw new ClassNotFoundException(
                name + ": Invalid class file magic number");
        }
    }

    /**
     * Verifies bytecode if verifier is configured.
     */
    private void verifyBytecode(String name, byte[] classData) throws ClassNotFoundException {
        if (bytecodeVerifier != null) {
            try {
                bytecodeVerifier.verify(name, classData);
            } catch (SecurityException e) {
                ClassNotFoundException ex = new ClassNotFoundException(
                    "Bytecode verification failed: " + name, e);
                eventDispatcher.fireClassLoadFailed(name, ex);
                throw ex;
            }
        }
    }

    /**
     * Attempts to cache class data, logging failures but not throwing exceptions.
     */
    private void tryCacheClassData(String name, byte[] classData) {
        try {
            cache.put(name, classData);
            eventDispatcher.fireClassCached(name, classData);
        } catch (IOException e) {
            ClassLoaderLogger.logError("Failed to cache class " + name + ": " + e.getMessage());
            eventDispatcher.fireClassCacheFailed(name, e);
        } catch (Throwable e) {
            ClassLoaderLogger.logError("Unexpected error caching class " + name + ": " + e.getMessage());
            eventDispatcher.fireClassCacheFailed(name, e);
        }
    }
}
