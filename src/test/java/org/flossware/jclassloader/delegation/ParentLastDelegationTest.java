package org.flossware.jclassloader.delegation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;


import java.util.Set;

class ParentLastDelegationTest {

    @Test
    void testDefaultPrefixes() {
        ParentLastDelegation delegation = ParentLastDelegation.withDefaults();
        Set<String> prefixes = delegation.getAlwaysParentPrefixes();

        assertTrue(prefixes.contains("java."));
        assertTrue(prefixes.contains("javax."));
        assertTrue(prefixes.contains("sun."));
        assertTrue(prefixes.contains("jdk."));
    }

    @Test
    void testCustomPrefixes() {
        ParentLastDelegation delegation = new ParentLastDelegation("com.example.api.", "org.platform.");
        Set<String> prefixes = delegation.getAlwaysParentPrefixes();

        assertEquals(2, prefixes.size());
        assertTrue(prefixes.contains("com.example.api."));
        assertTrue(prefixes.contains("org.platform."));
    }

    @Test
    void testParentFirstForSystemClasses() throws Exception {
        ParentLastDelegation delegation = ParentLastDelegation.withDefaults();
        ClassLoader parent = ClassLoader.getSystemClassLoader();

        // java.lang.String should always come from parent
        Class<?> result = delegation.loadClass("java.lang.String", parent,
            name -> { throw new ClassNotFoundException("Should not be called"); });

        assertEquals(String.class, result);
    }

    @Test
    void testParentLastForApplicationClasses() throws Exception {
        ParentLastDelegation delegation = ParentLastDelegation.withDefaults();
        ClassLoader parent = ClassLoader.getSystemClassLoader();

        boolean[] sourcesCalled = {false};

        try {
            delegation.loadClass("com.myapp.MyClass", parent, name -> {
                sourcesCalled[0] = true;
                throw new ClassNotFoundException(name);
            });
            fail("Should have thrown ClassNotFoundException");
        } catch (ClassNotFoundException e) {
            assertTrue(sourcesCalled[0], "Sources should be checked first (parent-last)");
        }
    }

    @Test
    void testToString() {
        ParentLastDelegation delegation = new ParentLastDelegation("com.example.");
        String str = delegation.toString();
        assertTrue(str.contains("ParentLastDelegation"));
        assertTrue(str.contains("com.example."));
    }
}
