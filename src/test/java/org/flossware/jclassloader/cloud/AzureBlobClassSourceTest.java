package org.flossware.jclassloader.cloud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for AzureBlobClassSource builder and configuration.
 */
class AzureBlobClassSourceTest {

    @Test
    void testBuilderWithConnectionString() {
        AzureBlobClassSource source = AzureBlobClassSource.builder()
                .connectionString("DefaultEndpointsProtocol=https;AccountName=test;AccountKey=key==;EndpointSuffix=core.windows.net")
                .container("classes")
                .build();

        assertNotNull(source);
        assertTrue(source.getDescription().contains("container=classes"));
    }

    @Test
    void testBuilderWithAccountKeyCredential() {
        AzureBlobClassSource source = AzureBlobClassSource.builder()
                .accountName("testaccount")
                .accountKey("dGVzdGtleQ==")
                .container("classes")
                .build();

        assertNotNull(source);
    }

    @Test
    void testBuilderWithPrefix() {
        AzureBlobClassSource source = AzureBlobClassSource.builder()
                .connectionString("DefaultEndpointsProtocol=https;AccountName=devstoreaccount1;AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;BlobEndpoint=http://127.0.0.1:10000/devstoreaccount1;")
                .container("classes")
                .prefix("app/")
                .build();

        assertTrue(source.getDescription().contains("prefix=app/"));
    }

    @Test
    void testBuilderWithEndpoint() {
        AzureBlobClassSource source = AzureBlobClassSource.builder()
                .accountName("testaccount")
                .accountKey("key")
                .container("classes")
                .endpoint("https://testaccount.blob.core.windows.net")
                .build();

        assertNotNull(source);
    }

    @Test
    void testBuilderNullContainerThrowsException() {
        assertThrows(NullPointerException.class, () -> {
            AzureBlobClassSource.builder()
                    .connectionString("conn")
                    .build();
        });
    }

    @Test
    void testBuilderNullConnectionStringAndNoAccountThrowsException() {
        assertThrows(IllegalStateException.class, () -> {
            AzureBlobClassSource.builder()
                    .container("classes")
                    .build();
        });
    }

    @Test
    void testGetDescription() {
        AzureBlobClassSource source = AzureBlobClassSource.builder()
                .connectionString("DefaultEndpointsProtocol=https;AccountName=devstoreaccount1;AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;BlobEndpoint=http://127.0.0.1:10000/devstoreaccount1;")
                .container("my-container")
                .prefix("classes/")
                .build();

        String description = source.getDescription();
        assertTrue(description.contains("AzureBlobClassSource"));
        assertTrue(description.contains("container=my-container"));
        assertTrue(description.contains("prefix=classes/"));
    }

    @Test
    void testBuilderChaining() {
        AzureBlobClassSource.Builder builder = AzureBlobClassSource.builder();
        assertSame(builder, builder.container("test"));
        assertSame(builder, builder.prefix("prefix/"));
        assertSame(builder, builder.connectionString("conn"));
        assertSame(builder, builder.accountName("name"));
        assertSame(builder, builder.accountKey("key"));
    }

    @Test
    void testMultipleInstances() {
        String validConnStr = "DefaultEndpointsProtocol=https;AccountName=devstoreaccount1;AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;BlobEndpoint=http://127.0.0.1:10000/devstoreaccount1;";
        AzureBlobClassSource source1 = AzureBlobClassSource.builder()
                .connectionString(validConnStr)
                .container("container1")
                .build();

        AzureBlobClassSource source2 = AzureBlobClassSource.builder()
                .connectionString(validConnStr)
                .container("container2")
                .build();

        assertNotEquals(source1.getDescription(), source2.getDescription());
    }

    @Test
    void testBuilderWithNullPrefix() {
        String validConnStr = "DefaultEndpointsProtocol=https;AccountName=devstoreaccount1;AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;BlobEndpoint=http://127.0.0.1:10000/devstoreaccount1;";
        AzureBlobClassSource source = AzureBlobClassSource.builder()
                .connectionString(validConnStr)
                .container("classes")
                .prefix(null)
                .build();

        assertTrue(source.getDescription().contains("prefix="));
    }

    @Test
    void testBuilderWithEmptyPrefix() {
        String validConnStr = "DefaultEndpointsProtocol=https;AccountName=devstoreaccount1;AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;BlobEndpoint=http://127.0.0.1:10000/devstoreaccount1;";
        AzureBlobClassSource source = AzureBlobClassSource.builder()
                .connectionString(validConnStr)
                .container("classes")
                .prefix("")
                .build();

        assertTrue(source.getDescription().contains("prefix="));
    }

}
