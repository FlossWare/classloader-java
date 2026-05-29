package org.flossware.classloader.util;

/**
 * Common constants used throughout the ApplicationClassLoader library.
 *
 * <p>This class provides shared constant values for buffer sizes, timeouts,
 * and other configuration parameters used across multiple ClassSource implementations.</p>
 */
public final class ClassLoaderConstants {

    private ClassLoaderConstants() {
        throw new AssertionError("Utility class - do not instantiate");
    }

    /**
     * Default buffer size for reading class data (8KB).
     *
     * <p>This is a common default for I/O operations because:</p>
     * <ul>
     *   <li>Matches most filesystem block sizes</li>
     *   <li>Good balance between memory usage and I/O performance</li>
     *   <li>Standard in many Java libraries (BufferedInputStream default is 8192)</li>
     * </ul>
     */
    public static final int DEFAULT_BUFFER_SIZE = 8192;
}
