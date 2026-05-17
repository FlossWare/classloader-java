package org.flossware.jclassloader;

import java.util.Objects;

public class AuthConfig {
    public enum AuthType {
        NONE,
        BASIC,
        BEARER
    }

    private final AuthType authType;
    private final String username;
    private final String password;
    private final String token;

    private AuthConfig(AuthType authType, String username, String password, String token) {
        this.authType = authType;
        this.username = username;
        this.password = password;
        this.token = token;
    }

    public static AuthConfig none() {
        return new AuthConfig(AuthType.NONE, null, null, null);
    }

    public static AuthConfig basic(String username, String password) {
        Objects.requireNonNull(username, "username cannot be null");
        Objects.requireNonNull(password, "password cannot be null");
        return new AuthConfig(AuthType.BASIC, username, password, null);
    }

    public static AuthConfig bearer(String token) {
        Objects.requireNonNull(token, "token cannot be null");
        return new AuthConfig(AuthType.BEARER, null, null, token);
    }

    public AuthType getAuthType() {
        return authType;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getToken() {
        return token;
    }
}
