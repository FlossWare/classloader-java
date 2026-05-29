/**
 * Lifecycle event listeners for class loading operations.
 *
 * <p>This package provides event listeners that can observe and react to
 * class loading events, enabling monitoring, logging, and metrics collection.</p>
 *
 * <h2>Key Classes</h2>
 * <ul>
 *   <li>{@link org.flossware.jclassloader.lifecycle.ClassLoaderLifecycleListener} - Listener interface</li>
 *   <li>{@link org.flossware.jclassloader.lifecycle.ClassLoadEvent} - Event data for successful loads</li>
 *   <li>{@link org.flossware.jclassloader.lifecycle.LoggingListener} - Logs class loading activity</li>
 *   <li>{@link org.flossware.jclassloader.lifecycle.ResourceTrackingListener} - Tracks resource usage</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // Add logging listener
 * ApplicationClassLoader loader = ApplicationClassLoader.builder()
 *     .addRemoteSource("https://example.com/classes/")
 *     .addLoggingListener(true)  // verbose mode
 *     .build();
 *
 * // Custom listener
 * loader.addListener(new ClassLoaderLifecycleListener() {
 *     public void onClassLoaded(ClassLoadEvent event) {
 *         System.out.println("Loaded: " + event.getClassName() +
 *                          " from " + event.getSource().getDescription() +
 *                          " in " + event.getLoadTimeNanos() / 1_000_000 + "ms");
 *     }
 * });
 * }</pre>
 *
 * @since 1.0
 */
package org.flossware.classloader.lifecycle;
