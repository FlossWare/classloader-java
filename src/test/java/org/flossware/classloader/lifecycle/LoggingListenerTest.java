package org.flossware.classloader.lifecycle;

import org.flossware.classloader.ClassSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

class LoggingListenerTest {

    @Test
    void testLoggingOutput() {
        // Capture System.out
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(outContent));

        try {
            LoggingListener listener = new LoggingListener();

            ClassSource mockSource = new ClassSource() {
                public byte[] loadClassData(String className) { return null; }
                public boolean canLoad(String className) { return false; }
                public String getDescription() { return "test-source"; }
            };

            ClassLoadEvent event = new ClassLoadEvent("com.example.Test", mockSource, 1000000, 1024);
            listener.onClassLoaded(event);

            String output = outContent.toString();
            assertTrue(output.contains("[JClassLoader]"));
            assertTrue(output.contains("com.example.Test"));
            assertTrue(output.contains("test-source"));

        } finally {
            System.setOut(originalOut);
        }
    }

    @Test
    void testVerboseLogging() {
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(outContent));

        try {
            LoggingListener listener = new LoggingListener(true); // verbose

            listener.onClassCacheHit("com.example.Cached");

            String output = outContent.toString();
            assertTrue(output.contains("Cache hit"));
            assertTrue(output.contains("com.example.Cached"));

        } finally {
            System.setOut(originalOut);
        }
    }

    @Test
    void testNonVerboseSkipsCacheHits() {
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(outContent));

        try {
            LoggingListener listener = new LoggingListener(false); // non-verbose

            listener.onClassCacheHit("com.example.Cached");

            String output = outContent.toString();
            assertEquals("", output); // Should not log cache hits in non-verbose mode

        } finally {
            System.setOut(originalOut);
        }
    }
}
