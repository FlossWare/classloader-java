package org.flossware.jclassloader.cloud;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import org.flossware.jclassloader.ClassSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;

/**
 * ClassSource implementation for loading classes from Google Drive.
 * Supports service account and OAuth credentials authentication.
 * Requires the Google Drive API SDK dependency.
 */
import java.util.Map;
import java.util.Objects;

public class GoogleDriveClassSource implements ClassSource {
    private final Drive driveService;
    private final String folderId;
    private final Map<String, String> pathToFileIdCache;

    private GoogleDriveClassSource(Drive driveService, String folderId) {
        this.driveService = Objects.requireNonNull(driveService, "driveService cannot be null");
        this.folderId = folderId;
        this.pathToFileIdCache = new HashMap<>();
    }

    @Override
    public byte[] loadClassData(String className) throws IOException {
        String filePath = className.replace('.', '/') + ".class";
        String fileId = findFileId(filePath);

        if (fileId == null) {
            throw new IOException("Class file not found in Google Drive: " + filePath);
        }

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            driveService.files().get(fileId).executeMediaAndDownloadTo(out);
            return out.toByteArray();
        }
    }

    @Override
    public boolean canLoad(String className) {
        String filePath = className.replace('.', '/') + ".class";
        try {
            return findFileId(filePath) != null;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public String getDescription() {
        return "GoogleDriveClassSource[folder=" + folderId + "]";
    }

    private String findFileId(String filePath) throws IOException {
        if (pathToFileIdCache.containsKey(filePath)) {
            return pathToFileIdCache.get(filePath);
        }

        String fileName = filePath.substring(filePath.lastIndexOf('/') + 1);
        String query = "name='" + fileName + "'";

        if (folderId != null) {
            query += " and '" + folderId + "' in parents";
        }

        query += " and trashed=false";

        FileList result = driveService.files().list()
            .setQ(query)
            .setSpaces("drive")
            .setFields("files(id, name)")
            .execute();

        if (result.getFiles() != null && !result.getFiles().isEmpty()) {
            File file = result.getFiles().get(0);
            pathToFileIdCache.put(filePath, file.getId());
            return file.getId();
        }

        return null;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private GoogleCredentials credentials;
        private String folderId;
        private String applicationName = "JClassLoader";

        public Builder credentials(GoogleCredentials credentials) {
            this.credentials = credentials;
            return this;
        }

        public Builder credentialsFromStream(InputStream credentialsStream) throws IOException {
            this.credentials = GoogleCredentials.fromStream(credentialsStream)
                .createScoped(Collections.singletonList("https://www.googleapis.com/auth/drive.readonly"));
            return this;
        }

        public Builder folderId(String folderId) {
            this.folderId = folderId;
            return this;
        }

        public Builder applicationName(String applicationName) {
            this.applicationName = applicationName;
            return this;
        }

        public GoogleDriveClassSource build() throws Exception {
            if (credentials == null) {
                credentials = GoogleCredentials.getApplicationDefault()
                    .createScoped(Collections.singletonList("https://www.googleapis.com/auth/drive.readonly"));
            }

            HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
            JsonFactory jsonFactory = GsonFactory.getDefaultInstance();

            Drive driveService = new Drive.Builder(
                httpTransport,
                jsonFactory,
                new HttpCredentialsAdapter(credentials))
                .setApplicationName(applicationName)
                .build();

            return new GoogleDriveClassSource(driveService, folderId);
        }
    }
}
