package org.flossware.jclassloader.protocol;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for custom protocol handlers.
 * Provides a centralized registry for registering and retrieving protocol handlers.
 * Thread-safe singleton implementation.
 */
public class ProtocolHandlerRegistry {
    private static final ProtocolHandlerRegistry INSTANCE = new ProtocolHandlerRegistry();
    private final Map<String, Class<? extends ProtocolHandler>> handlers = new ConcurrentHashMap<>();

    private ProtocolHandlerRegistry() {
    }

    /**
     * Gets the singleton instance of the registry.
     *
     * @return The registry instance
     */
    public static ProtocolHandlerRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * Registers a protocol handler class for a given protocol.
     *
     * @param protocol The protocol name (case-insensitive)
     * @param handlerClass The handler class to register
     * @throws NullPointerException if protocol or handlerClass is null
     */
    public void register(String protocol, Class<? extends ProtocolHandler> handlerClass) {
        Objects.requireNonNull(protocol, "protocol cannot be null");
        Objects.requireNonNull(handlerClass, "handlerClass cannot be null");
        handlers.put(protocol.toLowerCase(), handlerClass);
    }

    /**
     * Unregisters a protocol handler.
     *
     * @param protocol The protocol name to unregister (case-insensitive)
     */
    public void unregister(String protocol) {
        handlers.remove(protocol.toLowerCase());
    }

    /**
     * Gets the handler class for a protocol.
     *
     * @param protocol The protocol name (case-insensitive)
     * @return The handler class, or null if not registered
     */
    public Class<? extends ProtocolHandler> getHandler(String protocol) {
        return handlers.get(protocol.toLowerCase());
    }

    /**
     * Checks if a protocol is registered.
     *
     * @param protocol The protocol name (case-insensitive)
     * @return true if the protocol is registered, false otherwise
     */
    public boolean isRegistered(String protocol) {
        return handlers.containsKey(protocol.toLowerCase());
    }

    /**
     * Gets all registered protocol handlers.
     *
     * @return A map of protocol names to handler classes
     */
    public Map<String, Class<? extends ProtocolHandler>> getAllHandlers() {
        return new ConcurrentHashMap<>(handlers);
    }
}
