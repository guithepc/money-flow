package com.pctheone.money_flow.services.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

//---- Publishes the bot command menu on startup. Failures only log: the bot works without the menu.
@Component
public class TelegramCommandRegistrar {

    private static final Logger log = LoggerFactory.getLogger(TelegramCommandRegistrar.class);

    @Autowired
    TelegramApiClient telegramApiClient;

    @EventListener(ApplicationReadyEvent.class)
    public void registerCommands() {
        try {
            boolean ptRegistered = telegramApiClient.setMyCommands(TelegramBotCommands.portuguese(), null);
            boolean enRegistered = telegramApiClient.setMyCommands(TelegramBotCommands.english(), "en");
            log.info("Telegram commands registered - default(pt): {}, en: {}", ptRegistered, enRegistered);
        } catch (Exception e) {
            log.error("Failed to register Telegram commands: {}", e.getMessage(), e);
        }
    }
}
