package org.flossware.jclassloader.cloud;

import org.flossware.jclassloader.AuthConfig;
import org.flossware.jclassloader.AuthHelper;
import org.flossware.jclassloader.ClassSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * OneDrive ClassSource using Microsoft Graph REST API.
 * Requires an OAuth access token with Files.Read.All permissions.
 */
public class OneDriveClassSource implements ClassSource {
    private static final String GRAPH_API_BASE = "https://graph.microsoft.com/v1.0";
    private final String accessToken;
    private final String basePath;
    private final String driveId;  // null for default drive

    private OneDriveClassSource(String accessToken, String basePath, String driveId) {
        this.accessToken = Objects.requireNonNull(accessToken, "accessToken cannot be null");
        this.basePath = basePath != null ? basePath : "";
        this.driveId = driveId;
    }

    @Override
    public byte[] loadClassData(String className) throws IOException {
        String filePath = buildFilePath(className);
        String downloadUrl = buildDownloadUrl(filePath);

        HttpURLConnection connection = (HttpURLConnection) new URL(downloadUrl).openConnection();
        connection.setRequestProperty("Authorization", "Bearer " + accessToken);
        connection.setRequestMethod("GET");

        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("HTTP error code: " + responseCode + " for OneDrive file: " + filePath);
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
            String filePath = buildFilePath(className);
            String metadataUrl = buildMetadataUrl(filePath);

            HttpURLConnection connection = (HttpURLConnection) new URL(metadataUrl).openConnection();
            connection.setRequestProperty("Authorization", "Bearer " + accessToken);
            connection.setRequestMethod("HEAD");

            int responseCode = connection.getResponseCode();
            return responseCode == HttpURLConnection.HTTP_OK;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public String getDescription() {
        return "OneDriveClassSource[basePath=" + basePath +
               ", drive=" + (driveId != null ? driveId : "default") + "]";
    }

    private String buildFilePath(String className) {
        String classPath = className.replace('.', '/') + ".class";

        if (basePath.isEmpty()) {
            return classPath;
        }

        String normalizedBase = basePath.endsWith("/") ?
            basePath.substring(0, basePath.length() - 1) : basePath;

        return normalizedBase + "/" + classPath;
    }

    private String buildDownloadUrl(String filePath) throws IOException {
        String encodedPath = URLEncoder.encode(filePath, StandardCharsets.UTF_8.name());

        if (driveId != null) {
            return GRAPH_API_BASE + "/me/drives/" + driveId + "/root:/" + encodedPath + ":/content";
        } else {
            return GRAPH_API_BASE + "/me/drive/root:/" + encodedPath + ":/content";
        }
    }

    private String buildMetadataUrl(String filePath) throws IOException {
        String encodedPath = URLEncoder.encode(filePath, StandardCharsets.UTF_8.name());

        if (driveId != null) {
            return GRAPH_API_BASE + "/me/drives/" + driveId + "/root:/" + encodedPath;
        } else {
            return GRAPH_API_BASE + "/me/drive/root:/" + encodedPath;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String accessToken;
        private String basePath;
        private String driveId;

        public Builder accessToken(String accessToken) {
            this.accessToken = accessToken;
            return this;
        }

        public Builder basePath(String basePath) {
            this.basePath = basePath;
            return this;
        }

        public Builder driveId(String driveId) {
            this.driveId = driveId;
            return this;
        }

        public OneDriveClassSource build() {
            Objects.requireNonNull(accessToken, "accessToken must be set");
            return new OneDriveClassSource(accessToken, basePath, driveId);
        }
    }
}
