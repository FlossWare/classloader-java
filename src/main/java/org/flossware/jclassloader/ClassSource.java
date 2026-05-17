package org.flossware.jclassloader;

import java.io.IOException;

public interface ClassSource {
    byte[] loadClassData(String className) throws IOException;

    boolean canLoad(String className);

    String getDescription();
}
