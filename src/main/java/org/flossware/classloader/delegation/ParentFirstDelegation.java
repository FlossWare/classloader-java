package org.flossware.classloader.delegation;

/**
 * Standard parent-first delegation (default Java ClassLoader behavior).
 * Delegates to parent ClassLoader first, then checks configured sources.
 */
public class ParentFirstDelegation implements DelegationStrategy {

    @Override
    public Class<?> loadClass(String name, ClassLoader parent, ClassFinder findInSources)
            throws ClassNotFoundException {
        try {
            return parent.loadClass(name);
        } catch (ClassNotFoundException e) {
            return findInSources.findClass(name);
        }
    }

    @Override
    public String toString() {
        return "ParentFirstDelegation";
    }
}
