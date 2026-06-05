package org.flossware.classloader;

import org.flossware.classloader.cache.ClassCache;
import org.flossware.classloader.delegation.DelegationStrategy;
import org.flossware.classloader.delegation.ParentFirstDelegation;
import org.flossware.classloader.lifecycle.ClassLoaderLifecycleListener;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Custom ClassLoader supporting multiple class sources with caching and lifecycle management.
 * Implements AutoCloseable for proper resource cleanup and memory leak prevention.
 *
 * <h2>Memory Leak Prevention</h2>
 * <p><b>CRITICAL:</b> ClassLoaders can cause memory leaks if not properly managed. This ClassLoader
 * holds strong references to all loaded classes, which prevents those classes (and their static fields)
 * from being garbage collected until the ClassLoader itself is collected.</p>
 *
 * <h3>Always Close When Done</h3>
 * <p>Use try-with-resources to ensure proper cleanup:</p>
 * <pre>{@code
 * try (ApplicationClassLoader loader = ApplicationClassLoader.builder()
 *         .addLocalSource("/path/to/classes")
 *         .build()) {
 *     Class<?> clazz = loader.loadClass("com.example.MyClass");
 *     // Use the class
 * }  // Automatically closes and releases resources
 * }</pre>
 *
 * <h3>Hot Reload / Class Reloading Scenarios</h3>
 * <p>When implementing hot reload or dynamic class reloading:</p>
 * <ol>
 *   <li><b>Create a new ClassLoader for each reload</b> - Don't reuse ClassLoaders</li>
 *   <li><b>Close the old ClassLoader</b> - Call {@link #close()} on the old loader</li>
 *   <li><b>Clear all references</b> - Null out references to classes from the old loader</li>
 *   <li><b>Avoid static state</b> - Static fields in loaded classes prevent GC</li>
 *   <li><b>Be careful with ThreadLocal</b> - Can leak ClassLoader references</li>
 * </ol>
 *
 * <p><b>Example - Proper Hot Reload:</b></p>
 * <pre>{@code
 * // Old way (MEMORY LEAK - old ClassLoader never released):
 * ApplicationClassLoader loader = ApplicationClassLoader.builder()...build();
 * // ... use loader ...
 * loader = ApplicationClassLoader.builder()...build();  // OLD LOADER LEAKED!
 *
 * // Correct way:
 * ApplicationClassLoader oldLoader = currentLoader;
 * ApplicationClassLoader newLoader = ApplicationClassLoader.builder()...build();
 *
 * // Switch to new loader
 * currentLoader = newLoader;
 *
 * // Release old loader and all its classes
 * if (oldLoader != null) {
 *     oldLoader.close();  // Close resources
 *     oldLoader = null;   // Clear reference
 * }
 * System.gc();  // Suggest GC (doesn't guarantee collection)
 * }</pre>
 *
 * <h3>Memory Leak Detection</h3>
 * <p>To detect ClassLoader leaks in your application:</p>
 * <ul>
 *   <li>Use heap dumps and memory profilers (VisualVM, YourKit, JProfiler)</li>
 *   <li>Look for multiple instances of the same ClassLoader class</li>
 *   <li>Check for references from Thread contexts, static fields, or caches</li>
 *   <li>Monitor {@link #isClosed()} - closed loaders should be GC'd</li>
 * </ul>
 *
 * <h3>Common Memory Leak Causes</h3>
 * <ul>
 *   <li><b>ThreadLocal variables</b> holding class references</li>
 *   <li><b>Static caches</b> holding instances from dynamically loaded classes</li>
 *   <li><b>Logging frameworks</b> holding references to logger instances</li>
 *   <li><b>Thread pools</b> with threads created by loaded classes</li>
 *   <li><b>Weak/Soft reference caches</b> not cleared on reload</li>
 * </ul>
 *
 * @see #close()
 * @see #isClosed()
 */
public class ApplicationClassLoader extends ClassLoader implements AutoCloseable {
    private final List<ClassSource> classSources;
    private final ClassCache cache;
    private final boolean useCache;
    private final DelegationStrategy delegationStrategy;
    private final List<ClassLoaderLifecycleListener> listeners;
    private final BytecodeVerifier bytecodeVerifier;
    private volatile boolean closed = false;

    // Helper components
    private final ClassLoaderEventDispatcher eventDispatcher;
    private final ClassLoadingCoordinator loadingCoordinator;
    private final ClassLoaderResourceManager resourceManager;

    ApplicationClassLoader(ApplicationClassLoaderBuilder builder) {
        super(builder.getParent() != null ? builder.getParent() : getSystemClassLoader());
        this.classSources = new ArrayList<>(builder.getClassSources());
        this.cache = builder.getCache();
        this.useCache = builder.isUseCache() && cache != null;
        this.delegationStrategy = builder.getDelegationStrategy();
        this.listeners = new CopyOnWriteArrayList<>(builder.getListeners());
        this.bytecodeVerifier = builder.getBytecodeVerifier();

        // Initialize helper components
        this.eventDispatcher = new ClassLoaderEventDispatcher(this.listeners);
        this.loadingCoordinator = new ClassLoadingCoordinator(this.classSources, this.cache,
                                                             this.useCache, this.bytecodeVerifier,
                                                             this.eventDispatcher);
        this.resourceManager = new ClassLoaderResourceManager(this.classSources, this.cache,
                                                             this.eventDispatcher);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        // Check if already loaded
        Class<?> c = findLoadedClass(name);
        if (c != null) {
            return c;
        }

        // Use delegation strategy to load the class
        c = delegationStrategy.loadClass(name, getParent(), this::findClass);

        if (resolve) {
            resolveClass(c);
        }

        return c;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        if (closed) {
            throw new IllegalStateException("ApplicationClassLoader is closed");
        }

        // Use the loading coordinator to handle all class loading logic
        byte[] classData = loadingCoordinator.loadClass(name);
        return defineClass(name, classData, 0, classData.length);
    }

    /**
     * Closes this ClassLoader and releases all resources.
     * This includes closing all AutoCloseable class sources, cache, and notifying listeners.
     * After closing, this ClassLoader cannot load any more classes.
     *
     * @throws IOException if an error occurs while closing resources
     */
    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }

        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;

            // Use resource manager to handle all cleanup
            resourceManager.closeResources();
        }
    }

    /**
     * Checks if this ClassLoader is closed.
     *
     * @return true if closed, false otherwise
     */
    public boolean isClosed() {
        return closed;
    }

    /**
     * Gets the list of class sources configured for this ClassLoader.
     *
     * @return an unmodifiable list of class sources
     */
    public List<ClassSource> getClassSources() {
        return Collections.unmodifiableList(classSources);
    }

    /**
     * Gets the class cache used by this ClassLoader.
     *
     * @return the class cache, or null if no cache is configured
     */
    public ClassCache getCache() {
        return cache;
    }

    /**
     * Checks whether class caching is enabled.
     *
     * @return true if caching is enabled and a cache is configured, false otherwise
     */
    public boolean isCacheEnabled() {
        return useCache;
    }

    /**
     * Gets the delegation strategy used by this ClassLoader.
     *
     * @return the delegation strategy
     */
    public DelegationStrategy getDelegationStrategy() {
        return delegationStrategy;
    }

    /**
     * Gets the lifecycle listeners registered with this ClassLoader.
     *
     * @return an unmodifiable list of lifecycle listeners
     */
    public List<ClassLoaderLifecycleListener> getListeners() {
        return Collections.unmodifiableList(listeners);
    }

    /**
     * Gets the bytecode verifier used by this ClassLoader.
     *
     * @return the bytecode verifier, or null if no verifier is configured
     */
    public BytecodeVerifier getBytecodeVerifier() {
        return bytecodeVerifier;
    }

    /**
     * Creates a new builder for constructing ApplicationClassLoader instances.
     *
     * @return A new ApplicationClassLoaderBuilder with default configuration
     */
    public static ApplicationClassLoaderBuilder builder() {
        return ApplicationClassLoaderBuilder.create();
    }
}
