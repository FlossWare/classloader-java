package org.flossware.classloader.util;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.junit.jupiter.api.Assertions.*;

class ClassLoaderConstantsTest {

    @Test
    void testConstructorThrowsException() throws NoSuchMethodException {
        Constructor<ClassLoaderConstants> constructor = ClassLoaderConstants.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        InvocationTargetException thrown = assertThrows(InvocationTargetException.class, constructor::newInstance);
        assertTrue(thrown.getCause() instanceof AssertionError);
        assertEquals("Utility class - do not instantiate", thrown.getCause().getMessage());
    }

    @Test
    void testDefaultBufferSizeValue() {
        assertEquals(8192, ClassLoaderConstants.DEFAULT_BUFFER_SIZE);
    }

    @Test
    void testDefaultBufferSizeIsPositive() {
        assertTrue(ClassLoaderConstants.DEFAULT_BUFFER_SIZE > 0);
    }

    @Test
    void testDefaultBufferSizeIsPowerOfTwo() {
        // 8192 = 2^13
        int value = ClassLoaderConstants.DEFAULT_BUFFER_SIZE;
        assertTrue((value & (value - 1)) == 0, "Buffer size should be a power of 2");
    }
}
