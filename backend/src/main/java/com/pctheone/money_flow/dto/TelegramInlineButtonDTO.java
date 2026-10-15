package com.pctheone.money_flow.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

//---- callbackData must follow "<prefix>:<payload>" so it can be dispatched by TelegramCallbackRegistry (max 64 bytes).
@Getter
@AllArgsConstructor
public class TelegramInlineButtonDTO {
    private String text;
    private String callbackData;
}
