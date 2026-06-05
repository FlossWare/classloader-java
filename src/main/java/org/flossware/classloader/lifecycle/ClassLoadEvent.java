package org.flossware.classloader.lifecycle;

import org.flossware.classloader.ClassSource;
import java.util.Objects;

/**
 * Event containing details about a loaded class.
 */
public class ClassLoadEvent {
    private final String className;
    private final ClassSource source;
    private final long loadTimeNanos;
    private final int classSizeBytes;
    private final long timestamp;

    /**
     * Creates a new class load event with the specified details.
     *
     * @param className The fully qualified class name that was loaded
     * @param source The ClassSource that provided the class bytecode
     * @param loadTimeNanos The time taken to load the class in nanoseconds
     * @param classSizeBytes The size of the loaded class bytecode in bytes
     * @throws NullPointerException if className or source is null
     */
    public ClassLoadEvent(String className, ClassSource source, long loadTimeNanos, int classSizeBytes) {
        this.className = Objects.requireNonNull(className, "className cannot be null");
        this.source = Objects.requireNonNull(source, "source cannot be null");
        this.loadTimeNanos = loadTimeNanos;
        this.classSizeBytes = classSizeBytes;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * Gets the fully qualified name of the loaded class.
     *
     * @return the class name
     */
    public String getClassName() {
        return className;
    }

    /**
     * Gets the ClassSource that provided the class bytecode.
     *
     * @return the source that loaded the class
     */
    public ClassSource getSource() {
        return source;
    }

    /**
     * Gets the time taken to load the class in nanoseconds.
     *
     * @return the load time in nanoseconds
     */
    public long getLoadTimeNanos() {
        return loadTimeNanos;
    }

    /**
     * Gets the time taken to load the class in milliseconds.
     *
     * @return the load time in milliseconds
     */
    public long getLoadTimeMillis() {
        return loadTimeNanos / 1_000_000;
    }

    /**
     * Gets the size of the loaded class bytecode in bytes.
     *
     * @return the class size in bytes
     */
    public int getClassSizeBytes() {
        return classSizeBytes;
    }

    /**
     * Gets the timestamp when the class was loaded, in milliseconds since epoch.
     *
     * @return the load timestamp
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Returns a human-readable string representation of this event
     * including class name, source description, load time, and size.
     *
     * @return a formatted string describing this event
     */
    @Override
    public String toString() {
        return String.format("ClassLoadEvent{class=%s, source=%s, time=%dms, size=%dB}",
                className, source.getDescription(), getLoadTimeMillis(), classSizeBytes);
    }
}
