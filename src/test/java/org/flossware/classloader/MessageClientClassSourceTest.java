package org.flossware.classloader;

import org.flossware.messaging.MessageClient;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MessageClientClassSourceTest {

    @Test
    void testConstructorValidation() {
        assertThrows(NullPointerException.class, () -> {
            new MessageClientClassSource(null);
        });
    }

    @Test
    void testLoadClassData() throws IOException {
        MessageClient client = mock(MessageClient.class);
        byte[] expectedData = new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE};
        when(client.read("com/example/MyClass.class")).thenReturn(expectedData);

        MessageClientClassSource source = new MessageClientClassSource(client);
        byte[] data = source.loadClassData("com.example.MyClass");

        assertArrayEquals(expectedData, data);
        verify(client).read("com/example/MyClass.class");
    }

    @Test
    void testLoadClassDataThrowsIOException() throws IOException {
        MessageClient client = mock(MessageClient.class);
        when(client.read("com/example/MyClass.class"))
            .thenThrow(new IOException("Message not found"));

        MessageClientClassSource source = new MessageClientClassSource(client);

        IOException thrown = assertThrows(IOException.class, () -> {
            source.loadClassData("com.example.MyClass");
        });

        assertTrue(thrown.getMessage().contains("Message not found"));
    }

    @Test
    void testCanLoadReturnsTrue() throws IOException {
        MessageClient client = mock(MessageClient.class);
        when(client.exists("com/example/MyClass.class")).thenReturn(true);

        MessageClientClassSource source = new MessageClientClassSource(client);

        assertTrue(source.canLoad("com.example.MyClass"));
        verify(client).exists("com/example/MyClass.class");
    }

    @Test
    void testCanLoadReturnsFalse() throws IOException {
        MessageClient client = mock(MessageClient.class);
        when(client.exists("com/example/MyClass.class")).thenReturn(false);

        MessageClientClassSource source = new MessageClientClassSource(client);

        assertFalse(source.canLoad("com.example.MyClass"));
    }

    @Test
    void testCanLoadReturnsFalseOnIOException() throws IOException {
        MessageClient client = mock(MessageClient.class);
        when(client.exists("com/example/MyClass.class"))
            .thenThrow(new IOException("Connection error"));

        MessageClientClassSource source = new MessageClientClassSource(client);

        assertFalse(source.canLoad("com.example.MyClass"));
    }

    @Test
    void testGetDescription() {
        MessageClient client = mock(MessageClient.class);
        when(client.getDescription()).thenReturn("Kafka[topic=classes]");

        MessageClientClassSource source = new MessageClientClassSource(client);

        assertEquals("MessageClientClassSource[Kafka[topic=classes]]", source.getDescription());
    }

    @Test
    void testGetClient() {
        MessageClient client = mock(MessageClient.class);
        MessageClientClassSource source = new MessageClientClassSource(client);

        assertSame(client, source.getClient());
    }

    @Test
    void testClose() throws IOException {
        MessageClient client = mock(MessageClient.class);
        MessageClientClassSource source = new MessageClientClassSource(client);

        source.close();

        verify(client).close();
    }

    @Test
    void testCloseWithIOException() throws IOException {
        MessageClient client = mock(MessageClient.class);
        doThrow(new IOException("Close failed")).when(client).close();

        MessageClientClassSource source = new MessageClientClassSource(client);

        IOException thrown = assertThrows(IOException.class, source::close);
        assertTrue(thrown.getMessage().contains("Close failed"));
    }

    @Test
    void testNestedPackageClassName() throws IOException {
        MessageClient client = mock(MessageClient.class);
        byte[] data = new byte[]{1, 2, 3};
        when(client.read("com/example/deep/nested/MyClass.class")).thenReturn(data);

        MessageClientClassSource source = new MessageClientClassSource(client);
        byte[] result = source.loadClassData("com.example.deep.nested.MyClass");

        assertArrayEquals(data, result);
    }

    @Test
    void testInnerClassName() throws IOException {
        MessageClient client = mock(MessageClient.class);
        byte[] data = new byte[]{4, 5, 6};
        when(client.read("com/example/Outer$Inner.class")).thenReturn(data);

        MessageClientClassSource source = new MessageClientClassSource(client);
        byte[] result = source.loadClassData("com.example.Outer$Inner");

        assertArrayEquals(data, result);
    }

    @Test
    void testDefaultPackageClassName() throws IOException {
        MessageClient client = mock(MessageClient.class);
        byte[] data = new byte[]{7, 8, 9};
        when(client.read("MyClass.class")).thenReturn(data);

        MessageClientClassSource source = new MessageClientClassSource(client);
        byte[] result = source.loadClassData("MyClass");

        assertArrayEquals(data, result);
    }
}
