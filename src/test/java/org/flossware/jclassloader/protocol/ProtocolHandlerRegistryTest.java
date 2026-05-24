package org.flossware.jclassloader.protocol;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Tests for ProtocolHandlerRegistry.
 */
class ProtocolHandlerRegistryTest {

    static class TestHandler implements ProtocolHandler {
        @Override
        public byte[] fetchClass(String className) throws IOException {
            return new byte[0];
        }

        @Override
        public boolean canHandle(String className) {
            return true;
        }

        @Override
        public String getProtocolName() {
            return "test";
        }

        @Override
        public void close() throws IOException {
        }
    }

    static class AnotherHandler implements ProtocolHandler {
        @Override
        public byte[] fetchClass(String className) throws IOException {
            return new byte[0];
        }

        @Override
        public boolean canHandle(String className) {
            return false;
        }

        @Override
        public String getProtocolName() {
            return "another";
        }

        @Override
        public void close() throws IOException {
        }
    }

    @AfterEach
    void cleanup() {
        ProtocolHandlerRegistry registry = ProtocolHandlerRegistry.getInstance();
        registry.unregister("test");
        registry.unregister("another");
        registry.unregister("custom");
    }

    @Test
    void testGetInstance() {
        ProtocolHandlerRegistry registry1 = ProtocolHandlerRegistry.getInstance();
        ProtocolHandlerRegistry registry2 = ProtocolHandlerRegistry.getInstance();

        assertSame(registry1, registry2);
    }

    @Test
    void testRegister() {
        ProtocolHandlerRegistry registry = ProtocolHandlerRegistry.getInstance();

        registry.register("test", TestHandler.class);

        assertTrue(registry.isRegistered("test"));
        assertEquals(TestHandler.class, registry.getHandler("test"));
    }

    @Test
    void testRegisterCaseInsensitive() {
        ProtocolHandlerRegistry registry = ProtocolHandlerRegistry.getInstance();

        registry.register("TeSt", TestHandler.class);

        assertTrue(registry.isRegistered("test"));
        assertTrue(registry.isRegistered("TEST"));
        assertTrue(registry.isRegistered("TeSt"));
        assertEquals(TestHandler.class, registry.getHandler("test"));
        assertEquals(TestHandler.class, registry.getHandler("TEST"));
    }

    @Test
    void testRegisterNullProtocolThrowsException() {
        ProtocolHandlerRegistry registry = ProtocolHandlerRegistry.getInstance();

        assertThrows(NullPointerException.class, () -> {
            registry.register(null, TestHandler.class);
        });
    }

    @Test
    void testRegisterNullHandlerClassThrowsException() {
        ProtocolHandlerRegistry registry = ProtocolHandlerRegistry.getInstance();

        assertThrows(NullPointerException.class, () -> {
            registry.register("test", null);
        });
    }

    @Test
    void testUnregister() {
        ProtocolHandlerRegistry registry = ProtocolHandlerRegistry.getInstance();

        registry.register("test", TestHandler.class);
        assertTrue(registry.isRegistered("test"));

        registry.unregister("test");
        assertFalse(registry.isRegistered("test"));
    }

    @Test
    void testUnregisterNonExistent() {
        ProtocolHandlerRegistry registry = ProtocolHandlerRegistry.getInstance();

        assertDoesNotThrow(() -> {
            registry.unregister("nonexistent");
        });
    }

    @Test
    void testGetHandlerNotRegistered() {
        ProtocolHandlerRegistry registry = ProtocolHandlerRegistry.getInstance();

        assertNull(registry.getHandler("nonexistent"));
    }

    @Test
    void testIsRegisteredFalse() {
        ProtocolHandlerRegistry registry = ProtocolHandlerRegistry.getInstance();

        assertFalse(registry.isRegistered("nonexistent"));
    }

    @Test
    void testGetAllHandlers() {
        ProtocolHandlerRegistry registry = ProtocolHandlerRegistry.getInstance();

        registry.register("test", TestHandler.class);
        registry.register("another", AnotherHandler.class);

        Map<String, Class<? extends ProtocolHandler>> handlers = registry.getAllHandlers();

        assertEquals(2, handlers.size());
        assertTrue(handlers.containsKey("test"));
        assertTrue(handlers.containsKey("another"));
        assertEquals(TestHandler.class, handlers.get("test"));
        assertEquals(AnotherHandler.class, handlers.get("another"));
    }

    @Test
    void testGetAllHandlersReturnsNewMap() {
        ProtocolHandlerRegistry registry = ProtocolHandlerRegistry.getInstance();

        registry.register("test", TestHandler.class);

        Map<String, Class<? extends ProtocolHandler>> handlers1 = registry.getAllHandlers();
        Map<String, Class<? extends ProtocolHandler>> handlers2 = registry.getAllHandlers();

        assertNotSame(handlers1, handlers2);
        assertEquals(handlers1, handlers2);
    }

    @Test
    void testRegisterOverwrite() {
        ProtocolHandlerRegistry registry = ProtocolHandlerRegistry.getInstance();

        registry.register("test", TestHandler.class);
        assertEquals(TestHandler.class, registry.getHandler("test"));

        registry.register("test", AnotherHandler.class);
        assertEquals(AnotherHandler.class, registry.getHandler("test"));
    }

    @Test
    void testMultipleProtocols() {
        ProtocolHandlerRegistry registry = ProtocolHandlerRegistry.getInstance();

        registry.register("proto1", TestHandler.class);
        registry.register("proto2", AnotherHandler.class);
        registry.register("proto3", TestHandler.class);

        assertTrue(registry.isRegistered("proto1"));
        assertTrue(registry.isRegistered("proto2"));
        assertTrue(registry.isRegistered("proto3"));

        Map<String, Class<? extends ProtocolHandler>> all = registry.getAllHandlers();
        assertEquals(3, all.size());
    }
}
