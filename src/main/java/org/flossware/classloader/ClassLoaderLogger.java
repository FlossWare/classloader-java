package org.flossware.classloader;

/**
 * Centralized logging for ApplicationClassLoader.
 * Uses SLF4J if available, falls back to System.err.
 */
class ClassLoaderLogger {
    private ClassLoaderLogger() {
        // Utility class
    }

    /**
     * Logs an error message. Uses SLF4J if available, otherwise falls back to System.err.
     */
    static void logError(String message) {
        try {
            // Try SLF4J if available (optional dependency)
            Class<?> loggerFactoryClass = Class.forName("org.slf4j.LoggerFactory");
            Object logger = loggerFactoryClass.getMethod("getLogger", Class.class)
                .invoke(null, ApplicationClassLoader.class);
            logger.getClass().getMethod("error", String.class).invoke(logger, message);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException |
                 java.lang.reflect.InvocationTargetException e) {
            // SLF4J not available or error occurred, fall back to System.err
            System.err.println("[ApplicationClassLoader ERROR] " + message);
        }
    }
}
