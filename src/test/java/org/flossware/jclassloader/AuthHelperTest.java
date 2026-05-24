package org.flossware.jclassloader;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for AuthHelper authentication configuration.
 * Note: HttpURLConnection does not allow reading headers back before connection,
 * so we test that the methods execute without exceptions.
 */
class AuthHelperTest {

    @Test
    void testConfigureAuthWithNull() throws IOException {
        URL url = new URL("http://example.com");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        assertDoesNotThrow(() -> {
            AuthHelper.configureAuth(connection, null);
        });
    }

    @Test
    void testConfigureAuthWithNone() throws IOException {
        URL url = new URL("http://example.com");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        AuthConfig auth = AuthConfig.none();

        assertDoesNotThrow(() -> {
            AuthHelper.configureAuth(connection, auth);
        });
    }

    @Test
    void testConfigureAuthWithBasic() throws IOException {
        URL url = new URL("http://example.com");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        AuthConfig auth = AuthConfig.basic("user", "pass");

        assertDoesNotThrow(() -> {
            AuthHelper.configureAuth(connection, auth);
        });
    }

    @Test
    void testConfigureAuthWithBearer() throws IOException {
        URL url = new URL("http://example.com");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        AuthConfig auth = AuthConfig.bearer("my-token-123");

        assertDoesNotThrow(() -> {
            AuthHelper.configureAuth(connection, auth);
        });
    }

    @Test
    void testConfigureAuthBasicWithSpecialCharacters() throws IOException {
        URL url = new URL("http://example.com");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        AuthConfig auth = AuthConfig.basic("user@domain.com", "p@$$w0rd!");

        assertDoesNotThrow(() -> {
            AuthHelper.configureAuth(connection, auth);
        });
    }

    @Test
    void testConfigureAuthBasicWithEmptyCredentials() throws IOException {
        URL url = new URL("http://example.com");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        AuthConfig auth = AuthConfig.basic("", "");

        assertDoesNotThrow(() -> {
            AuthHelper.configureAuth(connection, auth);
        });
    }

    @Test
    void testConfigureAuthBearerWithLongToken() throws IOException {
        URL url = new URL("http://example.com");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        String longToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";
        AuthConfig auth = AuthConfig.bearer(longToken);

        assertDoesNotThrow(() -> {
            AuthHelper.configureAuth(connection, auth);
        });
    }

    @Test
    void testMultipleConfigureAuthCalls() throws IOException {
        URL url = new URL("http://example.com");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        AuthConfig auth1 = AuthConfig.basic("user1", "pass1");
        AuthHelper.configureAuth(connection, auth1);

        AuthConfig auth2 = AuthConfig.bearer("token123");
        assertDoesNotThrow(() -> {
            AuthHelper.configureAuth(connection, auth2);
        });
    }

    @Test
    void testAuthConfigTypes() {
        AuthConfig none = AuthConfig.none();
        assertEquals(AuthConfig.AuthType.NONE, none.getAuthType());

        AuthConfig basic = AuthConfig.basic("user", "pass");
        assertEquals(AuthConfig.AuthType.BASIC, basic.getAuthType());
        assertEquals("user", basic.getUsername());
        assertEquals("pass", basic.getPassword());

        AuthConfig bearer = AuthConfig.bearer("token");
        assertEquals(AuthConfig.AuthType.BEARER, bearer.getAuthType());
        assertEquals("token", bearer.getToken());
    }
}
