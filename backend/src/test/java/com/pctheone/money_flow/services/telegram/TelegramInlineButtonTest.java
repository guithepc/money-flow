package com.pctheone.money_flow.services.telegram;

import com.pctheone.money_flow.dto.TelegramInlineButtonDTO;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

public class TelegramInlineButtonTest {

    @Test
    void callbackButtonSerializesWithoutUrl() {
        Map<String, String> json = TelegramApiClient.toButtonJson(TelegramInlineButtonDTO.callback("BRL", "onb:currency:BRL"));

        Assertions.assertEquals(Map.of("text", "BRL", "callback_data", "onb:currency:BRL"), json);
    }

    @Test
    void urlButtonSerializesWithoutCallbackData() {
        Map<String, String> json = TelegramApiClient.toButtonJson(TelegramInlineButtonDTO.url("Dashboard", "https://moneyflow.example"));

        Assertions.assertEquals(Map.of("text", "Dashboard", "url", "https://moneyflow.example"), json);
    }

    @Test
    void callbackDataUpTo64BytesIsAccepted() {
        Assertions.assertDoesNotThrow(() -> TelegramInlineButtonDTO.callback("ok", "a".repeat(64)));
    }

    @Test
    void callbackDataOver64BytesFails() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> TelegramInlineButtonDTO.callback("too long", "a".repeat(65)));
        //---- 33 two-byte characters = 66 bytes, even though it is only 33 chars.
        Assertions.assertThrows(IllegalArgumentException.class, () -> TelegramInlineButtonDTO.callback("too long", "é".repeat(33)));
    }

    @Test
    void missingFieldsFail() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> TelegramInlineButtonDTO.callback(null, "x:y"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> TelegramInlineButtonDTO.callback("text", null));
        Assertions.assertThrows(IllegalArgumentException.class, () -> TelegramInlineButtonDTO.url("text", " "));
    }
}
