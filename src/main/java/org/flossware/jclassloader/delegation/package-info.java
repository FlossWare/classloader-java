/**
 * Delegation strategies for class loading.
 *
 * <p>This package provides different strategies for delegating class loading
 * between parent and child classloaders, enabling control over class resolution order.</p>
 *
 * <h2>Key Classes</h2>
 * <ul>
 *   <li>{@link org.flossware.jclassloader.delegation.DelegationStrategy} - Interface for delegation strategies</li>
 *   <li>{@link org.flossware.jclassloader.delegation.ParentFirstDelegation} - Standard Java delegation (parent first)</li>
 *   <li>{@link org.flossware.jclassloader.delegation.ParentLastDelegation} - Child-first delegation for isolation</li>
 *   <li>{@link org.flossware.jclassloader.delegation.CustomDelegation} - Custom predicate-based delegation</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // Parent-first (default Java behavior)
 * JClassLoader loader = JClassLoader.builder()
 *     .parentFirst()
 *     .build();
 *
 * // Parent-last for plugin isolation
 * JClassLoader loader = JClassLoader.builder()
 *     .parentLast("java.", "javax.")  // Always use parent for JDK classes
 *     .build();
 *
 * // Custom delegation logic
 * JClassLoader loader = JClassLoader.builder()
 *     .customDelegation(className ->
 *         className.startsWith("com.example.core"))
 *     .build();
 * }</pre>
 *
 * @since 1.0
 */
package org.flossware.jclassloader.delegation;
