package org.flossware.classloader.lifecycle;

import org.flossware.classloader.ClassSource;

/**
 * Event containing details about a loaded class.
 */
public class ClassLoadEvent {
    private final String className;
    private final ClassSource source;
    private final long loadTimeNanos;
    private final int classSizeBytes;
    private final long timestamp;

    public ClassLoadEvent(String className, ClassSource source, long loadTimeNanos, int classSizeBytes) {
        this.className = className;
        this.source = source;
        this.loadTimeNanos = loadTimeNanos;
        this.classSizeBytes = classSizeBytes;
        this.timestamp = System.currentTimeMillis();
    }

    public String getClassName() {
        return className;
    }

    public ClassSource getSource() {
        return source;
    }

    public long getLoadTimeNanos() {
        return loadTimeNanos;
    }

    public long getLoadTimeMillis() {
        return loadTimeNanos / 1_000_000;
    }

    public int getClassSizeBytes() {
        return classSizeBytes;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return String.format("ClassLoadEvent{class=%s, source=%s, time=%dms, size=%dB}",
                className, source.getDescription(), getLoadTimeMillis(), classSizeBytes);
    }
}
