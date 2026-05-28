package org.flossware.classloader.util;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.junit.jupiter.api.Assertions.*;

class ClassNameUtilTest {

    @Test
    void testConstructorThrowsException() throws NoSuchMethodException {
        Constructor<ClassNameUtil> constructor = ClassNameUtil.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        InvocationTargetException thrown = assertThrows(InvocationTargetException.class, constructor::newInstance);
        assertTrue(thrown.getCause() instanceof UnsupportedOperationException);
        assertEquals("Utility class cannot be instantiated", thrown.getCause().getMessage());
    }

    @Test
    void testToClassFilePathNullThrowsException() {
        NullPointerException thrown = assertThrows(NullPointerException.class, () -> {
            ClassNameUtil.toClassFilePath(null);
        });
        assertEquals("className cannot be null", thrown.getMessage());
    }

    @Test
    void testToClassFilePathSimpleClass() {
        assertEquals("MyClass.class", ClassNameUtil.toClassFilePath("MyClass"));
    }

    @Test
    void testToClassFilePathPackagedClass() {
        assertEquals("com/example/MyClass.class", ClassNameUtil.toClassFilePath("com.example.MyClass"));
    }

    @Test
    void testToClassFilePathDeeplyNestedPackage() {
        assertEquals("com/example/deep/nested/pkg/MyClass.class",
                    ClassNameUtil.toClassFilePath("com.example.deep.nested.pkg.MyClass"));
    }

    @Test
    void testToClassFilePathJavaLangString() {
        assertEquals("java/lang/String.class", ClassNameUtil.toClassFilePath("java.lang.String"));
    }

    @Test
    void testToClassFilePathInnerClass() {
        assertEquals("com/example/Outer$Inner.class",
                    ClassNameUtil.toClassFilePath("com.example.Outer$Inner"));
    }

    @Test
    void testToClassFilePathAnonymousInnerClass() {
        assertEquals("com/example/MyClass$1.class",
                    ClassNameUtil.toClassFilePath("com.example.MyClass$1"));
    }

    @Test
    void testToClassFilePathEmptyString() {
        assertEquals(".class", ClassNameUtil.toClassFilePath(""));
    }

    @Test
    void testToClassFilePathSingleCharacter() {
        assertEquals("A.class", ClassNameUtil.toClassFilePath("A"));
    }

    @Test
    void testToClassFilePathMultipleDollarSigns() {
        assertEquals("com/example/Outer$Inner$Deep.class",
                    ClassNameUtil.toClassFilePath("com.example.Outer$Inner$Deep"));
    }
}
