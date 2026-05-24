package org.flossware.jclassloader;

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
     */
    public static void configureAuth(HttpURLConnection connection, AuthConfig authConfig) {
        if (authConfig == null) {
            return;
        }

        switch (authConfig.getAuthType()) {
            case BASIC:
                String credentials = authConfig.getUsername() + ":" + authConfig.getPassword();
                String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
                connection.setRequestProperty("Authorization", "Basic " + encodedCredentials);
                break;
            case BEARER:
                connection.setRequestProperty("Authorization", "Bearer " + authConfig.getToken());
                break;
            case NONE:
            default:
                break;
        }
    }
}
