package org.flossware.jclassloader.protocol;

import java.io.IOException;

/**
 * Interface for custom protocol handlers.
 * Allows integration of custom class loading protocols and strategies.
 */
public interface ProtocolHandler {
    /**
     * Fetches class bytecode using this protocol handler.
     *
     * @param className The fully qualified class name to fetch
     * @return The class bytecode
     * @throws IOException if the class cannot be fetched
     */
    byte[] fetchClass(String className) throws IOException;

    /**
     * Checks if this handler can handle the specified class.
     *
     * @param className The fully qualified class name
     * @return true if this handler can fetch the class, false otherwise
     */
    boolean canHandle(String className);

    /**
     * Gets the name of this protocol.
     *
     * @return The protocol name (e.g., "custom-http", "ipfs")
     */
    String getProtocolName();

    /**
     * Closes the protocol handler and releases resources.
     *
     * @throws IOException if an I/O error occurs during closing
     */
    void close() throws IOException;
}
