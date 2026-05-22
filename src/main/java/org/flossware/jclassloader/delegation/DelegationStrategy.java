package org.flossware.jclassloader.delegation;

/**
 * Strategy for class loading delegation between parent ClassLoader and configured sources.
 * Allows customizing when to delegate to parent vs. load from sources.
 */
public interface DelegationStrategy {

    /**
     * Load a class using this delegation strategy.
     *
     * @param name The class name to load
     * @param parent The parent ClassLoader
     * @param findInSources Function to find class in configured sources
     * @return The loaded class
     * @throws ClassNotFoundException if class cannot be found
     */
    Class<?> loadClass(String name, ClassLoader parent, ClassFinder findInSources)
            throws ClassNotFoundException;

    /**
     * Functional interface for finding a class in configured sources.
     */
    @FunctionalInterface
    interface ClassFinder {
        Class<?> findClass(String name) throws ClassNotFoundException;
    }
}
