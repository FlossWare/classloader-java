package org.flossware.classloader;

import java.io.IOException;

/**
 * Interface for class loading sources.
 * Implementations provide class bytecode from various sources (local files, HTTP, cloud storage, etc.).
 * This is the core abstraction that enables ApplicationClassLoader's multi-protocol support.
 *
 * <h2>Implementation Contract</h2>
 *
 * <h3>Thread Safety</h3>
 * <p>Implementations MUST be thread-safe if used with ApplicationClassLoader, which may load classes
 * concurrently from multiple threads.</p>
 *
 * <h3>Return Values and Exceptions</h3>
 * <ul>
 *   <li>{@link #loadClassData(String)} MUST return non-null bytecode if the class exists</li>
 *   <li>{@link #loadClassData(String)} MUST throw {@link IOException} if:
 *     <ul>
 *       <li>The class does not exist in this source</li>
 *       <li>A network/IO error occurs</li>
 *       <li>Authentication fails</li>
 *       <li>The class file is corrupted or unreadable</li>
 *     </ul>
 *   </li>
 *   <li>Implementations MUST NOT return null from {@link #loadClassData(String)}</li>
 * </ul>
 *
 * <h3>Performance Characteristics</h3>
 * <ul>
 *   <li>{@link #canLoad(String)} may be expensive for remote sources (network calls)</li>
 *   <li>ApplicationClassLoader optimizes by skipping {@link #canLoad(String)} checks for remote sources</li>
 *   <li>For best performance, {@link #canLoad(String)} should return quickly or always return true</li>
 * </ul>
 *
 * <h3>Consistency Requirements</h3>
 * <ul>
 *   <li>If {@link #canLoad(String)} returns true, {@link #loadClassData(String)} SHOULD succeed</li>
 *   <li>If {@link #canLoad(String)} returns false, {@link #loadClassData(String)} SHOULD throw IOException</li>
 *   <li>The same class loaded twice SHOULD return identical bytecode (for caching correctness)</li>
 * </ul>
 */
public interface ClassSource {
    /**
     * Loads the bytecode for the specified class.
     *
     * <p><b>Contract:</b></p>
     * <ul>
     *   <li>MUST return non-null bytecode if the class exists</li>
     *   <li>MUST throw IOException if the class doesn't exist or cannot be loaded</li>
     *   <li>MUST NOT return null under any circumstances</li>
     *   <li>SHOULD return identical bytecode if called multiple times for the same class</li>
     *   <li>MUST be thread-safe if used with ApplicationClassLoader</li>
     * </ul>
     *
     * <p><b>Error Handling:</b></p>
     * <p>Throw IOException for all error conditions including:</p>
     * <ul>
     *   <li>Class not found in this source (404)</li>
     *   <li>Network errors, timeouts</li>
     *   <li>Authentication failures (401, 403)</li>
     *   <li>Server errors (500, 503)</li>
     *   <li>Malformed or corrupted class files</li>
     * </ul>
     *
     * @param className The fully qualified class name (e.g., "com.example.MyClass")
     *                  Must not be null.
     * @return The class bytecode as a byte array, never null
     * @throws IOException if the class cannot be loaded for any reason (including class not found)
     * @throws NullPointerException if className is null (optional, implementations may throw IOException instead)
     */
    byte[] loadClassData(String className) throws IOException;

    /**
     * Checks if this source can load the specified class.
     *
     * <p><b>Performance Warning:</b> This method may NOT be lightweight for remote sources.
     * Remote implementations typically make network calls (HTTP HEAD, stat operations) which
     * can be as expensive as {@link #loadClassData(String)}. For this reason, ApplicationClassLoader
     * may skip this check and call {@link #loadClassData(String)} directly for remote sources.</p>
     *
     * <p><b>Implementation Guidance:</b></p>
     * <ul>
     *   <li><b>Local sources:</b> Check if file/resource exists (fast)</li>
     *   <li><b>Remote sources:</b> Consider returning true always to avoid expensive network checks</li>
     *   <li><b>Database sources:</b> Execute lightweight query (table existence check, not full scan)</li>
     *   <li><b>When in doubt:</b> Return true and let {@link #loadClassData(String)} throw IOException if needed</li>
     * </ul>
     *
     * <p><b>Return Value Semantics:</b></p>
     * <ul>
     *   <li>{@code true} - Class likely exists, proceed to call {@link #loadClassData(String)}</li>
     *   <li>{@code false} - Class definitely does not exist, skip this source</li>
     *   <li><b>Not an error indicator:</b> Cannot distinguish "class not found" from "network error"</li>
     * </ul>
     *
     * <p><b>Contract:</b></p>
     * <ul>
     *   <li>SHOULD return true if {@link #loadClassData(String)} would succeed</li>
     *   <li>SHOULD return false if {@link #loadClassData(String)} would throw IOException for "not found"</li>
     *   <li>SHOULD NOT throw exceptions - return false on errors</li>
     *   <li>MAY return true always if checking is expensive (acceptable trade-off)</li>
     * </ul>
     *
     * @param className The fully qualified class name to check
     * @return true if this source might be able to load the class, false if definitely cannot
     */
    boolean canLoad(String className);

    /**
     * Returns a human-readable description of this class source.
     * Used for debugging, logging, and error messages.
     *
     * <p><b>Examples:</b></p>
     * <ul>
     *   <li>{@code "LocalClassSource[/opt/classes]"}</li>
     *   <li>{@code "RemoteClassSource[https://example.com/classes, auth=Bearer]"}</li>
     *   <li>{@code "MavenRepositoryClassSource[https://repo1.maven.org/maven2/, artifacts=3, auth=none]"}</li>
     *   <li>{@code "DatabaseClassSource[jdbc:postgresql://localhost/mydb, table=class_files]"}</li>
     * </ul>
     *
     * <p><b>Contract:</b></p>
     * <ul>
     *   <li>MUST return a non-null string</li>
     *   <li>SHOULD include the source type and key configuration details</li>
     *   <li>SHOULD be concise but informative (one line preferred)</li>
     *   <li>MUST NOT include sensitive data (passwords, tokens) in the description</li>
     * </ul>
     *
     * @return A description string, never null
     */
    String getDescription();

    /**
     * Loads a non-class resource by its path (e.g., "com/example/config.properties").
     *
     * <p>Unlike {@link #loadClassData(String)}, which takes a fully qualified class name
     * and converts it to a path internally, this method takes a resource path directly
     * (using '/' separators, no leading '/').</p>
     *
     * <p>The default implementation returns {@code null}, meaning this source does not
     * support resource loading. Implementations that store resources alongside classes
     * (e.g., filesystem, JAR, cloud storage) should override this method.</p>
     *
     * @param resourceName The resource path (e.g., "com/example/config.properties")
     * @return The resource data as a byte array, or null if the resource is not found
     * @throws IOException if an I/O error occurs while loading the resource
     */
    default byte[] loadResourceData(String resourceName) throws IOException {
        return null;
    }
}
