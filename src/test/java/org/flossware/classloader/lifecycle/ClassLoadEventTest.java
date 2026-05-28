package org.flossware.classloader.lifecycle;

import org.flossware.classloader.ClassSource;
import org.flossware.classloader.LocalClassSource;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Tests for ClassLoadEvent.
 */
class ClassLoadEventTest {

    @Test
    void testConstructorWithAllParameters() {
        ClassSource source = new LocalClassSource(Paths.get("/tmp"));
        ClassLoadEvent event = new ClassLoadEvent("com.example.TestClass",
                source, 1000000L, 1024);

        assertEquals("com.example.TestClass", event.getClassName());
        assertSame(source, event.getSource());
        assertEquals(1000000L, event.getLoadTimeNanos());
        assertEquals(1024, event.getClassSizeBytes());
        assertTrue(event.getTimestamp() > 0);
    }

    @Test
    void testGetClassName() {
        ClassSource source = new LocalClassSource(Paths.get("/tmp"));
        ClassLoadEvent event = new ClassLoadEvent("org.example.MyClass",
                source, 500000L, 512);

        assertEquals("org.example.MyClass", event.getClassName());
    }

    @Test
    void testGetSource() {
        ClassSource source = new LocalClassSource(Paths.get("/tmp"));
        ClassLoadEvent event = new ClassLoadEvent("TestClass", source, 100000L, 256);

        assertSame(source, event.getSource());
    }

    @Test
    void testGetLoadTimeNanos() {
        ClassSource source = new LocalClassSource(Paths.get("/tmp"));
        ClassLoadEvent event = new ClassLoadEvent("TestClass", source, 2000000L, 1024);

        assertEquals(2000000L, event.getLoadTimeNanos());
    }

    @Test
    void testGetLoadTimeMillis() {
        ClassSource source = new LocalClassSource(Paths.get("/tmp"));
        ClassLoadEvent event = new ClassLoadEvent("TestClass", source, 5000000L, 1024);

        assertEquals(5L, event.getLoadTimeMillis());
    }

    @Test
    void testGetClassSizeBytes() {
        ClassSource source = new LocalClassSource(Paths.get("/tmp"));
        ClassLoadEvent event = new ClassLoadEvent("TestClass", source, 1000000L, 2048);

        assertEquals(2048, event.getClassSizeBytes());
    }

    @Test
    void testGetTimestamp() {
        long before = System.currentTimeMillis();
        ClassSource source = new LocalClassSource(Paths.get("/tmp"));
        ClassLoadEvent event = new ClassLoadEvent("TestClass", source, 1000000L, 1024);
        long after = System.currentTimeMillis();

        assertTrue(event.getTimestamp() >= before);
        assertTrue(event.getTimestamp() <= after);
    }

    @Test
    void testToString() {
        ClassSource source = new LocalClassSource(Paths.get("/tmp"));
        ClassLoadEvent event = new ClassLoadEvent("com.example.TestClass",
                source, 3000000L, 1536);

        String str = event.toString();
        assertTrue(str.contains("ClassLoadEvent"));
        assertTrue(str.contains("com.example.TestClass"));
        assertTrue(str.contains("3ms"));
        assertTrue(str.contains("1536B"));
    }

    @Test
    void testNanosToMillisConversion() {
        ClassSource source = new LocalClassSource(Paths.get("/tmp"));
        ClassLoadEvent event = new ClassLoadEvent("TestClass", source, 15000000L, 1024);

        assertEquals(15L, event.getLoadTimeMillis());
    }

    @Test
    void testZeroLoadTime() {
        ClassSource source = new LocalClassSource(Paths.get("/tmp"));
        ClassLoadEvent event = new ClassLoadEvent("FastClass", source, 0L, 100);

        assertEquals(0L, event.getLoadTimeNanos());
        assertEquals(0L, event.getLoadTimeMillis());
    }

    @Test
    void testLargeClassSize() {
        ClassSource source = new LocalClassSource(Paths.get("/tmp"));
        ClassLoadEvent event = new ClassLoadEvent("LargeClass", source, 1000000L, 1048576);

        assertEquals(1048576, event.getClassSizeBytes());
    }

    @Test
    void testMultipleEvents() {
        ClassSource source = new LocalClassSource(Paths.get("/tmp"));
        ClassLoadEvent event1 = new ClassLoadEvent("Class1", source, 1000000L, 512);
        ClassLoadEvent event2 = new ClassLoadEvent("Class2", source, 2000000L, 1024);

        assertNotEquals(event1.getClassName(), event2.getClassName());
        assertNotEquals(event1.getLoadTimeNanos(), event2.getLoadTimeNanos());
    }

    @Test
    void testDifferentSources() {
        ClassSource source1 = new LocalClassSource(Paths.get("/tmp1"));
        ClassSource source2 = new LocalClassSource(Paths.get("/tmp2"));

        ClassLoadEvent event1 = new ClassLoadEvent("Class1", source1, 1000000L, 512);
        ClassLoadEvent event2 = new ClassLoadEvent("Class2", source2, 1000000L, 512);

        assertNotSame(event1.getSource(), event2.getSource());
    }

    @Test
    void testVeryLongLoadTime() {
        ClassSource source = new LocalClassSource(Paths.get("/tmp"));
        ClassLoadEvent event = new ClassLoadEvent("SlowClass", source, 1000000000L, 1024);

        assertEquals(1000L, event.getLoadTimeMillis());
    }
}

