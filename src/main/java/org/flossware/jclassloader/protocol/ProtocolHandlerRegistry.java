package org.flossware.jclassloader.protocol;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class ProtocolHandlerRegistry {
    private static final ProtocolHandlerRegistry INSTANCE = new ProtocolHandlerRegistry();
    private final Map<String, Class<? extends ProtocolHandler>> handlers = new ConcurrentHashMap<>();

    private ProtocolHandlerRegistry() {
    }

    public static ProtocolHandlerRegistry getInstance() {
        return INSTANCE;
    }

    public void register(String protocol, Class<? extends ProtocolHandler> handlerClass) {
        Objects.requireNonNull(protocol, "protocol cannot be null");
        Objects.requireNonNull(handlerClass, "handlerClass cannot be null");
        handlers.put(protocol.toLowerCase(), handlerClass);
    }

    public void unregister(String protocol) {
        handlers.remove(protocol.toLowerCase());
    }

    public Class<? extends ProtocolHandler> getHandler(String protocol) {
        return handlers.get(protocol.toLowerCase());
    }

    public boolean isRegistered(String protocol) {
        return handlers.containsKey(protocol.toLowerCase());
    }

    public Map<String, Class<? extends ProtocolHandler>> getAllHandlers() {
        return new ConcurrentHashMap<>(handlers);
    }
}
