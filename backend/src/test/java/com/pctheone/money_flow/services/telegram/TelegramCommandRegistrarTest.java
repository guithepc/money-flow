package com.pctheone.money_flow.services.telegram;

import com.pctheone.money_flow.dto.TelegramBotCommandDTO;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.util.List;

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

    @Test
    void registersResetPasswordWithTheLanguageName() {
        TelegramApiClient telegramApiClient = Mockito.mock(TelegramApiClient.class);
        TelegramCommandRegistrar registrar = new TelegramCommandRegistrar();
        registrar.telegramApiClient = telegramApiClient;

        registrar.registerCommands();

        Mockito.verify(telegramApiClient).setMyCommands(ArgumentMatchers.argThat(commands -> hasCommand(commands, "redefinir_senha") && !hasCommand(commands, "reset_password")), ArgumentMatchers.isNull());
        Mockito.verify(telegramApiClient).setMyCommands(ArgumentMatchers.argThat(commands -> hasCommand(commands, "reset_password") && !hasCommand(commands, "redefinir_senha")), ArgumentMatchers.eq("en"));
    }

    private static boolean hasCommand(List<TelegramBotCommandDTO> commands, String name) {
        return commands.stream().anyMatch(command -> name.equals(command.getCommand()));
    }
}
