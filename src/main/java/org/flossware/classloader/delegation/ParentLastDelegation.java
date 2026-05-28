package org.flossware.classloader.delegation;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Parent-last delegation for application isolation.
 * Checks configured sources first, then falls back to parent.
 * Certain packages (java.*, javax.*, etc.) always delegate to parent first.
 */
public class ParentLastDelegation implements DelegationStrategy {

    private final Set<String> alwaysParentPrefixes;

    /**
     * Creates a parent-last delegation strategy with custom parent-first prefixes.
     *
     * @param alwaysParentPrefixes Class name prefixes that should always be loaded from parent
     */
    public ParentLastDelegation(String... alwaysParentPrefixes) {
        Set<String> prefixes = new HashSet<>();
        Collections.addAll(prefixes, alwaysParentPrefixes);
        this.alwaysParentPrefixes = Collections.unmodifiableSet(prefixes);
    }

    /**
     * Creates a parent-last delegation strategy with default parent-first prefixes.
     * Default prefixes: java., javax., sun., jdk.
     */
    public static ParentLastDelegation withDefaults() {
        return new ParentLastDelegation("java.", "javax.", "sun.", "jdk.");
    }

    @Override
    public Class<?> loadClass(String name, ClassLoader parent, ClassFinder findInSources)
            throws ClassNotFoundException {
        // System classes and specified prefixes always from parent
        if (alwaysParentPrefixes.stream().anyMatch(name::startsWith)) {
            return parent.loadClass(name);
        }

        // Try sources first (parent-last)
        try {
            return findInSources.findClass(name);
        } catch (ClassNotFoundException e) {
            // Fall back to parent
            return parent.loadClass(name);
        }
    }

    public Set<String> getAlwaysParentPrefixes() {
        return alwaysParentPrefixes;
    }

    @Override
    public String toString() {
        return "ParentLastDelegation{alwaysParent=" + alwaysParentPrefixes + "}";
    }
}
