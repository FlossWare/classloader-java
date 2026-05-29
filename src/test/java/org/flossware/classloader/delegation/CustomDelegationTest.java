package org.flossware.classloader.delegation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;


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

    @Test
    void testNullPredicateThrowsException() {
        assertThrows(NullPointerException.class, () -> new CustomDelegation(null));
    }

    @Test
    void testLoadClassNullNameThrowsException() {
        CustomDelegation delegation = new CustomDelegation(name -> true);
        ClassLoader parent = ClassLoader.getSystemClassLoader();
        assertThrows(NullPointerException.class, () ->
            delegation.loadClass(null, parent, name -> { throw new ClassNotFoundException(); }));
    }

    @Test
    void testLoadClassNullParentThrowsException() {
        CustomDelegation delegation = new CustomDelegation(name -> true);
        assertThrows(NullPointerException.class, () ->
            delegation.loadClass("test.Class", null, name -> { throw new ClassNotFoundException(); }));
    }

    @Test
    void testLoadClassNullFinderThrowsException() {
        CustomDelegation delegation = new CustomDelegation(name -> true);
        ClassLoader parent = ClassLoader.getSystemClassLoader();
        assertThrows(NullPointerException.class, () ->
            delegation.loadClass("test.Class", parent, null));
    }
}
