package org.flossware.classloader.delegation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


class ParentFirstDelegationTest {

    @Test
    void testParentFirstBehavior() throws Exception {
        ParentFirstDelegation delegation = new ParentFirstDelegation();
        ClassLoader parent = ClassLoader.getSystemClassLoader();

        // Should load from parent first
        Class<?> result = delegation.loadClass("java.lang.String", parent,
            name -> { throw new ClassNotFoundException("Should not be called"); });

        assertEquals(String.class, result);
    }

    @Test
    void testFallbackToSources() throws Exception {
        ParentFirstDelegation delegation = new ParentFirstDelegation();
        ClassLoader parent = ClassLoader.getSystemClassLoader();

        boolean[] sourcesCalled = {false};
        Class<?> mockClass = String.class; // Just for testing

        // Class not in parent should fall back to sources
        Class<?> result = delegation.loadClass("com.example.NotInParent", parent, name -> {
            sourcesCalled[0] = true;
            return mockClass;
        });

        assertTrue(sourcesCalled[0], "Should fall back to sources");
        assertEquals(mockClass, result);
    }

    @Test
    void testToString() {
        ParentFirstDelegation delegation = new ParentFirstDelegation();
        assertEquals("ParentFirstDelegation", delegation.toString());
    }
}
