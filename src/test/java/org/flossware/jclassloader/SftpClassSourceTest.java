package org.flossware.jclassloader;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SftpClassSource builder and configuration.
 */
class SftpClassSourceTest {

    @Test
    void testBuilderWithPasswordAuth() {
        SftpClassSource source = SftpClassSource.builder()
                .host("example.com")
                .username("user")
                .password("pass")
                .build();

        assertNotNull(source);
        assertTrue(source.getDescription().contains("user@example.com:22/"));
    }

    @Test
    void testBuilderWithPrivateKeyAuth() {
        SftpClassSource source = SftpClassSource.builder()
                .host("example.com")
                .username("user")
                .privateKey("/path/to/key")
                .build();

        assertNotNull(source);
    }

    @Test
    void testBuilderWithCustomPort() {
        SftpClassSource source = SftpClassSource.builder()
                .host("example.com")
                .port(2222)
                .username("user")
                .password("pass")
                .build();

        assertTrue(source.getDescription().contains(":2222"));
    }

    @Test
    void testBuilderWithBasePath() {
        SftpClassSource source = SftpClassSource.builder()
                .host("example.com")
                .username("user")
                .password("pass")
                .basePath("/data/classes")
                .build();

        assertTrue(source.getDescription().contains("/data/classes"));
    }

    @Test
    void testBuilderWithKnownHostsFile() {
        SftpClassSource source = SftpClassSource.builder()
                .host("example.com")
                .username("user")
                .password("pass")
                .knownHostsFile("/home/user/.ssh/known_hosts")
                .build();

        assertNotNull(source);
    }

    @Test
    void testBuilderMissingHostThrowsException() {
        assertThrows(NullPointerException.class, () -> {
            SftpClassSource.builder()
                    .username("user")
                    .password("pass")
                    .build();
        });
    }

    @Test
    void testBuilderMissingUsernameThrowsException() {
        assertThrows(NullPointerException.class, () -> {
            SftpClassSource.builder()
                    .host("example.com")
                    .password("pass")
                    .build();
        });
    }

    @Test
    void testBuilderMissingAuthThrowsException() {
        assertThrows(IllegalStateException.class, () -> {
            SftpClassSource.builder()
                    .host("example.com")
                    .username("user")
                    .build();
        });
    }

    @Test
    void testBuilderInvalidPortThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            SftpClassSource.builder()
                    .host("example.com")
                    .port(0)
                    .username("user")
                    .password("pass")
                    .build();
        });
    }

    @Test
    void testBuilderPortTooHighThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            SftpClassSource.builder()
                    .host("example.com")
                    .port(65536)
                    .username("user")
                    .password("pass")
                    .build();
        });
    }

    @Test
    void testBuilderNegativePortThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            SftpClassSource.builder()
                    .host("example.com")
                    .port(-1)
                    .username("user")
                    .password("pass")
                    .build();
        });
    }

    @Test
    void testGetDescription() {
        SftpClassSource source = SftpClassSource.builder()
                .host("sftp.example.com")
                .port(22)
                .username("admin")
                .password("secret")
                .basePath("/classes")
                .build();

        String description = source.getDescription();
        assertTrue(description.contains("SftpClassSource"));
        assertTrue(description.contains("admin@sftp.example.com:22/classes"));
    }

    @Test
    void testDefaultPort() {
        SftpClassSource source = SftpClassSource.builder()
                .host("example.com")
                .username("user")
                .password("pass")
                .build();

        assertTrue(source.getDescription().contains(":22"));
    }

    @Test
    void testDefaultBasePath() {
        SftpClassSource source = SftpClassSource.builder()
                .host("example.com")
                .username("user")
                .password("pass")
                .build();

        assertTrue(source.getDescription().contains(":22/"));
    }

    @Test
    void testCloseDoesNotThrow() {
        SftpClassSource source = SftpClassSource.builder()
                .host("example.com")
                .username("user")
                .password("pass")
                .build();

        assertDoesNotThrow(() -> source.close());
    }

    @Test
    void testDisconnectDoesNotThrow() {
        SftpClassSource source = SftpClassSource.builder()
                .host("example.com")
                .username("user")
                .password("pass")
                .build();

        assertDoesNotThrow(() -> source.disconnect());
    }

    @Test
    void testMultipleClose() {
        SftpClassSource source = SftpClassSource.builder()
                .host("example.com")
                .username("user")
                .password("pass")
                .build();

        assertDoesNotThrow(() -> {
            source.close();
            source.close();
        });
    }

    @Test
    void testBuilderWithAllOptions() {
        SftpClassSource source = SftpClassSource.builder()
                .host("sftp.example.com")
                .port(2222)
                .username("admin")
                .password("secret")
                .basePath("/data/classes")
                .knownHostsFile("/home/user/.ssh/known_hosts")
                .build();

        String description = source.getDescription();
        assertTrue(description.contains("admin@sftp.example.com:2222/data/classes"));
    }

    @Test
    void testBuilderBothPasswordAndPrivateKey() {
        SftpClassSource source = SftpClassSource.builder()
                .host("example.com")
                .username("user")
                .password("pass")
                .privateKey("/path/to/key")
                .build();

        assertNotNull(source);
    }

    @Test
    void testValidPortRange() {
        assertDoesNotThrow(() -> {
            SftpClassSource.builder()
                    .host("example.com")
                    .port(1)
                    .username("user")
                    .password("pass")
                    .build();
        });

        assertDoesNotThrow(() -> {
            SftpClassSource.builder()
                    .host("example.com")
                    .port(65535)
                    .username("user")
                    .password("pass")
                    .build();
        });
    }
}
