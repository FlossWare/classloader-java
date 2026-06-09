package org.flossware.classloader;

import org.flossware.classloader.lifecycle.ClassLoadEvent;
import org.flossware.classloader.lifecycle.ClassLoaderLifecycleListener;

import java.util.List;
import java.util.Objects;

/**
 * Dispatches lifecycle events to all registered listeners.
 *
 * Responsibilities:
 * - Firing class loaded events
 * - Firing cache hit/miss events
 * - Firing load failure events
 * - Error handling for listener exceptions
 *
 * <p><b>Exception Handling Strategy:</b> All event methods catch specific RuntimeException
 * subtypes that commonly arise from listener callback implementations. Since listeners
 * implement a callback interface outside our control, these catches prevent listener
 * errors from affecting the main class loading flow.</p>
 */
class ClassLoaderEventDispatcher {
    private final List<ClassLoaderLifecycleListener> listeners;

    ClassLoaderEventDispatcher(List<ClassLoaderLifecycleListener> listeners) {
        this.listeners = Objects.requireNonNull(listeners, "listeners cannot be null");
    }

    void fireClassLoaded(ClassLoadEvent event) {
        for (ClassLoaderLifecycleListener listener : listeners) {
            try {
                listener.onClassLoaded(event);
            } catch (NullPointerException | IllegalArgumentException | IllegalStateException
                     | ClassCastException | UnsupportedOperationException e) {
                ClassLoaderLogger.logError("Listener error in " + listener.getClass().getSimpleName() +
                        ".onClassLoaded: " + e.getMessage());
            }
        }
    }

    void fireClassCacheHit(String className) {
        for (ClassLoaderLifecycleListener listener : listeners) {
            try {
                listener.onClassCacheHit(className);
            } catch (NullPointerException | IllegalArgumentException | IllegalStateException
                     | ClassCastException | UnsupportedOperationException e) {
                ClassLoaderLogger.logError("Listener error in " + listener.getClass().getSimpleName() +
                        ".onClassCacheHit: " + e.getMessage());
            }
        }
    }

    void fireClassCached(String className, byte[] classData) {
        for (ClassLoaderLifecycleListener listener : listeners) {
            try {
                listener.onClassCached(className, classData);
            } catch (NullPointerException | IllegalArgumentException | IllegalStateException
                     | ClassCastException | UnsupportedOperationException e) {
                ClassLoaderLogger.logError("Listener error in " + listener.getClass().getSimpleName() +
                        ".onClassCached: " + e.getMessage());
            }
        }
    }

    void fireClassLoadFailed(String className, Throwable error) {
        for (ClassLoaderLifecycleListener listener : listeners) {
            try {
                listener.onClassLoadFailed(className, error);
            } catch (NullPointerException | IllegalArgumentException | IllegalStateException
                     | ClassCastException | UnsupportedOperationException e) {
                ClassLoaderLogger.logError("Listener error in " + listener.getClass().getSimpleName() +
                        ".onClassLoadFailed: " + e.getMessage());
            }
        }
    }

    void fireClassCacheFailed(String className, Throwable error) {
        for (ClassLoaderLifecycleListener listener : listeners) {
            try {
                listener.onClassCacheFailed(className, error);
            } catch (NullPointerException | IllegalArgumentException | IllegalStateException
                     | ClassCastException | UnsupportedOperationException e) {
                ClassLoaderLogger.logError("Listener error in " + listener.getClass().getSimpleName() +
                        ".onClassCacheFailed: " + e.getMessage());
            }
        }
    }

    void fireClassLoaderClosed() {
        for (ClassLoaderLifecycleListener listener : listeners) {
            try {
                listener.onClassLoaderClosed();
            } catch (NullPointerException | IllegalArgumentException | IllegalStateException
                     | ClassCastException | UnsupportedOperationException e) {
                ClassLoaderLogger.logError("Listener error in " + listener.getClass().getSimpleName() +
                        ".onClassLoaderClosed: " + e.getMessage());
            }
        }
    }
}
