package org.flossware.jclassloader.cache;

import java.io.IOException;

public interface ClassCache {
    byte[] get(String className);

    void put(String className, byte[] classData) throws IOException;

    boolean contains(String className);

    void clear() throws IOException;

    void remove(String className) throws IOException;
}
