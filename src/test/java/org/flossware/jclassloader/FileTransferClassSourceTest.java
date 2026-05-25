package org.flossware.jclassloader;

import org.flossware.filetransfer.FileTransferClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FileTransferClassSourceTest {

    @Test
    void testConstructorValidation() {
        assertThrows(NullPointerException.class, () -> {
            new FileTransferClassSource(null);
        });
    }

    @Test
    void testLoadClassData() throws IOException {
        FileTransferClient client = mock(FileTransferClient.class);
        byte[] expectedData = new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE};
        when(client.readFile("com/example/MyClass.class")).thenReturn(expectedData);

        FileTransferClassSource source = new FileTransferClassSource(client);
        byte[] data = source.loadClassData("com.example.MyClass");

        assertArrayEquals(expectedData, data);
        verify(client).readFile("com/example/MyClass.class");
    }

    @Test
    void testLoadClassDataThrowsIOException() throws IOException {
        FileTransferClient client = mock(FileTransferClient.class);
        when(client.readFile("com/example/MyClass.class"))
            .thenThrow(new IOException("File not found"));

        FileTransferClassSource source = new FileTransferClassSource(client);

        IOException thrown = assertThrows(IOException.class, () -> {
            source.loadClassData("com.example.MyClass");
        });

        assertTrue(thrown.getMessage().contains("File not found"));
    }

    @Test
    void testCanLoadReturnsTrue() throws IOException {
        FileTransferClient client = mock(FileTransferClient.class);
        when(client.exists("com/example/MyClass.class")).thenReturn(true);

        FileTransferClassSource source = new FileTransferClassSource(client);

        assertTrue(source.canLoad("com.example.MyClass"));
        verify(client).exists("com/example/MyClass.class");
    }

    @Test
    void testCanLoadReturnsFalse() throws IOException {
        FileTransferClient client = mock(FileTransferClient.class);
        when(client.exists("com/example/MyClass.class")).thenReturn(false);

        FileTransferClassSource source = new FileTransferClassSource(client);

        assertFalse(source.canLoad("com.example.MyClass"));
    }

    @Test
    void testCanLoadReturnsFalseOnIOException() throws IOException {
        FileTransferClient client = mock(FileTransferClient.class);
        when(client.exists("com/example/MyClass.class"))
            .thenThrow(new IOException("Network error"));

        FileTransferClassSource source = new FileTransferClassSource(client);

        assertFalse(source.canLoad("com.example.MyClass"));
    }

    @Test
    void testGetDescription() {
        FileTransferClient client = mock(FileTransferClient.class);
        when(client.getDescription()).thenReturn("SFTP[host=example.com]");

        FileTransferClassSource source = new FileTransferClassSource(client);

        assertEquals("FileTransferClassSource[SFTP[host=example.com]]", source.getDescription());
    }

    @Test
    void testGetClient() {
        FileTransferClient client = mock(FileTransferClient.class);
        FileTransferClassSource source = new FileTransferClassSource(client);

        assertSame(client, source.getClient());
    }

    @Test
    void testClose() throws IOException {
        FileTransferClient client = mock(FileTransferClient.class);
        FileTransferClassSource source = new FileTransferClassSource(client);

        source.close();

        verify(client).close();
    }

    @Test
    void testCloseWithIOException() throws IOException {
        FileTransferClient client = mock(FileTransferClient.class);
        doThrow(new IOException("Close failed")).when(client).close();

        FileTransferClassSource source = new FileTransferClassSource(client);

        IOException thrown = assertThrows(IOException.class, source::close);
        assertTrue(thrown.getMessage().contains("Close failed"));
    }

    @Test
    void testNestedPackageClassName() throws IOException {
        FileTransferClient client = mock(FileTransferClient.class);
        byte[] data = new byte[]{1, 2, 3};
        when(client.readFile("com/example/deep/nested/MyClass.class")).thenReturn(data);

        FileTransferClassSource source = new FileTransferClassSource(client);
        byte[] result = source.loadClassData("com.example.deep.nested.MyClass");

        assertArrayEquals(data, result);
    }

    @Test
    void testInnerClassName() throws IOException {
        FileTransferClient client = mock(FileTransferClient.class);
        byte[] data = new byte[]{4, 5, 6};
        when(client.readFile("com/example/Outer$Inner.class")).thenReturn(data);

        FileTransferClassSource source = new FileTransferClassSource(client);
        byte[] result = source.loadClassData("com.example.Outer$Inner");

        assertArrayEquals(data, result);
    }

    @Test
    void testDefaultPackageClassName() throws IOException {
        FileTransferClient client = mock(FileTransferClient.class);
        byte[] data = new byte[]{7, 8, 9};
        when(client.readFile("MyClass.class")).thenReturn(data);

        FileTransferClassSource source = new FileTransferClassSource(client);
        byte[] result = source.loadClassData("MyClass");

        assertArrayEquals(data, result);
    }
}
