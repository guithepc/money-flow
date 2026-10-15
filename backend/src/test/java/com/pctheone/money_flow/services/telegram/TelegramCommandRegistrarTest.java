package com.pctheone.money_flow.services.telegram;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

public class TelegramCommandRegistrarTest {

    @Test
    void registrationFailureOnlyLogs() {
        TelegramApiClient telegramApiClient = Mockito.mock(TelegramApiClient.class);
        Mockito.when(telegramApiClient.setMyCommands(ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenThrow(new RuntimeException("telegram down"));
        TelegramCommandRegistrar registrar = new TelegramCommandRegistrar();
        registrar.telegramApiClient = telegramApiClient;

        Assertions.assertDoesNotThrow(registrar::registerCommands);
    }
}
