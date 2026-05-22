package org.flossware.jclassloader;

import org.flossware.jclassloader.protocol.ProtocolHandler;

import java.io.IOException;
import java.util.Objects;

/**
 * ClassSource implementation that delegates to a custom ProtocolHandler.
 * Allows integration of custom protocols and class loading strategies.
 */
public class CustomProtocolClassSource implements ClassSource {
    private final ProtocolHandler handler;

    /**
     * Creates a custom protocol class source with the specified handler.
     *
     * @param handler The protocol handler to delegate class loading to
     * @throws NullPointerException if handler is null
     */
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

    /**
     * Gets the protocol handler used by this class source.
     *
     * @return The protocol handler
     */
    public ProtocolHandler getHandler() {
        return handler;
    }

    /**
     * Closes the protocol handler and releases resources.
     *
     * @throws IOException if an I/O error occurs during closing
     */
    public void close() throws IOException {
        handler.close();
    }
}
