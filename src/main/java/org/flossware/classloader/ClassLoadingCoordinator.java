package org.flossware.classloader;

import org.flossware.classloader.cache.ClassCache;
import org.flossware.classloader.lifecycle.ClassLoadEvent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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

    ClassLoadingCoordinator(List<ClassSource> classSources, ClassCache cache,
            boolean useCache, BytecodeVerifier bytecodeVerifier,
            ClassLoaderEventDispatcher eventDispatcher) {
        this.classSources = Objects.requireNonNull(classSources, "classSources cannot be null");
        this.cache = cache;
        this.useCache = useCache;
        this.bytecodeVerifier = bytecodeVerifier;
        this.eventDispatcher = Objects.requireNonNull(eventDispatcher, "eventDispatcher cannot be null");
    }

    /**
     * Loads a class from cache or available sources.
     *
     * @param name The fully qualified class name
     * @return The class bytecode, or null if not found
     * @throws ClassNotFoundException if validation or verification fails
     */
    byte[] loadClass(String name) throws ClassNotFoundException {
        Objects.requireNonNull(name, "name cannot be null");

        byte[] cachedData = loadFromCache(name);
        if (cachedData != null) {
            return cachedData;
        }

        return loadFromSources(name);
    }

    private byte[] loadFromCache(String name) throws ClassNotFoundException {
        if (!useCache || cache == null) {
            return null;
        }
        byte[] cachedData = cache.get(name);
        if (cachedData == null) {
            return null;
        }
        eventDispatcher.fireClassCacheHit(name);
        verifyBytecode(name, cachedData);
        return cachedData;
    }

    private byte[] loadFromSources(String name) throws ClassNotFoundException {
        List<String> attemptedSources = new ArrayList<>();
        List<String> failureReasons = new ArrayList<>();

        for (ClassSource source : classSources) {
            byte[] classData = tryLoadFromSource(name, source, attemptedSources, failureReasons);
            if (classData != null) {
                return classData;
            }
        }

        throwClassNotFound(name, attemptedSources, failureReasons);
        return null; // unreachable
    }

    private byte[] tryLoadFromSource(String name, ClassSource source,
                                     List<String> attemptedSources, List<String> failureReasons)
            throws ClassNotFoundException {
        if (!source.canLoad(name)) {
            return null;
        }

        attemptedSources.add(source.getDescription());

        try {
            return loadAndValidateFromSource(name, source);
        } catch (IOException e) {
            failureReasons.add(source.getDescription() + ": " + e.getMessage());
            return null;
        }
    }

    private byte[] loadAndValidateFromSource(String name, ClassSource source)
            throws IOException, ClassNotFoundException {
        long loadStartTime = System.nanoTime();
        byte[] classData = source.loadClassData(name);

        if (classData == null) {
            return null;
        }

        long loadTime = System.nanoTime() - loadStartTime;
        validateClassData(name, classData);
        verifyBytecode(name, classData);

        if (useCache && cache != null) {
            tryCacheClassData(name, classData);
        }

        eventDispatcher.fireClassLoaded(new ClassLoadEvent(name, source, loadTime, classData.length));
        return classData;
    }

    private void throwClassNotFound(String name, List<String> attemptedSources,
                                    List<String> failureReasons) throws ClassNotFoundException {
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
        if (bytecodeVerifier == null) {
            return;
        }
        try {
            bytecodeVerifier.verify(name, classData);
        } catch (SecurityException e) {
            ClassNotFoundException ex = new ClassNotFoundException(
                "Bytecode verification failed: " + name, e);
            eventDispatcher.fireClassLoadFailed(name, ex);
            throw ex;
        }
    }

    /**
     * Attempts to cache class data, logging failures but not throwing exceptions.
     * Cache failures should never break class loading, so IOException from
     * cache operations and specific runtime exceptions from event dispatching are
     * caught and logged. Errors (e.g., OutOfMemoryError) are allowed to propagate
     * since they indicate unrecoverable JVM-level problems.
     */
    private void tryCacheClassData(String name, byte[] classData) {
        try {
            cache.put(name, classData);
            eventDispatcher.fireClassCached(name, classData);
        } catch (IOException e) {
            ClassLoaderLogger.logError("Failed to cache class " + name + ": " + e.getMessage());
            eventDispatcher.fireClassCacheFailed(name, e);
        } catch (IllegalStateException | NullPointerException | UnsupportedOperationException e) {
            ClassLoaderLogger.logError("Error caching class " + name + ": " + e.getMessage());
            eventDispatcher.fireClassCacheFailed(name, e);
        } catch (Error e) {
            // Catch critical errors (OutOfMemoryError, StackOverflowError, etc.) to prevent
            // cache failures from breaking class loading, but log them as unexpected
            ClassLoaderLogger.logError("Critical error while caching class " + name + ": " + e.getMessage());
            eventDispatcher.fireClassCacheFailed(name, e);
            // Do not rethrow - allow class loading to continue despite cache failure
        }
    }
}
