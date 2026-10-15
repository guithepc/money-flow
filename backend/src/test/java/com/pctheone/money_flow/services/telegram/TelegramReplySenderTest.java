package com.pctheone.money_flow.services.telegram;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

import java.util.List;

public class TelegramReplySenderTest {

    private static final long CHAT_ID = 42L;

    TelegramApiClient telegramApiClient = Mockito.mock(TelegramApiClient.class);
    TelegramReplySender sender = new TelegramReplySender(telegramApiClient);

    @AfterEach
    void clear() {
        if (TransactionSynchronizationManager.isSynchronizationActive())
            TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void outsideATransactionRepliesAreSentRightAway() {
        sender.send(CHAT_ID, "hi");

        Mockito.verify(telegramApiClient).sendMessage(CHAT_ID, "hi");
    }

    @Test
    void insideATransactionRepliesWaitForTheCommitAndKeepTheirOrder() {
        TransactionSynchronizationManager.initSynchronization();

        sender.send(CHAT_ID, "first");
        sender.sendWithKeyboard(CHAT_ID, "second", List.of());
        Mockito.verifyNoInteractions(telegramApiClient);

        TransactionSynchronizationUtils.triggerAfterCommit();

        InOrder order = Mockito.inOrder(telegramApiClient);
        order.verify(telegramApiClient).sendMessage(CHAT_ID, "first");
        order.verify(telegramApiClient).sendMessageWithInlineKeyboard(CHAT_ID, "second", List.of());
    }

    @Test
    void rolledBackTransactionSendsNothing() {
        TransactionSynchronizationManager.initSynchronization();

        sender.send(CHAT_ID, "lost");
        TransactionSynchronizationUtils.triggerAfterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

        Mockito.verifyNoInteractions(telegramApiClient);
    }

    @Test
    void failedSendAfterCommitDoesNotThrow() {
        Mockito.doThrow(new IllegalStateException("telegram down")).when(telegramApiClient).sendMessage(CHAT_ID, "boom");
        TransactionSynchronizationManager.initSynchronization();

        sender.send(CHAT_ID, "boom");
        sender.send(CHAT_ID, "next");

        Assertions.assertDoesNotThrow(TransactionSynchronizationUtils::triggerAfterCommit);
        Mockito.verify(telegramApiClient).sendMessage(CHAT_ID, "next");
    }
}
