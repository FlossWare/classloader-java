package org.flossware.jclassloader.delegation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CustomDelegationTest {

    @Test
    void testCustomPredicate() throws Exception {
        // Parent-first for classes starting with "java." or "com.platform."
        CustomDelegation delegation = new CustomDelegation(
            name -> name.startsWith("java.") || name.startsWith("com.platform.")
        );

        ClassLoader parent = ClassLoader.getSystemClassLoader();

        // java.lang.String should use parent-first
        Class<?> result = delegation.loadClass("java.lang.String", parent,
            name -> { throw new ClassNotFoundException("Should not be called"); });
        assertEquals(String.class, result);
    }

    @Test
    void testCustomPredicateParentLast() throws Exception {
        CustomDelegation delegation = new CustomDelegation(
            name -> name.startsWith("java.")
        );

        ClassLoader parent = ClassLoader.getSystemClassLoader();
        boolean[] sourcesCalled = {false};

        // com.myapp.MyClass should use parent-last
        try {
            delegation.loadClass("com.myapp.MyClass", parent, name -> {
                sourcesCalled[0] = true;
                throw new ClassNotFoundException(name);
            });
            fail("Should have thrown ClassNotFoundException");
        } catch (ClassNotFoundException e) {
            assertTrue(sourcesCalled[0], "Sources should be checked first");
        }
    }

    @Test
    void testToString() {
        CustomDelegation delegation = new CustomDelegation(name -> true);
        String str = delegation.toString();
        assertTrue(str.contains("CustomDelegation"));
    }
}
