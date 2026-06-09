package org.flossware.classloader;

import org.flossware.classloader.protocol.ProtocolHandler;

import java.io.IOException;
import java.util.Objects;

/**
 * ClassSource implementation that delegates to a custom ProtocolHandler.
 * Allows integration of custom protocols and class loading strategies.
 */
public class CustomProtocolClassSource implements ClassSource, AutoCloseable {
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

    /** {@inheritDoc} */
    @Override
    public byte[] loadClassData(String className) throws IOException {
        Objects.requireNonNull(className, "className cannot be null");
        return handler.fetchClass(className);
    }

    /** {@inheritDoc} */
    @Override
    public boolean canLoad(String className) {
        Objects.requireNonNull(className, "className cannot be null");
        return handler.canHandle(className);
    }

    /** {@inheritDoc} */
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
    @Override
    public void close() throws IOException {
        handler.close();
    }
}
