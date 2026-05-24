package org.flossware.jclassloader.cloud;

import com.google.auth.oauth2.GoogleCredentials;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import static org.mockito.Mockito.mock;


/**
 * Tests for GoogleDriveClassSource builder and configuration.
 */
class GoogleDriveClassSourceTest {

    @Test
    void testBuilderWithFolderId() {
        GoogleDriveClassSource.Builder builder = GoogleDriveClassSource.builder()
                .folderId("my-folder-id");

        assertNotNull(builder);
    }

    @Test
    void testBuilderWithApplicationName() {
        GoogleDriveClassSource.Builder builder = GoogleDriveClassSource.builder()
                .applicationName("MyApp");

        assertNotNull(builder);
    }

    @Test
    void testBuilderChaining() {
        GoogleDriveClassSource.Builder builder = GoogleDriveClassSource.builder();
        assertSame(builder, builder.folderId("folder"));
        assertSame(builder, builder.applicationName("app"));
    }

    @Test
    void testBuilderWithCredentials() {
        GoogleCredentials mockCreds = mock(GoogleCredentials.class);
        GoogleDriveClassSource.Builder builder = GoogleDriveClassSource.builder()
                .credentials(mockCreds)
                .folderId("test-folder");

        assertNotNull(builder);
    }

    @Test
    void testBuilderDefaultApplicationName() {
        GoogleDriveClassSource.Builder builder = GoogleDriveClassSource.builder()
                .folderId("folder");

        assertNotNull(builder);
    }

    @Test
    void testBuilderWithNullFolderId() {
        GoogleDriveClassSource.Builder builder = GoogleDriveClassSource.builder()
                .folderId(null);

        assertNotNull(builder);
    }

    @Test
    void testBuilderWithEmptyFolderId() {
        GoogleDriveClassSource.Builder builder = GoogleDriveClassSource.builder()
                .folderId("");

        assertNotNull(builder);
    }

    @Test
    void testMultipleBuilderInstances() {
        GoogleDriveClassSource.Builder builder1 = GoogleDriveClassSource.builder()
                .folderId("folder1");

        GoogleDriveClassSource.Builder builder2 = GoogleDriveClassSource.builder()
                .folderId("folder2");

        assertNotSame(builder1, builder2);
    }

    @Test
    void testBuilderWithAllOptions() {
        GoogleCredentials mockCreds = mock(GoogleCredentials.class);
        GoogleDriveClassSource.Builder builder = GoogleDriveClassSource.builder()
                .credentials(mockCreds)
                .folderId("my-folder")
                .applicationName("CustomApp");

        assertNotNull(builder);
    }
}
