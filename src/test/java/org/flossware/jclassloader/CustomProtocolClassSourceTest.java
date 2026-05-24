package org.flossware.jclassloader;

import org.flossware.jclassloader.protocol.ProtocolHandler;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Tests for CustomProtocolClassSource.
 */
class CustomProtocolClassSourceTest {

    static class TestProtocolHandler implements ProtocolHandler {
        private boolean closed = false;
        private final String protocolName;

        TestProtocolHandler(String protocolName) {
            this.protocolName = protocolName;
        }

        @Override
        public byte[] fetchClass(String className) throws IOException {
            if ("error.Class".equals(className)) {
                throw new IOException("Test error");
            }
            return ("class:" + className).getBytes();
        }

        @Override
        public boolean canHandle(String className) {
            return className.startsWith("com.example");
        }

        @Override
        public String getProtocolName() {
            return protocolName;
        }

        @Override
        public void close() throws IOException {
            closed = true;
        }

        public boolean isClosed() {
            return closed;
        }
    }

    @Test
    void testConstructor() {
        TestProtocolHandler handler = new TestProtocolHandler("test");
        CustomProtocolClassSource source = new CustomProtocolClassSource(handler);

        assertNotNull(source);
        assertSame(handler, source.getHandler());
    }

    @Test
    void testConstructorNullHandlerThrowsException() {
        assertThrows(NullPointerException.class, () -> {
            new CustomProtocolClassSource(null);
        });
    }

    @Test
    void testLoadClassData() throws IOException {
        TestProtocolHandler handler = new TestProtocolHandler("custom");
        CustomProtocolClassSource source = new CustomProtocolClassSource(handler);

        byte[] data = source.loadClassData("com.example.TestClass");

        assertNotNull(data);
        assertArrayEquals("class:com.example.TestClass".getBytes(), data);
    }

    @Test
    void testLoadClassDataThrowsException() {
        TestProtocolHandler handler = new TestProtocolHandler("custom");
        CustomProtocolClassSource source = new CustomProtocolClassSource(handler);

        assertThrows(IOException.class, () -> {
            source.loadClassData("error.Class");
        });
    }

    @Test
    void testCanLoad() {
        TestProtocolHandler handler = new TestProtocolHandler("custom");
        CustomProtocolClassSource source = new CustomProtocolClassSource(handler);

        assertTrue(source.canLoad("com.example.TestClass"));
        assertFalse(source.canLoad("other.package.Class"));
    }

    @Test
    void testGetDescription() {
        TestProtocolHandler handler = new TestProtocolHandler("ipfs");
        CustomProtocolClassSource source = new CustomProtocolClassSource(handler);

        String description = source.getDescription();

        assertTrue(description.contains("CustomProtocolClassSource"));
        assertTrue(description.contains("protocol=ipfs"));
    }

    @Test
    void testGetHandler() {
        TestProtocolHandler handler = new TestProtocolHandler("test");
        CustomProtocolClassSource source = new CustomProtocolClassSource(handler);

        assertSame(handler, source.getHandler());
    }

    @Test
    void testClose() throws IOException {
        TestProtocolHandler handler = new TestProtocolHandler("test");
        CustomProtocolClassSource source = new CustomProtocolClassSource(handler);

        assertFalse(handler.isClosed());

        source.close();

        assertTrue(handler.isClosed());
    }

    @Test
    void testMultipleOperations() throws IOException {
        TestProtocolHandler handler = new TestProtocolHandler("custom");
        CustomProtocolClassSource source = new CustomProtocolClassSource(handler);

        assertTrue(source.canLoad("com.example.Class1"));
        byte[] data1 = source.loadClassData("com.example.Class1");
        assertNotNull(data1);

        assertTrue(source.canLoad("com.example.Class2"));
        byte[] data2 = source.loadClassData("com.example.Class2");
        assertNotNull(data2);

        assertFalse(source.canLoad("other.Class"));
    }

    @Test
    void testDifferentProtocolNames() {
        TestProtocolHandler handler1 = new TestProtocolHandler("proto1");
        TestProtocolHandler handler2 = new TestProtocolHandler("proto2");

        CustomProtocolClassSource source1 = new CustomProtocolClassSource(handler1);
        CustomProtocolClassSource source2 = new CustomProtocolClassSource(handler2);

        assertTrue(source1.getDescription().contains("proto1"));
        assertTrue(source2.getDescription().contains("proto2"));
    }
}
