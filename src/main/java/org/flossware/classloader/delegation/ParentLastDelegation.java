package org.flossware.classloader.delegation;

import java.util.Arrays;
import java.util.Objects;

/**
 * Parent-last delegation for application isolation.
 * Checks configured sources first, then falls back to parent.
 * Certain packages (java.*, javax.*, etc.) always delegate to parent first.
 */
public class ParentLastDelegation implements DelegationStrategy {

    private final String[] alwaysParentPrefixes;

    /**
     * Creates a parent-last delegation strategy with custom parent-first prefixes.
     *
     * @param alwaysParentPrefixes Class name prefixes that should always be loaded from parent
     * @throws NullPointerException if alwaysParentPrefixes is null
     */
    public ParentLastDelegation(String... alwaysParentPrefixes) {
        Objects.requireNonNull(alwaysParentPrefixes, "alwaysParentPrefixes cannot be null");
        this.alwaysParentPrefixes = alwaysParentPrefixes.clone();
    }

    /**
     * Creates a parent-last delegation strategy with default parent-first prefixes.
     * Default prefixes include all JDK system packages to prevent ClassCastException.
     */
    public static ParentLastDelegation withDefaults() {
        return new ParentLastDelegation(
            "java.",      // Core Java classes
            "javax.",     // Java extension classes
            "sun.",       // Sun internal classes
            "jdk.",       // JDK internal classes
            "com.sun.",   // Sun implementation classes
            "org.xml.",   // XML APIs (SAX, DOM)
            "org.w3c.",   // W3C APIs (DOM)
            "org.ietf.",  // IETF APIs (GSS)
            "org.omg."    // CORBA (legacy but still in some JDKs)
        );
    }

    @Override
    public Class<?> loadClass(String name, ClassLoader parent, ClassFinder findInSources)
            throws ClassNotFoundException {
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(parent, "parent cannot be null");
        Objects.requireNonNull(findInSources, "findInSources cannot be null");

        // System classes and specified prefixes always from parent
        // Use simple for-each loop instead of Stream.anyMatch() for better performance on hot path
        for (String prefix : alwaysParentPrefixes) {
            if (name.startsWith(prefix)) {
                return parent.loadClass(name);
            }
        }

        // Try sources first (parent-last)
        try {
            return findInSources.findClass(name);
        } catch (ClassNotFoundException e) {
            // Fall back to parent
            return parent.loadClass(name);
        }
    }

    public String[] getAlwaysParentPrefixes() {
        return alwaysParentPrefixes.clone();
    }

    @Override
    public String toString() {
        return "ParentLastDelegation{alwaysParent=" + Arrays.toString(alwaysParentPrefixes) + "}";
    }
}
