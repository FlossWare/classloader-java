package org.flossware.classloader.delegation;

import java.util.Objects;
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
     * @throws NullPointerException if parentFirstPredicate is null
     */
    public CustomDelegation(Predicate<String> parentFirstPredicate) {
        this.parentFirstPredicate = Objects.requireNonNull(parentFirstPredicate,
            "parentFirstPredicate cannot be null");
    }

    @Override
    public Class<?> loadClass(String name, ClassLoader parent, ClassFinder findInSources)
            throws ClassNotFoundException {
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(parent, "parent cannot be null");
        Objects.requireNonNull(findInSources, "findInSources cannot be null");

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
