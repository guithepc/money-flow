package com.pctheone.money_flow.services.telegram;

import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

//---- Handles inline keyboard clicks whose callback_data is "<prefix>:<payload>".
//---- Declare an implementation as a Spring bean and it is picked up by TelegramCallbackRegistry.
public interface TelegramCallbackHandler {

    //---- e.g. "onb" for callback_data "onb:currency:BRL". Must not contain ':'.
    String prefix();

    //---- Returns an optional short text shown to the user by answerCallbackQuery (null for none).
    String handle(Context context);

    record Context(CallbackQuery callbackQuery, Long chatId, Integer ownerId, String payload) {
    }
}
