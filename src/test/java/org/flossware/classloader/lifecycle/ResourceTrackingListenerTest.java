package org.flossware.classloader.lifecycle;

import org.flossware.classloader.ClassSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


import java.io.Closeable;
import java.util.Set;

class ResourceTrackingListenerTest {

    private ResourceTrackingListener listener;

    @BeforeEach
    void setUp() {
        listener = new ResourceTrackingListener();
    }

    @Test
    void testTrackClassLoaded() {
        ClassSource mockSource = new ClassSource() {
            @Override
            public byte[] loadClassData(String className) { return null; }
            @Override
            public boolean canLoad(String className) { return false; }
            @Override
            public String getDescription() { return "mock"; }
        };

        ClassLoadEvent event = new ClassLoadEvent("com.example.MyClass", mockSource, 1000000, 1024);
        listener.onClassLoaded(event);

        assertEquals(1, listener.getTotalClassesLoaded());
        assertEquals(1024, listener.getTotalBytesLoaded());
        assertTrue(listener.getLoadedClasses().contains("com.example.MyClass"));
    }

    @Test
    void testTrackMultipleClasses() {
        ClassSource mockSource = new ClassSource() {
            @Override
            public byte[] loadClassData(String className) { return null; }
            @Override
            public boolean canLoad(String className) { return false; }
            @Override
            public String getDescription() { return "mock"; }
        };

        listener.onClassLoaded(new ClassLoadEvent("com.example.Class1", mockSource, 1000, 100));
        listener.onClassLoaded(new ClassLoadEvent("com.example.Class2", mockSource, 2000, 200));
        listener.onClassLoaded(new ClassLoadEvent("com.example.Class3", mockSource, 3000, 300));

        assertEquals(3, listener.getTotalClassesLoaded());
        assertEquals(600, listener.getTotalBytesLoaded());

        Set<String> loadedClasses = listener.getLoadedClasses();
        assertEquals(3, loadedClasses.size());
        assertTrue(loadedClasses.contains("com.example.Class1"));
        assertTrue(loadedClasses.contains("com.example.Class2"));
        assertTrue(loadedClasses.contains("com.example.Class3"));
    }

    @Test
    void testTrackCacheHits() {
        listener.onClassCacheHit("com.example.Cached");
        listener.onClassCacheHit("com.example.Cached");

        assertEquals(2, listener.getCacheHits());
    }

    @Test
    void testTrackResources() {
        Closeable resource1 = () -> {};
        Closeable resource2 = () -> {};

        listener.onResourceOpened("resource1", resource1);
        listener.onResourceOpened("resource2", resource2);

        // Resources are tracked internally
        listener.closeAllResources();

        // After close, should be cleared
        assertEquals(0, listener.getLoadedClasses().size());
    }

    @Test
    void testReset() {
        ClassSource mockSource = new ClassSource() {
            @Override
            public byte[] loadClassData(String className) { return null; }
            @Override
            public boolean canLoad(String className) { return false; }
            @Override
            public String getDescription() { return "mock"; }
        };

        listener.onClassLoaded(new ClassLoadEvent("com.example.Test", mockSource, 1000, 100));
        listener.onClassCacheHit("test");

        listener.reset();

        assertEquals(0, listener.getTotalClassesLoaded());
        assertEquals(0, listener.getTotalBytesLoaded());
        assertEquals(0, listener.getCacheHits());
        assertEquals(0, listener.getLoadedClasses().size());
    }

    @Test
    void testToString() {
        String str = listener.toString();
        assertTrue(str.contains("ResourceTracker"));
        assertTrue(str.contains("classes="));
        assertTrue(str.contains("bytes="));
    }
}
