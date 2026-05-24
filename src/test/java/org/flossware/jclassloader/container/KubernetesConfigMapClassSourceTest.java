package org.flossware.jclassloader.container;

import io.kubernetes.client.openapi.ApiClient;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.mockito.Mockito.mock;


/**
 * Tests for KubernetesConfigMapClassSource builder and configuration.
 */
class KubernetesConfigMapClassSourceTest {

    @Test
    void testBuilderWithConfigMapAndNamespace() throws IOException {
        ApiClient mockClient = mock(ApiClient.class);
        KubernetesConfigMapClassSource source = KubernetesConfigMapClassSource.builder()
                .apiClient(mockClient)
                .configMapName("class-config")
                .namespace("default")
                .build();

        assertNotNull(source);
        assertTrue(source.getDescription().contains("class-config"));
    }

    @Test
    void testBuilderNullConfigMapNameThrowsException() {
        ApiClient mockClient = mock(ApiClient.class);
        assertThrows(NullPointerException.class, () -> {
            KubernetesConfigMapClassSource.builder()
                    .apiClient(mockClient)
                    .namespace("default")
                    .build();
        });
    }

    @Test
    void testGetDescription() throws IOException {
        ApiClient mockClient = mock(ApiClient.class);
        KubernetesConfigMapClassSource source = KubernetesConfigMapClassSource.builder()
                .apiClient(mockClient)
                .configMapName("my-config")
                .namespace("production")
                .build();

        String description = source.getDescription();
        assertTrue(description.contains("KubernetesConfigMapClassSource"));
        assertTrue(description.contains("my-config"));
        assertTrue(description.contains("production"));
    }

    @Test
    void testBuilderChaining() {
        ApiClient mockClient = mock(ApiClient.class);
        KubernetesConfigMapClassSource.Builder builder = KubernetesConfigMapClassSource.builder();
        assertSame(builder, builder.configMapName("config"));
        assertSame(builder, builder.namespace("namespace"));
        assertSame(builder, builder.apiClient(mockClient));
    }

    @Test
    void testBuilderDefaultNamespace() throws IOException {
        ApiClient mockClient = mock(ApiClient.class);
        KubernetesConfigMapClassSource source = KubernetesConfigMapClassSource.builder()
                .apiClient(mockClient)
                .configMapName("class-config")
                .build();

        assertTrue(source.getDescription().contains("namespace=default"));
    }

    @Test
    void testMultipleInstances() throws IOException {
        ApiClient mockClient = mock(ApiClient.class);
        KubernetesConfigMapClassSource source1 = KubernetesConfigMapClassSource.builder()
                .apiClient(mockClient)
                .configMapName("config1")
                .namespace("ns1")
                .build();

        KubernetesConfigMapClassSource source2 = KubernetesConfigMapClassSource.builder()
                .apiClient(mockClient)
                .configMapName("config2")
                .namespace("ns2")
                .build();

        assertNotEquals(source1.getDescription(), source2.getDescription());
    }

    @Test
    void testBuilderWithApiClient() throws IOException {
        ApiClient mockClient = mock(ApiClient.class);
        KubernetesConfigMapClassSource source = KubernetesConfigMapClassSource.builder()
                .apiClient(mockClient)
                .configMapName("class-config")
                .namespace("test")
                .build();

        assertNotNull(source);
    }

}

