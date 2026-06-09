package org.flossware.classloader;

import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Utility class for configuring HTTP authentication on connections.
 * Provides helper methods to apply Basic or Bearer token authentication.
 */
public class AuthHelper {

    /**
     * Configures authentication headers on an HTTP connection based on the provided AuthConfig.
     *
     * @param connection The HTTP connection to configure
     * @param authConfig The authentication configuration (null for no authentication)
     * @throws IllegalArgumentException if authentication credentials are null when required
     */
    public static void configureAuth(HttpURLConnection connection, AuthConfig authConfig) {
        if (authConfig == null) {
            return;
        }

        switch (authConfig.getAuthType()) {
            case BASIC:
                configureBasicAuth(connection, authConfig);
                break;
            case BEARER:
                configureBearerAuth(connection, authConfig);
                break;
            case NONE:
            default:
                break;
        }
    }

    private static void configureBasicAuth(HttpURLConnection connection, AuthConfig authConfig) {
        String username = authConfig.getUsername();
        String password = authConfig.getPassword();
        if (username == null || password == null) {
<<<<<<< Updated upstream
            throw new IllegalArgumentException("Username and password must not be null for BASIC authentication");
        }
        String credentials = username + ":" + password;
        String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        connection.setRequestProperty("Authorization", "Basic " + encodedCredentials);
=======
            throw new IllegalArgumentException(
                "Username and password required with BASIC auth");
        }
        String credentials = username + ":" + password;
        String encoded = Base64.getEncoder().encodeToString(
            credentials.getBytes(StandardCharsets.UTF_8));
        connection.setRequestProperty("Authorization", "Basic " + encoded);
>>>>>>> Stashed changes
    }

    private static void configureBearerAuth(HttpURLConnection connection, AuthConfig authConfig) {
        String token = authConfig.getToken();
        if (token == null) {
<<<<<<< Updated upstream
            throw new IllegalArgumentException("Token must not be null for BEARER authentication");
=======
            throw new IllegalArgumentException(
                "Token required with BEARER auth");
>>>>>>> Stashed changes
        }
        connection.setRequestProperty("Authorization", "Bearer " + token);
    }
}
