package org.flossware.classloader.util;

/**
 * Utility class for class name conversions and manipulations.
 */
public final class ClassNameUtil {

    private ClassNameUtil() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Converts a fully-qualified class name to a class file path.
     *
     * <p>This method transforms a class name in dot notation to its corresponding
     * file path by replacing dots with forward slashes and appending the .class extension.</p>
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>"com.example.MyClass" → "com/example/MyClass.class"</li>
     *   <li>"java.lang.String" → "java/lang/String.class"</li>
     *   <li>"MyClass" → "MyClass.class"</li>
     * </ul>
     *
     * @param className The fully-qualified class name (e.g., "com.example.MyClass")
     * @return The class file path (e.g., "com/example/MyClass.class")
     * @throws NullPointerException if className is null
     */
    public static String toClassFilePath(String className) {
        if (className == null) {
            throw new NullPointerException("className cannot be null");
        }
        return className.replace('.', '/') + ".class";
    }
}
