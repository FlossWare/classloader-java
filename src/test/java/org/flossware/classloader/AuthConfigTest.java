package org.flossware.classloader;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;


/**
 * Tests for AuthConfig.
 */
class AuthConfigTest {

    @Test
    void testNone() {
        AuthConfig auth = AuthConfig.none();

        assertEquals(AuthConfig.AuthType.NONE, auth.getAuthType());
        assertNull(auth.getUsername());
        assertNull(auth.getPassword());
        assertNull(auth.getToken());
    }

    @Test
    void testBasic() {
        AuthConfig auth = AuthConfig.basic("user", "pass");

        assertEquals(AuthConfig.AuthType.BASIC, auth.getAuthType());
        assertEquals("user", auth.getUsername());
        assertEquals("pass", auth.getPassword());
        assertNull(auth.getToken());
    }

    @Test
    void testBearer() {
        AuthConfig auth = AuthConfig.bearer("my-token");

        assertEquals(AuthConfig.AuthType.BEARER, auth.getAuthType());
        assertEquals("my-token", auth.getToken());
        assertNull(auth.getUsername());
        assertNull(auth.getPassword());
    }

    @Test
    void testBasicNullUsernameThrowsException() {
        assertThrows(NullPointerException.class, () -> {
            AuthConfig.basic(null, "pass");
        });
    }

    @Test
    void testBasicNullPasswordThrowsException() {
        assertThrows(NullPointerException.class, () -> {
            AuthConfig.basic("user", null);
        });
    }

    @Test
    void testBearerNullTokenThrowsException() {
        assertThrows(NullPointerException.class, () -> {
            AuthConfig.bearer(null);
        });
    }

    @Test
    void testBasicWithEmptyCredentials() {
        AuthConfig auth = AuthConfig.basic("", "");

        assertEquals(AuthConfig.AuthType.BASIC, auth.getAuthType());
        assertEquals("", auth.getUsername());
        assertEquals("", auth.getPassword());
    }

    @Test
    void testBearerWithEmptyToken() {
        AuthConfig auth = AuthConfig.bearer("");

        assertEquals(AuthConfig.AuthType.BEARER, auth.getAuthType());
        assertEquals("", auth.getToken());
    }

    @Test
    void testBasicWithSpecialCharacters() {
        AuthConfig auth = AuthConfig.basic("user@domain.com", "p@$$w0rd!");

        assertEquals("user@domain.com", auth.getUsername());
        assertEquals("p@$$w0rd!", auth.getPassword());
    }

    @Test
    void testBearerWithLongToken() {
        String longToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";
        AuthConfig auth = AuthConfig.bearer(longToken);

        assertEquals(longToken, auth.getToken());
    }
}
