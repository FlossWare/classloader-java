package org.flossware.classloader;

import org.flossware.classloader.cache.ClassCache;
import org.flossware.classloader.delegation.CustomDelegation;
import org.flossware.classloader.delegation.DelegationStrategy;
import org.flossware.classloader.delegation.ParentFirstDelegation;
import org.flossware.classloader.delegation.ParentLastDelegation;
import org.flossware.classloader.lifecycle.ClassLoaderLifecycleListener;
import org.flossware.classloader.lifecycle.LoggingListener;
import org.flossware.classloader.lifecycle.ResourceTrackingListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Builder for constructing ApplicationClassLoader instances with fluent API.
 *
 * Supports configuration of:
 * - Multiple class sources (local, HTTP, cloud storage, databases, Maven, etc.)
 * - Class caching (in-memory or filesystem-based)
 * - Delegation strategy (parent-first, parent-last, custom)
 * - Lifecycle listeners (logging, resource tracking, metrics)
 * - Bytecode verification (checksum validation)
 *
 * Basic Example:
 * <pre>{@code
 * ApplicationClassLoader loader = ApplicationClassLoaderBuilder.create()
 *     .addLocalSource("/path/to/classes")
 *     .addRemoteSource("https://example.com/classes")
 *     .addMavenCentral("com.example:my-lib:1.0")
 *     .parentLast()
 *     .build();
 * }</pre>
 *
 * Advanced Example with Caching and Listeners:
 * <pre>{@code
 * ApplicationClassLoader loader = ApplicationClassLoaderBuilder.create()
 *     .addRemoteJar("https://cdn.example.com/lib.jar")
 *     .cache(new FileSystemCache(Paths.get("/tmp/class-cache")))
 *     .addLoggingListener(true)
 *     .trackResources()
 *     .bytecodeVerifier(new ChecksumValidator(checksumMap))
 *     .build();
 * }</pre>
 *
 * Defaults:
 * - parent: Thread.currentThread().getContextClassLoader()
 * - delegation: ParentFirstDelegation (standard Java behavior)
 * - cache: In-memory cache (if useCache=true)
 * - bytecodeVerifier: null (no verification)
 */
public class ApplicationClassLoaderBuilder {
    private static final int MAX_CLASS_SOURCES = 100;

    private ClassLoader parent;
    private final List<ClassSource> classSources = new ArrayList<>();
    private ClassCache cache;
    private boolean useCache = true;
    private DelegationStrategy delegationStrategy = new ParentFirstDelegation();
    private final List<ClassLoaderLifecycleListener> listeners = new ArrayList<>();
    private BytecodeVerifier bytecodeVerifier;

    /**
     * Creates a new builder instance.
     */
    public static ApplicationClassLoaderBuilder create() {
        return new ApplicationClassLoaderBuilder();
    }

    /**
     * Sets the parent ClassLoader for delegation.
     * If not set, defaults to Thread.currentThread().getContextClassLoader().
     *
     * @param parent The parent ClassLoader (null to use bootstrap classloader as parent)
     * @return this builder
     */
    public ApplicationClassLoaderBuilder parent(ClassLoader parent) {
        this.parent = parent;
        return this;
    }

    /**
     * Adds a custom ClassSource to the loading chain.
     * Sources are tried in the order they're added. Maximum 100 sources.
     *
     * @param source The ClassSource to add
     * @return this builder
     * @throws NullPointerException if source is null
     * @throws IllegalStateException if MAX_CLASS_SOURCES (100) is exceeded
     */
    public ApplicationClassLoaderBuilder addClassSource(ClassSource source) {
        Objects.requireNonNull(source, "source cannot be null");
        if (classSources.size() >= MAX_CLASS_SOURCES) {
            throw new IllegalStateException(
                "Too many class sources (max " + MAX_CLASS_SOURCES + ")");
        }
        this.classSources.add(source);
        return this;
    }

    /**
     * Adds a local filesystem directory as a class source.
     * Classes are loaded from path + "/" + className.replace('.', '/') + ".class"
     *
     * @param path Base directory path (e.g., "/opt/app/classes" or "target/classes")
     * @return this builder
     */
    public ApplicationClassLoaderBuilder addLocalSource(String path) {
        return addClassSource(new LocalClassSource(path));
    }

    /**
     * Adds an HTTP/HTTPS URL as a class source (no authentication).
     * Classes are loaded from baseUrl + "/" + className.replace('.', '/') + ".class"
     *
     * @param baseUrl Base URL (e.g., "https://cdn.example.com/classes")
     * @return this builder
     */
    public ApplicationClassLoaderBuilder addRemoteSource(String baseUrl) {
        return addClassSource(new RemoteClassSource(baseUrl));
    }

    /**
     * Adds an HTTP/HTTPS URL as a class source with authentication.
     *
     * @param baseUrl Base URL
     * @param authConfig Authentication configuration (basic auth, bearer token, API key, etc.)
     * @return this builder
     */
    public ApplicationClassLoaderBuilder addRemoteSource(String baseUrl, AuthConfig authConfig) {
        return addClassSource(new RemoteClassSource(baseUrl, authConfig));
    }

    /**
     * Adds a remote JAR file as a class source (no authentication).
     * Downloads and caches the entire JAR, then loads classes from it.
     *
     * @param jarUrl URL to JAR file (e.g., "https://cdn.example.com/lib-1.0.jar")
     * @return this builder
     */
    public ApplicationClassLoaderBuilder addRemoteJar(String jarUrl) {
        return addClassSource(new RemoteJarClassSource(jarUrl));
    }

    /**
     * Adds a remote JAR file as a class source with authentication.
     * Downloads and caches the entire JAR, then loads classes from it.
     *
     * @param jarUrl URL to JAR file (e.g., "https://cdn.example.com/lib-1.0.jar")
     * @param authConfig Authentication configuration (basic auth, bearer token, etc.)
     * @return this builder
     */
    public ApplicationClassLoaderBuilder addRemoteJar(String jarUrl, AuthConfig authConfig) {
        return addClassSource(new RemoteJarClassSource(jarUrl, authConfig));
    }

    /**
     * Adds a Nexus RAW repository as a class source without authentication.
     * Loads individual .class files directly from the repository.
     *
     * @param nexusUrl The Nexus server URL (e.g., "https://nexus.example.com")
     * @param repository The repository name
     * @return this builder
     */
    public ApplicationClassLoaderBuilder addNexusRawSource(String nexusUrl, String repository) {
        return addClassSource(new NexusClassSource(nexusUrl, repository, NexusClassSource.NexusMode.RAW));
    }

    /**
     * Adds a Nexus RAW repository as a class source with authentication.
     * Loads individual .class files directly from the repository.
     *
     * @param nexusUrl The Nexus server URL (e.g., "https://nexus.example.com")
     * @param repository The repository name
     * @param authConfig Authentication configuration
     * @return this builder
     */
    public ApplicationClassLoaderBuilder addNexusRawSource(String nexusUrl, String repository, AuthConfig authConfig) {
        return addClassSource(new NexusClassSource(nexusUrl, repository, NexusClassSource.NexusMode.RAW, authConfig));
    }

    /**
     * Adds a pre-configured MavenNexusClassSource as a class source.
     * Loads classes from Maven artifacts stored in a Nexus repository.
     *
     * @param source The MavenNexusClassSource to add
     * @return this builder
     */
    public ApplicationClassLoaderBuilder addNexusMavenSource(MavenNexusClassSource source) {
        return addClassSource(source);
    }

    /**
     * Adds Maven Central as a class source for specified artifacts.
     * Coordinates format: "groupId:artifactId:version"
     *
     * Example:
     * <pre>{@code
     * builder.addMavenCentral(
     *     "com.google.guava:guava:32.1.0-jre",
     *     "org.apache.commons:commons-lang3:3.12.0"
     * )
     * }</pre>
     *
     * @param artifactCoordinates Maven coordinates (groupId:artifactId:version)
     * @return this builder
     */
    public ApplicationClassLoaderBuilder addMavenCentral(String... artifactCoordinates) {
        MavenRepositoryClassSource.Builder builder = MavenRepositoryClassSource.builder()
            .mavenCentral();
        for (String coords : artifactCoordinates) {
            builder.addArtifact(coords);
        }
        return addClassSource(builder.build());
    }

    /**
     * Adds a custom Maven repository as a class source with specified artifacts.
     * Coordinates format: "groupId:artifactId:version"
     *
     * @param repositoryUrl The Maven repository URL (e.g., "https://nexus.example.com/repository/maven-releases/")
     * @param artifactCoordinates Maven coordinates (groupId:artifactId:version)
     * @return this builder
     */
    public ApplicationClassLoaderBuilder addMavenRepository(String repositoryUrl, String... artifactCoordinates) {
        MavenRepositoryClassSource.Builder builder = MavenRepositoryClassSource.builder()
            .repositoryUrl(repositoryUrl);
        for (String coords : artifactCoordinates) {
            builder.addArtifact(coords);
        }
        return addClassSource(builder.build());
    }

    /**
     * Adds a JDBC database as a class source.
     * Class bytecode is loaded from the specified table, column, and row structure.
     *
     * @param dataSource The JDBC DataSource (use a pooled DataSource for production)
     * @param tableName The table name containing class data
     * @param classNameColumn The column containing fully qualified class names
     * @param classBytesColumn The column containing class bytecode (BLOB/BINARY)
     * @return this builder
     */
    public ApplicationClassLoaderBuilder addDatabaseSource(javax.sql.DataSource dataSource, String tableName,
                                        String classNameColumn, String classBytesColumn) {
        return addClassSource(new DatabaseClassSource(dataSource, tableName,
                                                     classNameColumn, classBytesColumn));
    }

    /**
     * Adds a pre-configured RestApiClassSource as a class source.
     * Loads classes from a custom REST API endpoint.
     *
     * @param source The RestApiClassSource to add
     * @return this builder
     */
    public ApplicationClassLoaderBuilder addRestApiSource(RestApiClassSource source) {
        return addClassSource(source);
    }

    /**
     * Adds a cloud storage provider as a class source.
     * Supports S3, Azure Blob Storage, GCS, Google Drive, Dropbox, and OneDrive.
     *
     * @param client The cloud storage client to use
     * @return this builder
     */
    public ApplicationClassLoaderBuilder addCloudStorage(org.flossware.cloud.storage.CloudStorageClient client) {
        return addClassSource(new CloudStorageClassSource(client));
    }

    /**
     * Sets the class cache implementation for caching loaded class bytecode.
     * If not set, an in-memory cache is used when caching is enabled.
     *
     * @param cache The cache implementation (e.g., FileSystemCache), or null to use default
     * @return this builder
     */
    public ApplicationClassLoaderBuilder cache(ClassCache cache) {
        this.cache = cache;
        return this;
    }

    /**
     * Enables or disables class caching. When enabled, loaded class bytecode is cached
     * to avoid repeated loading from sources. Enabled by default.
     *
     * @param useCache true to enable caching, false to disable
     * @return this builder
     */
    public ApplicationClassLoaderBuilder useCache(boolean useCache) {
        this.useCache = useCache;
        return this;
    }

    /**
     * Sets a custom delegation strategy.
     * Determines whether to check parent ClassLoader before or after custom sources.
     *
     * @param strategy The delegation strategy
     * @return this builder
     * @throws NullPointerException if strategy is null
     */
    public ApplicationClassLoaderBuilder delegationStrategy(DelegationStrategy strategy) {
        this.delegationStrategy = Objects.requireNonNull(strategy, "delegationStrategy cannot be null");
        return this;
    }

    /**
     * Uses parent-first delegation (standard Java ClassLoader behavior).
     * Checks parent ClassLoader first, then custom sources. This is the default.
     *
     * @return this builder
     */
    public ApplicationClassLoaderBuilder parentFirst() {
        return delegationStrategy(new ParentFirstDelegation());
    }

    /**
     * Uses parent-last delegation (checks custom sources before parent).
     * Allows overriding classes from parent ClassLoader, except for specified prefixes
     * which are always loaded from parent (e.g., JDK classes).
     *
     * Default always-parent prefixes include:
     * - java. (Java platform classes)
     * - javax. (Java extensions)
     * - sun. (Sun/Oracle internal classes)
     * - jdk. (JDK internal classes)
     *
     * Example:
     * <pre>{@code
     * builder.parentLast("com.example.core.")  // Always load com.example.core.* from parent
     * }</pre>
     *
     * @param alwaysParentPrefixes Additional prefixes to always load from parent
     * @return this builder
     */
    public ApplicationClassLoaderBuilder parentLast(String... alwaysParentPrefixes) {
        return delegationStrategy(new ParentLastDelegation(alwaysParentPrefixes));
    }

    /**
     * Uses a custom delegation strategy based on a predicate.
     * The predicate determines per-class whether to use parent-first or parent-last delegation.
     *
     * @param parentFirstPredicate Predicate returning true for classes that should use parent-first
     * @return this builder
     * @throws NullPointerException if parentFirstPredicate is null
     */
    public ApplicationClassLoaderBuilder customDelegation(Predicate<String> parentFirstPredicate) {
        return delegationStrategy(new CustomDelegation(parentFirstPredicate));
    }

    /**
     * Adds a lifecycle listener to receive class loading events.
     *
     * @param listener The listener to add
     * @return this builder
     * @throws NullPointerException if listener is null
     */
    public ApplicationClassLoaderBuilder addListener(ClassLoaderLifecycleListener listener) {
        this.listeners.add(Objects.requireNonNull(listener, "listener cannot be null"));
        return this;
    }

    /**
     * Adds a non-verbose logging listener that logs class load events to System.out.
     *
     * @return this builder
     */
    public ApplicationClassLoaderBuilder addLoggingListener() {
        return addListener(new LoggingListener());
    }

    /**
     * Adds a logging listener with configurable verbosity.
     * When verbose, also logs cache hits, caching operations, and resource events.
     *
     * @param verbose true for verbose logging, false for load events only
     * @return this builder
     */
    public ApplicationClassLoaderBuilder addLoggingListener(boolean verbose) {
        return addListener(new LoggingListener(verbose));
    }

    /**
     * Adds a resource tracking listener that tracks loaded classes, bytes, and cache hits.
     * Useful for monitoring and cleanup scenarios.
     *
     * @return this builder
     */
    public ApplicationClassLoaderBuilder trackResources() {
        return addListener(new ResourceTrackingListener());
    }

    /**
     * Sets a bytecode verifier for security validation.
     * Verifies loaded bytecode before defining classes (e.g., checksum validation).
     *
     * Example:
     * <pre>{@code
     * Map<String, String> checksums = new HashMap<>();
     * checksums.put("com.example.MyClass", "sha256:abc123...");
     *
     * builder.bytecodeVerifier(new ChecksumValidator(checksums));
     * }</pre>
     *
     * @param verifier The bytecode verifier (null to disable verification)
     * @return this builder
     */
    public ApplicationClassLoaderBuilder bytecodeVerifier(BytecodeVerifier verifier) {
        this.bytecodeVerifier = verifier;
        return this;
    }

    /**
     * Builds the ApplicationClassLoader with configured settings.
     *
     * @return A new ApplicationClassLoader instance
     * @throws IllegalStateException if no class sources are configured
     */
    public ApplicationClassLoader build() {
        if (classSources.isEmpty()) {
            throw new IllegalStateException("At least one class source must be configured");
        }

        return new ApplicationClassLoader(this);
    }

    // Package-private accessors for ApplicationClassLoader
    ClassLoader getParent() {
        return parent;
    }

    List<ClassSource> getClassSources() {
        return classSources;
    }

    ClassCache getCache() {
        return cache;
    }

    boolean isUseCache() {
        return useCache;
    }

    DelegationStrategy getDelegationStrategy() {
        return delegationStrategy;
    }

    List<ClassLoaderLifecycleListener> getListeners() {
        return listeners;
    }

    BytecodeVerifier getBytecodeVerifier() {
        return bytecodeVerifier;
    }
}
