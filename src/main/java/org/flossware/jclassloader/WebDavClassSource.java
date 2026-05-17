package org.flossware.jclassloader;

import com.github.sardine.Sardine;
import com.github.sardine.SardineFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

public class WebDavClassSource implements ClassSource {
    private final String baseUrl;
    private final String username;
    private final String password;
    private final Sardine sardine;

    public WebDavClassSource(String baseUrl, String username, String password) {
        Objects.requireNonNull(baseUrl, "baseUrl cannot be null");
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        this.username = username;
        this.password = password;

        if (username != null && password != null) {
            this.sardine = SardineFactory.begin(username, password);
        } else {
            this.sardine = SardineFactory.begin();
        }
    }

    public WebDavClassSource(String baseUrl) {
        this(baseUrl, null, null);
    }

    @Override
    public byte[] loadClassData(String className) throws IOException {
        String classPath = className.replace('.', '/') + ".class";
        String url = baseUrl + classPath;

        try (InputStream in = sardine.get(url);
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
            String url = baseUrl + classPath;
            return sardine.exists(url);
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public String getDescription() {
        return "WebDavClassSource[" + baseUrl + ", authenticated=" + (username != null) + "]";
    }

    public void shutdown() throws IOException {
        sardine.shutdown();
    }

    public String getBaseUrl() {
        return baseUrl;
    }
}
