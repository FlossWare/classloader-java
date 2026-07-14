package org.flossware.classloader.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.Objects;

/**
 * URLStreamHandler that serves byte array data from memory.
 * Used to create URLs for resources loaded by ClassSource implementations
 * without requiring temporary files on disk.
 */
public class ByteArrayURLStreamHandler extends URLStreamHandler {
    private final byte[] data;

    public ByteArrayURLStreamHandler(byte[] data) {
        this.data = Objects.requireNonNull(data, "data cannot be null");
    }

    @Override
    protected URLConnection openConnection(URL url) {
        return new ByteArrayURLConnection(url, data);
    }

    private static class ByteArrayURLConnection extends URLConnection {
        private final byte[] data;

        ByteArrayURLConnection(URL url, byte[] data) {
            super(url);
            this.data = data;
        }

        @Override
        public void connect() {
            connected = true;
        }

        @Override
        public InputStream getInputStream() {
            connected = true;
            return new ByteArrayInputStream(data);
        }

        @Override
        public int getContentLength() {
            return data.length;
        }

        @Override
        public long getContentLengthLong() {
            return data.length;
        }

        @Override
        public String getContentType() {
            return "application/octet-stream";
        }
    }
}
