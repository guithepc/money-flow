package com.pctheone.money_flow.controllers;

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

    @Autowired
    TelegramBotServices telegramBotServices;

    @Value("${telegram.webhook-secret}")
    String telegramSecret;

    @PostMapping("/telegram-webhook")
    public ResponseEntity<Void> expenseFromTelegram(@RequestBody Update update, @RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token") String xTelegramBotApiSecretToken){
        if (!xTelegramBotApiSecretToken.equals(telegramSecret)){
            log.info("Forbidden message.");
            return ResponseEntity.ok().build();
        }

        telegramBotServices.telegramRouter(update);
        return ResponseEntity.ok().build();
    }
}
