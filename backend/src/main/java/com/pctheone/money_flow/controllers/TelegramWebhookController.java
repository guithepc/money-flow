package com.pctheone.money_flow.controllers;

import com.pctheone.money_flow.services.TelegramBotServices;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.telegram.telegrambots.meta.api.objects.Update;


@RestController
@RequestMapping("/api/integration")
public class TelegramWebhookController {

    @Autowired
    TelegramBotServices telegramBotServices;

    @PostMapping("/telegram-webhook")
    public void expenseFromTelegram(@RequestBody Update update){

        telegramBotServices.telegramRouter(update);
    }
}
