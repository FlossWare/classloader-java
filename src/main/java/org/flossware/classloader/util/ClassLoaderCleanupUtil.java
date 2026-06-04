package org.flossware.classloader.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Utility class for cleaning up ClassLoader resources to prevent memory leaks.
 *
 * <p>ClassLoader leaks are a common problem in Java platforms that load and unload
 * applications dynamically. This utility provides comprehensive cleanup for common
 * leak sources:</p>
 * <ul>
 *   <li>ThreadLocal variables</li>
 *   <li>JDBC drivers registered with DriverManager</li>
 *   <li>JMX MBeans</li>
 *   <li>Shutdown hooks</li>
 *   <li>Resource bundle caches</li>
 * </ul>
 *
 * <p>Usage:</p>
 * <pre>
 * ClassLoaderCleanupUtil cleanup = new ClassLoaderCleanupUtil("my-app", classLoader);
 * cleanup.cleanupAll();
 * cleanup.detectLeaks(); // Verify cleanup succeeded
 * </pre>
 *
 * @since 2.0
 */
public class ClassLoaderCleanupUtil {

    private static final Logger logger = LoggerFactory.getLogger(ClassLoaderCleanupUtil.class);

    private final String applicationId;
    private final ClassLoader classLoader;
    private final WeakReference<ClassLoader> leakDetector;

    /**
     * Creates a new cleanup utility for the specified ClassLoader.
     *
     * @param applicationId the application identifier for logging
     * @param classLoader the ClassLoader to clean up
     */
    public ClassLoaderCleanupUtil(String applicationId, ClassLoader classLoader) {
        this.applicationId = applicationId;
        this.classLoader = classLoader;
        this.leakDetector = new WeakReference<>(classLoader);
    }

    /**
     * Performs all cleanup operations.
     *
     * <p>This is the main entry point for ClassLoader cleanup. It calls all
     * individual cleanup methods in the correct order.</p>
     */
    public void cleanupAll() {
        logger.info("[{}] Starting ClassLoader cleanup", applicationId);

        cleanupThreadLocals();
        cleanupJdbcDrivers();
        cleanupMBeans();
        cleanupShutdownHooks();
        cleanupResourceBundles();

        logger.info("[{}] ClassLoader cleanup completed", applicationId);
    }

    /**
     * Removes ThreadLocal variables from application threads.
     *
     * <p>ThreadLocals are a common source of ClassLoader leaks. This method
     * clears ThreadLocals from all threads whose names contain the application ID.</p>
     */
    public void cleanupThreadLocals() {
        try {
            int cleaned = 0;
            Map<Thread, StackTraceElement[]> threads = Thread.getAllStackTraces();

            for (Thread thread : threads.keySet()) {
                if (thread.getName().contains(applicationId)) {
                    if (clearThreadLocals(thread)) {
                        cleaned++;
                    }
                }
            }

            logger.info("[{}] Cleaned ThreadLocals from {} threads", applicationId, cleaned);
        } catch (SecurityException e) {
            logger.warn("[{}] Failed to clean ThreadLocals (security): {}", applicationId, e.getMessage());
        }
    }

    /**
     * Clears ThreadLocals from a specific thread using reflection.
     *
     * @param thread the thread to clean
     * @return true if cleanup succeeded
     */
    private boolean clearThreadLocals(Thread thread) {
        try {
            // Access Thread.threadLocals field
            Field threadLocalsField = Thread.class.getDeclaredField("threadLocals");
            threadLocalsField.setAccessible(true);
            Object threadLocalMap = threadLocalsField.get(thread);

            if (threadLocalMap != null) {
                // Access ThreadLocalMap.table field
                Class<?> threadLocalMapClass = threadLocalMap.getClass();
                Field tableField = threadLocalMapClass.getDeclaredField("table");
                tableField.setAccessible(true);
                Object[] table = (Object[]) tableField.get(threadLocalMap);

                if (table != null) {
                    // Clear entries loaded by our ClassLoader
                    for (int i = 0; i < table.length; i++) {
                        Object entry = table[i];
                        if (entry != null) {
                            // Check if entry's value was loaded by our ClassLoader
                            Field valueField = entry.getClass().getDeclaredField("value");
                            valueField.setAccessible(true);
                            Object value = valueField.get(entry);

                            if (value != null && value.getClass().getClassLoader() == classLoader) {
                                table[i] = null; // Clear the entry
                            }
                        }
                    }
                }
            }

            // Also clean inheritableThreadLocals
            Field inheritableThreadLocalsField = Thread.class.getDeclaredField("inheritableThreadLocals");
            inheritableThreadLocalsField.setAccessible(true);
            inheritableThreadLocalsField.set(thread, null);

            return true;
        } catch (NoSuchFieldException | SecurityException | IllegalAccessException e) {
            logger.debug("[{}] Could not clear ThreadLocals for thread {}: {}",
                    applicationId, thread.getName(), e.getMessage());
            return false;
        }
    }

    /**
     * Deregisters JDBC drivers loaded by this ClassLoader.
     *
     * <p>JDBC drivers registered with DriverManager create a permanent reference
     * to the ClassLoader that loaded them. This method deregisters all drivers
     * loaded by our ClassLoader.</p>
     */
    public void cleanupJdbcDrivers() {
        try {
            List<Driver> driversToDeregister = new ArrayList<>();
            Enumeration<Driver> drivers = DriverManager.getDrivers();

            while (drivers.hasMoreElements()) {
                Driver driver = drivers.nextElement();
                if (driver.getClass().getClassLoader() == classLoader) {
                    driversToDeregister.add(driver);
                }
            }

            for (Driver driver : driversToDeregister) {
                DriverManager.deregisterDriver(driver);
                logger.info("[{}] Deregistered JDBC driver: {}", applicationId, driver.getClass().getName());
            }

            if (!driversToDeregister.isEmpty()) {
                logger.info("[{}] Deregistered {} JDBC drivers", applicationId, driversToDeregister.size());
            }
        } catch (SQLException e) {
            logger.warn("[{}] Failed to cleanup JDBC drivers: {}", applicationId, e.getMessage());
        }
    }

    /**
     * Unregisters JMX MBeans registered by this ClassLoader.
     *
     * <p>MBeans registered by applications can hold references to the ClassLoader.
     * This method unregisters all MBeans whose ObjectName contains the application ID.</p>
     */
    public void cleanupMBeans() {
        try {
            MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
            Set<ObjectName> allMBeans = mbs.queryNames(null, null);
            int unregistered = 0;

            for (ObjectName name : allMBeans) {
                // Unregister MBeans that match our application ID
                if (name.toString().contains(applicationId)) {
                    try {
                        mbs.unregisterMBean(name);
                        unregistered++;
                        logger.debug("[{}] Unregistered MBean: {}", applicationId, name);
                    } catch (javax.management.InstanceNotFoundException | javax.management.MBeanRegistrationException e) {
                        logger.warn("[{}] Failed to unregister MBean {}: {}",
                                applicationId, name, e.getMessage());
                    }
                }
            }

            if (unregistered > 0) {
                logger.info("[{}] Unregistered {} MBeans", applicationId, unregistered);
            }
        } catch (javax.management.MalformedObjectNameException | javax.management.ReflectionException e) {
            logger.warn("[{}] Failed to cleanup MBeans: {}", applicationId, e.getMessage());
        }
    }

    /**
     * Removes shutdown hooks registered by application threads.
     *
     * <p>Applications may register shutdown hooks that hold references to the
     * ClassLoader. This method attempts to remove hooks for threads whose names
     * contain the application ID.</p>
     */
    public void cleanupShutdownHooks() {
        try {
            // Access Runtime.shutdownHooks field
            Field hooksField = Runtime.class.getDeclaredField("shutdownHooks");
            hooksField.setAccessible(true);
            Map<Thread, Thread> hooks = (Map<Thread, Thread>) hooksField.get(Runtime.getRuntime());

            List<Thread> toRemove = new ArrayList<>();
            for (Thread hook : hooks.keySet()) {
                if (hook.getName().contains(applicationId)) {
                    toRemove.add(hook);
                }
            }

            for (Thread hook : toRemove) {
                Runtime.getRuntime().removeShutdownHook(hook);
                logger.debug("[{}] Removed shutdown hook: {}", applicationId, hook.getName());
            }

            if (!toRemove.isEmpty()) {
                logger.info("[{}] Removed {} shutdown hooks", applicationId, toRemove.size());
            }
        } catch (NoSuchFieldException | SecurityException | IllegalAccessException e) {
            logger.warn("[{}] Failed to cleanup shutdown hooks: {}", applicationId, e.getMessage());
        }
    }

    /**
     * Clears ResourceBundle caches that may hold ClassLoader references.
     *
     * <p>ResourceBundles are cached by ClassLoader, which can prevent garbage
     * collection. This method clears the cache using reflection.</p>
     */
    public void cleanupResourceBundles() {
        try {
            Class<?> bundleClass = Class.forName("java.util.ResourceBundle");
            Field cacheListField = bundleClass.getDeclaredField("cacheList");
            cacheListField.setAccessible(true);
            Object cacheList = cacheListField.get(null);

            if (cacheList != null) {
                synchronized (cacheList) {
                    // Clear the cache (implementation varies by JDK version)
                    if (cacheList instanceof Map) {
                        ((Map<?, ?>) cacheList).clear();
                        logger.info("[{}] Cleared ResourceBundle cache", applicationId);
                    }
                }
            }
        } catch (ClassNotFoundException | NoSuchFieldException | SecurityException | IllegalAccessException e) {
            // ResourceBundle implementation details vary by JDK version
            logger.debug("[{}] Could not clear ResourceBundle cache: {}", applicationId, e.getMessage());
        }
    }

    /**
     * Detects if the ClassLoader has been garbage collected.
     *
     * <p>This method should be called after {@link #cleanupAll()} to verify
     * that the ClassLoader is eligible for garbage collection. It triggers
     * a GC and checks if the WeakReference has been cleared.</p>
     *
     * @return true if the ClassLoader has been garbage collected, false if a leak is detected
     */
    public boolean detectLeaks() {
        logger.info("[{}] Running leak detection...", applicationId);

        // Suggest garbage collection
        System.gc();
        System.runFinalization();
        System.gc();

        // Small delay to allow GC to run
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Check if ClassLoader was garbage collected
        if (leakDetector.get() != null) {
            logger.warn("[{}] CLASSLOADER LEAK DETECTED - ClassLoader was not garbage collected", applicationId);
            logLeakDiagnostics();
            return false;
        } else {
            logger.info("[{}] No ClassLoader leak detected", applicationId);
            return true;
        }
    }

    /**
     * Logs diagnostic information to help identify leak sources.
     */
    private void logLeakDiagnostics() {
        logger.warn("[{}] Leak diagnostics:", applicationId);
        logger.warn("[{}] - Check for static fields holding application objects", applicationId);
        logger.warn("[{}] - Check for threads still running with application classes", applicationId);
        logger.warn("[{}] - Check for external caches holding application objects", applicationId);
        logger.warn("[{}] - Use a heap profiler (VisualVM, JProfiler) to find reference chains", applicationId);

        // Log threads that might hold references
        Map<Thread, StackTraceElement[]> threads = Thread.getAllStackTraces();
        for (Map.Entry<Thread, StackTraceElement[]> entry : threads.entrySet()) {
            Thread thread = entry.getKey();
            if (thread.getName().contains(applicationId) && thread.isAlive()) {
                logger.warn("[{}] - Active thread: {} (state: {})", applicationId, thread.getName(), thread.getState());
            }
        }
    }

    /**
     * Returns the application ID.
     *
     * @return the application ID
     */
    public String getApplicationId() {
        return applicationId;
    }
}
