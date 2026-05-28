package org.flossware.classloader;

import org.flossware.cloud.storage.CloudStorageClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CloudStorageClassSourceTest {

    @Test
    void testConstructorValidation() {
        assertThrows(NullPointerException.class, () -> {
            new CloudStorageClassSource(null);
        });
    }

    @Test
    void testLoadClassData() throws IOException {
        CloudStorageClient client = mock(CloudStorageClient.class);
        byte[] expectedData = new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE};
        when(client.readFile("com/example/MyClass.class")).thenReturn(expectedData);

        CloudStorageClassSource source = new CloudStorageClassSource(client);
        byte[] data = source.loadClassData("com.example.MyClass");

        assertArrayEquals(expectedData, data);
        verify(client).readFile("com/example/MyClass.class");
    }

    @Test
    void testLoadClassDataThrowsIOException() throws IOException {
        CloudStorageClient client = mock(CloudStorageClient.class);
        when(client.readFile("com/example/MyClass.class"))
            .thenThrow(new IOException("File not found"));

        CloudStorageClassSource source = new CloudStorageClassSource(client);

        IOException thrown = assertThrows(IOException.class, () -> {
            source.loadClassData("com.example.MyClass");
        });

        assertTrue(thrown.getMessage().contains("File not found"));
    }

    @Test
    void testCanLoadReturnsTrue() throws IOException {
        CloudStorageClient client = mock(CloudStorageClient.class);
        when(client.exists("com/example/MyClass.class")).thenReturn(true);

        CloudStorageClassSource source = new CloudStorageClassSource(client);

        assertTrue(source.canLoad("com.example.MyClass"));
        verify(client).exists("com/example/MyClass.class");
    }

    @Test
    void testCanLoadReturnsFalse() throws IOException {
        CloudStorageClient client = mock(CloudStorageClient.class);
        when(client.exists("com/example/MyClass.class")).thenReturn(false);

        CloudStorageClassSource source = new CloudStorageClassSource(client);

        assertFalse(source.canLoad("com.example.MyClass"));
    }

    @Test
    void testCanLoadReturnsFalseOnIOException() throws IOException {
        CloudStorageClient client = mock(CloudStorageClient.class);
        when(client.exists("com/example/MyClass.class"))
            .thenThrow(new IOException("Network error"));

        CloudStorageClassSource source = new CloudStorageClassSource(client);

        assertFalse(source.canLoad("com.example.MyClass"));
    }

    @Test
    void testGetDescription() {
        CloudStorageClient client = mock(CloudStorageClient.class);
        when(client.getDescription()).thenReturn("S3[bucket=my-bucket]");

        CloudStorageClassSource source = new CloudStorageClassSource(client);

        assertEquals("CloudStorageClassSource[S3[bucket=my-bucket]]", source.getDescription());
    }

    @Test
    void testGetClient() {
        CloudStorageClient client = mock(CloudStorageClient.class);
        CloudStorageClassSource source = new CloudStorageClassSource(client);

        assertSame(client, source.getClient());
    }

    @Test
    void testClose() throws IOException {
        CloudStorageClient client = mock(CloudStorageClient.class);
        CloudStorageClassSource source = new CloudStorageClassSource(client);

        source.close();

        verify(client).close();
    }

    @Test
    void testCloseWithIOException() throws IOException {
        CloudStorageClient client = mock(CloudStorageClient.class);
        doThrow(new IOException("Close failed")).when(client).close();

        CloudStorageClassSource source = new CloudStorageClassSource(client);

        IOException thrown = assertThrows(IOException.class, source::close);
        assertTrue(thrown.getMessage().contains("Close failed"));
    }

    @Test
    void testNestedPackageClassName() throws IOException {
        CloudStorageClient client = mock(CloudStorageClient.class);
        byte[] data = new byte[]{1, 2, 3};
        when(client.readFile("com/example/deep/nested/MyClass.class")).thenReturn(data);

        CloudStorageClassSource source = new CloudStorageClassSource(client);
        byte[] result = source.loadClassData("com.example.deep.nested.MyClass");

        assertArrayEquals(data, result);
    }

    @Test
    void testInnerClassName() throws IOException {
        CloudStorageClient client = mock(CloudStorageClient.class);
        byte[] data = new byte[]{4, 5, 6};
        when(client.readFile("com/example/Outer$Inner.class")).thenReturn(data);

        CloudStorageClassSource source = new CloudStorageClassSource(client);
        byte[] result = source.loadClassData("com.example.Outer$Inner");

        assertArrayEquals(data, result);
    }

    @Test
    void testDefaultPackageClassName() throws IOException {
        CloudStorageClient client = mock(CloudStorageClient.class);
        byte[] data = new byte[]{7, 8, 9};
        when(client.readFile("MyClass.class")).thenReturn(data);

        CloudStorageClassSource source = new CloudStorageClassSource(client);
        byte[] result = source.loadClassData("MyClass");

        assertArrayEquals(data, result);
    }
}
