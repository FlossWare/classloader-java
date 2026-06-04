package org.flossware.classloader;

import org.flossware.classloader.lifecycle.ClassLoadEvent;
import org.flossware.classloader.lifecycle.ClassLoaderLifecycleListener;

import java.util.List;

/**
 * Dispatches lifecycle events to all registered listeners.
 *
 * Responsibilities:
 * - Firing class loaded events
 * - Firing cache hit/miss events
 * - Firing load failure events
 * - Error handling for listener exceptions
 */
class ClassLoaderEventDispatcher {
    private final List<ClassLoaderLifecycleListener> listeners;

    ClassLoaderEventDispatcher(List<ClassLoaderLifecycleListener> listeners) {
        this.listeners = listeners;
    }

    void fireClassLoaded(ClassLoadEvent event) {
        for (ClassLoaderLifecycleListener listener : listeners) {
            try {
                listener.onClassLoaded(event);
            } catch (RuntimeException e) {
                ClassLoaderLogger.logError("Listener error in " + listener.getClass().getSimpleName() +
                        ".onClassLoaded: " + e.getMessage());
            }
        }
    }

    void fireClassCacheHit(String className) {
        for (ClassLoaderLifecycleListener listener : listeners) {
            try {
                listener.onClassCacheHit(className);
            } catch (RuntimeException e) {
                ClassLoaderLogger.logError("Listener error in " + listener.getClass().getSimpleName() +
                        ".onClassCacheHit: " + e.getMessage());
            }
        }
    }

    void fireClassCached(String className, byte[] classData) {
        for (ClassLoaderLifecycleListener listener : listeners) {
            try {
                listener.onClassCached(className, classData);
            } catch (RuntimeException e) {
                ClassLoaderLogger.logError("Listener error in " + listener.getClass().getSimpleName() +
                        ".onClassCached: " + e.getMessage());
            }
        }
    }

    void fireClassLoadFailed(String className, Throwable error) {
        for (ClassLoaderLifecycleListener listener : listeners) {
            try {
                listener.onClassLoadFailed(className, error);
            } catch (RuntimeException e) {
                ClassLoaderLogger.logError("Listener error in " + listener.getClass().getSimpleName() +
                        ".onClassLoadFailed: " + e.getMessage());
            }
        }
    }

    void fireClassCacheFailed(String className, Throwable error) {
        for (ClassLoaderLifecycleListener listener : listeners) {
            try {
                listener.onClassCacheFailed(className, error);
            } catch (RuntimeException e) {
                ClassLoaderLogger.logError("Listener error in " + listener.getClass().getSimpleName() +
                        ".onClassCacheFailed: " + e.getMessage());
            }
        }
    }

    void fireClassLoaderClosed() {
        for (ClassLoaderLifecycleListener listener : listeners) {
            try {
                listener.onClassLoaderClosed();
            } catch (RuntimeException e) {
                ClassLoaderLogger.logError("Listener error in " + listener.getClass().getSimpleName() +
                        ".onClassLoaderClosed: " + e.getMessage());
            }
        }
    }
}
