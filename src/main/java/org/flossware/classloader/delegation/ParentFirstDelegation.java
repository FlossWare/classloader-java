package org.flossware.classloader.delegation;

import java.util.Objects;

/**
 * Standard parent-first delegation (default Java ClassLoader behavior).
 * Delegates to parent ClassLoader first, then checks configured sources.
 */
public class ParentFirstDelegation implements DelegationStrategy {

    @Override
    public Class<?> loadClass(String name, ClassLoader parent, ClassFinder findInSources)
            throws ClassNotFoundException {
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(parent, "parent cannot be null");
        Objects.requireNonNull(findInSources, "findInSources cannot be null");

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
