package com.pctheone.money_flow.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.nio.charset.StandardCharsets;

//---- An inline keyboard button: either a callback button (routed by TelegramCallbackRegistry) or a URL button.
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class TelegramInlineButtonDTO {

    //---- Telegram limit for callback_data.
    public static final int MAX_CALLBACK_DATA_BYTES = 64;

    private String text;
    private String callbackData;
    private String url;

    //---- callbackData must follow "<prefix>:<payload>" so it can be dispatched by TelegramCallbackRegistry.
    public static TelegramInlineButtonDTO callback(String text, String callbackData) {
        requireText(text);
        if (callbackData == null || callbackData.isEmpty())
            throw new IllegalArgumentException("Inline button callback_data is required");
        int bytes = callbackData.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > MAX_CALLBACK_DATA_BYTES)
            throw new IllegalArgumentException("Inline button callback_data is " + bytes + " bytes, max is " + MAX_CALLBACK_DATA_BYTES + ": " + callbackData);
        return new TelegramInlineButtonDTO(text, callbackData, null);
    }

    public static TelegramInlineButtonDTO url(String text, String url) {
        requireText(text);
        if (url == null || url.isBlank())
            throw new IllegalArgumentException("Inline button url is required");
        return new TelegramInlineButtonDTO(text, null, url);
    }

    private static void requireText(String text) {
        if (text == null || text.isBlank())
            throw new IllegalArgumentException("Inline button text is required");
    }
}
