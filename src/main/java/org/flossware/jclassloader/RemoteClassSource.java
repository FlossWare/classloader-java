package org.flossware.jclassloader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Base64;
import java.util.Objects;

public class RemoteClassSource implements ClassSource {
    private final String baseUrl;
    private final AuthConfig authConfig;

    public RemoteClassSource(String baseUrl, AuthConfig authConfig) {
        Objects.requireNonNull(baseUrl, "baseUrl cannot be null");
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        this.authConfig = authConfig != null ? authConfig : AuthConfig.none();
    }

    public RemoteClassSource(String baseUrl) {
        this(baseUrl, AuthConfig.none());
    }

    @Override
    public byte[] loadClassData(String className) throws IOException {
        String classPath = className.replace('.', '/') + ".class";
        URL url = new URL(baseUrl + classPath);

        URLConnection connection = url.openConnection();

        if (connection instanceof HttpURLConnection) {
            HttpURLConnection httpConnection = (HttpURLConnection) connection;
            configureAuthentication(httpConnection);

            int responseCode = httpConnection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP error code: " + responseCode + " for URL: " + url);
            }
        }

        try (InputStream in = connection.getInputStream();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }

            return out.toByteArray();
        }
    }

    @Override
    public boolean canLoad(String className) {
        try {
            String classPath = className.replace('.', '/') + ".class";
            URL url = new URL(baseUrl + classPath);
            URLConnection connection = url.openConnection();

            if (connection instanceof HttpURLConnection) {
                HttpURLConnection httpConnection = (HttpURLConnection) connection;
                httpConnection.setRequestMethod("HEAD");
                configureAuthentication(httpConnection);

                int responseCode = httpConnection.getResponseCode();
                return responseCode == HttpURLConnection.HTTP_OK;
            }

            connection.connect();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public String getDescription() {
        return "RemoteClassSource[" + baseUrl + ", auth=" + authConfig.getAuthType() + "]";
    }

    private void configureAuthentication(HttpURLConnection connection) {
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

    public String getBaseUrl() {
        return baseUrl;
    }

    public AuthConfig getAuthConfig() {
        return authConfig;
    }
}
