package org.flossware.jclassloader;

import java.net.HttpURLConnection;
import java.util.Base64;

public class AuthHelper {

    public static void configureAuth(HttpURLConnection connection, AuthConfig authConfig) {
        if (authConfig == null) {
            return;
        }

        switch (authConfig.getAuthType()) {
            case BASIC:
                String credentials = authConfig.getUsername() + ":" + authConfig.getPassword();
                String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes());
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
