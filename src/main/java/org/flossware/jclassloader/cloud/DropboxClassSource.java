package org.flossware.jclassloader.cloud;

import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.Metadata;
import org.flossware.jclassloader.ClassSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Objects;

/**
 * ClassSource implementation for loading classes from Dropbox.
 * Supports OAuth access token authentication.
 * Requires the Dropbox Core SDK dependency.
 */
public class DropboxClassSource implements ClassSource {
    private final DbxClientV2 client;
    private final String basePath;

    private DropboxClassSource(DbxClientV2 client, String basePath) {
        this.client = Objects.requireNonNull(client, "client cannot be null");
        this.basePath = basePath != null ? basePath : "";
    }

    @Override
    public byte[] loadClassData(String className) throws IOException {
        String filePath = buildFilePath(className);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            client.files().download(filePath).download(out);
            return out.toByteArray();
        } catch (DbxException e) {
            throw new IOException("Failed to load class from Dropbox: " + filePath, e);
        }
    }

    @Override
    public boolean canLoad(String className) {
        String filePath = buildFilePath(className);

        try {
            Metadata metadata = client.files().getMetadata(filePath);
            return metadata instanceof FileMetadata;
        } catch (DbxException e) {
            return false;
        }
    }

    @Override
    public String getDescription() {
        return "DropboxClassSource[basePath=" + basePath + "]";
    }

    private String buildFilePath(String className) {
        String classPath = className.replace('.', '/') + ".class";

        if (basePath.isEmpty()) {
            return "/" + classPath;
        }

        String normalizedBase = basePath.startsWith("/") ? basePath : "/" + basePath;
        normalizedBase = normalizedBase.endsWith("/") ? normalizedBase.substring(0, normalizedBase.length() - 1) : normalizedBase;

        return normalizedBase + "/" + classPath;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String accessToken;
        private String basePath;
        private String clientIdentifier = "JClassLoader";

        public Builder accessToken(String accessToken) {
            this.accessToken = accessToken;
            return this;
        }

        public Builder basePath(String basePath) {
            this.basePath = basePath;
            return this;
        }

        public Builder clientIdentifier(String clientIdentifier) {
            this.clientIdentifier = clientIdentifier;
            return this;
        }

        public DropboxClassSource build() {
            Objects.requireNonNull(accessToken, "accessToken must be set");

            DbxRequestConfig config = DbxRequestConfig.newBuilder(clientIdentifier).build();
            DbxClientV2 client = new DbxClientV2(config, accessToken);

            return new DropboxClassSource(client, basePath);
        }
    }
}
