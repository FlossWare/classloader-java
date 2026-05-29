package org.flossware.classloader.delegation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Arrays;
import java.util.List;

class ParentLastDelegationTest {

    @Test
    void testDefaultPrefixes() {
        ParentLastDelegation delegation = ParentLastDelegation.withDefaults();
        List<String> prefixes = Arrays.asList(delegation.getAlwaysParentPrefixes());

        assertTrue(prefixes.contains("java."));
        assertTrue(prefixes.contains("javax."));
        assertTrue(prefixes.contains("sun."));
        assertTrue(prefixes.contains("jdk."));
        assertTrue(prefixes.contains("com.sun."));
        assertTrue(prefixes.contains("org.xml."));
        assertTrue(prefixes.contains("org.w3c."));
        assertTrue(prefixes.contains("org.ietf."));
        assertTrue(prefixes.contains("org.omg."));
    }

    @Test
    void testCustomPrefixes() {
        ParentLastDelegation delegation = new ParentLastDelegation("com.example.api.", "org.platform.");
        String[] prefixes = delegation.getAlwaysParentPrefixes();

        assertEquals(2, prefixes.length);
        List<String> prefixList = Arrays.asList(prefixes);
        assertTrue(prefixList.contains("com.example.api."));
        assertTrue(prefixList.contains("org.platform."));
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
