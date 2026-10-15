package com.pctheone.money_flow.services.telegram;

import com.pctheone.money_flow.dto.TelegramInlineButtonDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

//---- Replies sent from inside a transaction go out only after it commits (dropped on rollback), in the order they were queued.
//---- Outside a transaction they are sent right away. A failed send is logged and never affects the database.
@Component
public class TelegramReplySender {

    private static final Logger log = LoggerFactory.getLogger(TelegramReplySender.class);

    private final TelegramApiClient telegramApiClient;

    public TelegramReplySender(TelegramApiClient telegramApiClient) {
        this.telegramApiClient = telegramApiClient;
    }

    public void send(Long chatId, String text) {
        dispatch(() -> telegramApiClient.sendMessage(chatId, text));
    }

    public void sendWithKeyboard(Long chatId, String text, List<List<TelegramInlineButtonDTO>> keyboard) {
        dispatch(() -> telegramApiClient.sendMessageWithInlineKeyboard(chatId, text, keyboard));
    }

    private void dispatch(Runnable send) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendSafely(send);
                }
            });
            return;
        }
        sendSafely(send);
    }

    private void sendSafely(Runnable send) {
        try {
            send.run();
        } catch (Exception e) {
            log.error("Failed to send Telegram reply: {}", e.getMessage(), e);
        }
    }
}
