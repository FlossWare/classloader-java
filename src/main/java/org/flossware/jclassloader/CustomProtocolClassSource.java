package org.flossware.jclassloader;

import org.flossware.jclassloader.protocol.ProtocolHandler;

import java.io.IOException;
import java.util.Objects;

public class CustomProtocolClassSource implements ClassSource {
    private final ProtocolHandler handler;

    public CustomProtocolClassSource(ProtocolHandler handler) {
        this.handler = Objects.requireNonNull(handler, "handler cannot be null");
    }

    @Override
    public byte[] loadClassData(String className) throws IOException {
        return handler.fetchClass(className);
    }

    @Override
    public boolean canLoad(String className) {
        return handler.canHandle(className);
    }

    @Override
    public String getDescription() {
        return "CustomProtocolClassSource[protocol=" + handler.getProtocolName() + "]";
    }

    public ProtocolHandler getHandler() {
        return handler;
    }

    public void close() throws IOException {
        handler.close();
    }
}
