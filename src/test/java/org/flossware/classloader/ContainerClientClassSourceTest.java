package org.flossware.classloader;

import org.flossware.container.ContainerClient;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ContainerClientClassSourceTest {

    @Test
    void testConstructorValidationNullClient() {
        assertThrows(NullPointerException.class, () -> {
            new ContainerClientClassSource(null, "resource");
        });
    }

    @Test
    void testConstructorValidationNullResourceName() {
        ContainerClient client = mock(ContainerClient.class);
        assertThrows(NullPointerException.class, () -> {
            new ContainerClientClassSource(client, null);
        });
    }

    @Test
    void testLoadClassData() throws IOException {
        ContainerClient client = mock(ContainerClient.class);
        byte[] expectedData = new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE};
        when(client.read("class-map", "com/example/MyClass.class")).thenReturn(expectedData);

        ContainerClientClassSource source = new ContainerClientClassSource(client, "class-map");
        byte[] data = source.loadClassData("com.example.MyClass");

        assertArrayEquals(expectedData, data);
        verify(client).read("class-map", "com/example/MyClass.class");
    }

    @Test
    void testLoadClassDataThrowsIOException() throws IOException {
        ContainerClient client = mock(ContainerClient.class);
        when(client.read("class-map", "com/example/MyClass.class"))
            .thenThrow(new IOException("Key not found"));

        ContainerClientClassSource source = new ContainerClientClassSource(client, "class-map");

        IOException thrown = assertThrows(IOException.class, () -> {
            source.loadClassData("com.example.MyClass");
        });

        assertTrue(thrown.getMessage().contains("Key not found"));
    }

    @Test
    void testCanLoadReturnsTrue() throws IOException {
        ContainerClient client = mock(ContainerClient.class);
        when(client.exists("class-map", "com/example/MyClass.class")).thenReturn(true);

        ContainerClientClassSource source = new ContainerClientClassSource(client, "class-map");

        assertTrue(source.canLoad("com.example.MyClass"));
        verify(client).exists("class-map", "com/example/MyClass.class");
    }

    @Test
    void testCanLoadReturnsFalse() throws IOException {
        ContainerClient client = mock(ContainerClient.class);
        when(client.exists("class-map", "com/example/MyClass.class")).thenReturn(false);

        ContainerClientClassSource source = new ContainerClientClassSource(client, "class-map");

        assertFalse(source.canLoad("com.example.MyClass"));
    }

    @Test
    void testCanLoadReturnsFalseOnIOException() throws IOException {
        ContainerClient client = mock(ContainerClient.class);
        when(client.exists("class-map", "com/example/MyClass.class"))
            .thenThrow(new IOException("Connection error"));

        ContainerClientClassSource source = new ContainerClientClassSource(client, "class-map");

        assertFalse(source.canLoad("com.example.MyClass"));
    }

    @Test
    void testGetDescription() {
        ContainerClient client = mock(ContainerClient.class);
        when(client.getDescription()).thenReturn("Kubernetes[namespace=prod]");

        ContainerClientClassSource source = new ContainerClientClassSource(client, "app-classes");

        assertEquals("ContainerClientClassSource[Kubernetes[namespace=prod], resource=app-classes]",
                    source.getDescription());
    }

    @Test
    void testGetClient() {
        ContainerClient client = mock(ContainerClient.class);
        ContainerClientClassSource source = new ContainerClientClassSource(client, "class-map");

        assertSame(client, source.getClient());
    }

    @Test
    void testGetResourceName() {
        ContainerClient client = mock(ContainerClient.class);
        ContainerClientClassSource source = new ContainerClientClassSource(client, "my-resource");

        assertEquals("my-resource", source.getResourceName());
    }

    @Test
    void testClose() throws IOException {
        ContainerClient client = mock(ContainerClient.class);
        ContainerClientClassSource source = new ContainerClientClassSource(client, "class-map");

        source.close();

        verify(client).close();
    }

    @Test
    void testCloseWithIOException() throws IOException {
        ContainerClient client = mock(ContainerClient.class);
        doThrow(new IOException("Close failed")).when(client).close();

        ContainerClientClassSource source = new ContainerClientClassSource(client, "class-map");

        IOException thrown = assertThrows(IOException.class, source::close);
        assertTrue(thrown.getMessage().contains("Close failed"));
    }

    @Test
    void testNestedPackageClassName() throws IOException {
        ContainerClient client = mock(ContainerClient.class);
        byte[] data = new byte[]{1, 2, 3};
        when(client.read("map", "com/example/deep/nested/MyClass.class")).thenReturn(data);

        ContainerClientClassSource source = new ContainerClientClassSource(client, "map");
        byte[] result = source.loadClassData("com.example.deep.nested.MyClass");

        assertArrayEquals(data, result);
    }

    @Test
    void testInnerClassName() throws IOException {
        ContainerClient client = mock(ContainerClient.class);
        byte[] data = new byte[]{4, 5, 6};
        when(client.read("map", "com/example/Outer$Inner.class")).thenReturn(data);

        ContainerClientClassSource source = new ContainerClientClassSource(client, "map");
        byte[] result = source.loadClassData("com.example.Outer$Inner");

        assertArrayEquals(data, result);
    }

    @Test
    void testDefaultPackageClassName() throws IOException {
        ContainerClient client = mock(ContainerClient.class);
        byte[] data = new byte[]{7, 8, 9};
        when(client.read("map", "MyClass.class")).thenReturn(data);

        ContainerClientClassSource source = new ContainerClientClassSource(client, "map");
        byte[] result = source.loadClassData("MyClass");

        assertArrayEquals(data, result);
    }
}
