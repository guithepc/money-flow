package com.pctheone.money_flow.services.telegram;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

//---- Maps callback_data prefixes to handlers. "onb:currency:BRL" -> prefix "onb", payload "currency:BRL".
@Component
public class TelegramCallbackRegistry {

    static final String SEPARATOR = ":";

    private final Map<String, TelegramCallbackHandler> handlers = new ConcurrentHashMap<>();

    public TelegramCallbackRegistry(ObjectProvider<TelegramCallbackHandler> handlerBeans) {
        handlerBeans.orderedStream().forEach(this::register);
    }

    public void register(TelegramCallbackHandler handler) {
        String prefix = handler.prefix();
        if (prefix == null || prefix.isBlank() || prefix.contains(SEPARATOR))
            throw new IllegalArgumentException("Invalid callback prefix: " + prefix);
        TelegramCallbackHandler previous = handlers.putIfAbsent(prefix, handler);
        if (previous != null)
            throw new IllegalStateException("Callback prefix already registered: " + prefix);
    }

    public Optional<TelegramCallbackHandler> find(String callbackData) {
        if (callbackData == null)
            return Optional.empty();
        return Optional.ofNullable(handlers.get(prefixOf(callbackData)));
    }

    public static String prefixOf(String callbackData) {
        int index = callbackData.indexOf(SEPARATOR);
        return index < 0 ? callbackData : callbackData.substring(0, index);
    }

    public static String payloadOf(String callbackData) {
        int index = callbackData.indexOf(SEPARATOR);
        return index < 0 ? "" : callbackData.substring(index + 1);
    }

    public static String callbackData(String prefix, String payload) {
        return prefix + SEPARATOR + payload;
    }
}
