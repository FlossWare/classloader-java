package org.flossware.jclassloader.protocol;

import java.io.IOException;

public interface ProtocolHandler {
    byte[] fetchClass(String className) throws IOException;

    boolean canHandle(String className);

    String getProtocolName();

    void close() throws IOException;
}
