package org.flossware.classloader;

import java.util.Objects;

/**
 * Configuration for authentication when accessing remote class sources.
 * Supports Basic authentication (username/password) and Bearer token authentication.
 * Immutable value object with proper equals/hashCode/toString implementations.
 *
 * <h3>Security Note on Credential Storage</h3>
 * <p>This class stores credentials (passwords and tokens) as {@code String} objects rather
 * than {@code char[]} arrays. While Java security best practices typically recommend
 * {@code char[]} for password storage (to allow explicit clearing), this design is
 * acceptable in this context for the following reasons:</p>
 *
 * <ul>
 *   <li><b>HTTP API Compatibility:</b> HTTP authentication requires credentials to be
 *       Base64-encoded and sent as strings. Java's {@link java.net.HttpURLConnection}
 *       and most HTTP client APIs accept String credentials.</li>
 *   <li><b>Network Transmission:</b> Credentials must ultimately be converted to bytes/strings
 *       for network transmission, negating the memory security benefit of char arrays.</li>
 *   <li><b>Library Purpose:</b> This is a ClassLoader library for loading classes from
 *       remote sources, not a dedicated security or authentication library.</li>
 *   <li><b>Real Security:</b> Actual security comes from using HTTPS (encrypted transport),
 *       not hardcoding credentials, using environment variables or secrets managers,
 *       and preferring short-lived tokens over long-lived passwords.</li>
 * </ul>
 *
 * <h3>Security Best Practices</h3>
 * <p>When using this class in production:</p>
 * <ul>
 *   <li>Always use HTTPS for encrypted transport of credentials</li>
 *   <li>Never hardcode credentials in source code</li>
 *   <li>Load credentials from environment variables, configuration files with restricted
 *       permissions, or a secrets management system (HashiCorp Vault, AWS Secrets Manager, etc.)</li>
 *   <li>Prefer short-lived bearer tokens over long-lived passwords</li>
 *   <li>Rotate credentials regularly</li>
 *   <li>Use the principle of least privilege - grant only necessary permissions</li>
 * </ul>
 *
 * @see AuthHelper
 */
public final class AuthConfig {
    /**
     * Authentication type enumeration.
     */
    public enum AuthType {
        /** No authentication */
        NONE,
        /** HTTP Basic authentication (username and password) */
        BASIC,
        /** Bearer token authentication */
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

    /**
     * Creates an authentication configuration with no authentication.
     *
     * @return AuthConfig with NONE type
     */
    public static AuthConfig none() {
        return new AuthConfig(AuthType.NONE, null, null, null);
    }

    /**
     * Creates an authentication configuration using HTTP Basic authentication.
     *
     * @param username The username for authentication
     * @param password The password for authentication
     * @return AuthConfig with BASIC type
     * @throws NullPointerException if username or password is null
     */
    public static AuthConfig basic(String username, String password) {
        Objects.requireNonNull(username, "username cannot be null");
        Objects.requireNonNull(password, "password cannot be null");
        return new AuthConfig(AuthType.BASIC, username, password, null);
    }

    /**
     * Creates an authentication configuration using Bearer token authentication.
     *
     * @param token The bearer token for authentication
     * @return AuthConfig with BEARER type
     * @throws NullPointerException if token is null
     */
    public static AuthConfig bearer(String token) {
        Objects.requireNonNull(token, "token cannot be null");
        return new AuthConfig(AuthType.BEARER, null, null, token);
    }

    /**
     * Gets the authentication type.
     *
     * @return The authentication type
     */
    public AuthType getAuthType() {
        return authType;
    }

    /**
     * Gets the username for Basic authentication.
     *
     * @return The username, or null if not using Basic authentication
     */
    public String getUsername() {
        return username;
    }

    /**
     * Gets the password for Basic authentication.
     *
     * @return The password, or null if not using Basic authentication
     */
    public String getPassword() {
        return password;
    }

    /**
     * Gets the token for Bearer authentication.
     *
     * @return The token, or null if not using Bearer authentication
     */
    public String getToken() {
        return token;
    }

    /**
     * Compares this AuthConfig with another object for equality.
     * Two AuthConfig instances are equal if they have the same authentication type
     * and credentials (username/password or token).
     *
     * @param o The object to compare with
     * @return true if the objects are equal, false otherwise
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AuthConfig that = (AuthConfig) o;
        return authType == that.authType &&
               Objects.equals(username, that.username) &&
               Objects.equals(password, that.password) &&
               Objects.equals(token, that.token);
    }

    /**
     * Returns a hash code value for this AuthConfig.
     * The hash code is computed from the authentication type and credentials.
     *
     * @return A hash code value for this object
     */
    @Override
    public int hashCode() {
        return Objects.hash(authType, username, password, token);
    }

    /**
     * Returns a string representation of this AuthConfig.
     * Credentials are masked for security (passwords and tokens are not exposed).
     *
     * @return A string representation showing the type and username (if applicable)
     */
    @Override
    public String toString() {
        switch (authType) {
            case BASIC:
                return "AuthConfig[type=BASIC, username=" + username + "]";
            case BEARER:
                return "AuthConfig[type=BEARER, token=***]";
            case NONE:
            default:
                return "AuthConfig[type=NONE]";
        }
    }
}
