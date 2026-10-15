package com.pctheone.money_flow.controllers;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pctheone.money_flow.services.TelegramBotServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.telegram.telegrambots.meta.api.objects.Update;


@RestController
@RequestMapping("/api/integration")
public class TelegramWebhookController {

    private static final Logger log = LoggerFactory.getLogger(TelegramWebhookController.class);

    //---- telegrambots-meta objects carry Jackson 2 annotations (e.g. the callback_query message deserializer).
    private static final ObjectMapper UPDATE_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Autowired
    TelegramBotServices telegramBotServices;

    @Value("${telegram.webhook-secret}")
    String telegramSecret;

    //---- Always answers 200: any other status makes Telegram retry the same update.
    @PostMapping("/telegram-webhook")
    public ResponseEntity<Void> expenseFromTelegram(@RequestBody(required = false) String body, @RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false) String xTelegramBotApiSecretToken){
        if (xTelegramBotApiSecretToken == null || !xTelegramBotApiSecretToken.equals(telegramSecret)){
            log.info("Forbidden message.");
            return ResponseEntity.ok().build();
        }

        if (body == null || body.isBlank()) {
            log.warn("Empty Telegram webhook body ignored.");
            return ResponseEntity.ok().build();
        }

        try {
            Update update = UPDATE_MAPPER.readValue(body, Update.class);
            telegramBotServices.telegramRouter(update);
        } catch (Exception e) {
            log.error("Failed to process Telegram update: {}", e.getMessage(), e);
        }
        return ResponseEntity.ok().build();
    }
}
