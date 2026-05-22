package org.flossware.jclassloader.delegation;

import java.util.function.Predicate;

/**
 * Custom delegation allowing fine-grained control over parent-first vs parent-last.
 * Uses a predicate to determine whether each class should use parent-first delegation.
 */
public class CustomDelegation implements DelegationStrategy {

    private final Predicate<String> parentFirstPredicate;

    /**
     * Creates a custom delegation strategy.
     *
     * @param parentFirstPredicate Predicate that returns true if a class should use parent-first,
     *                            false for parent-last (sources first)
     */
    public CustomDelegation(Predicate<String> parentFirstPredicate) {
        this.parentFirstPredicate = parentFirstPredicate;
    }

    @Override
    public Class<?> loadClass(String name, ClassLoader parent, ClassFinder findInSources)
            throws ClassNotFoundException {
        if (parentFirstPredicate.test(name)) {
            // Parent-first for this class
            try {
                return parent.loadClass(name);
            } catch (ClassNotFoundException e) {
                return findInSources.findClass(name);
            }
        } else {
            // Parent-last for this class
            try {
                return findInSources.findClass(name);
            } catch (ClassNotFoundException e) {
                return parent.loadClass(name);
            }
        }
    }

    @Override
    public String toString() {
        return "CustomDelegation{predicate=" + parentFirstPredicate + "}";
    }
}
