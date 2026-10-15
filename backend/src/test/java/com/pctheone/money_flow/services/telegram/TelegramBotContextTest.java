package com.pctheone.money_flow.services.telegram;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockReset;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.concurrent.ThreadLocalRandom;

//---- Boots the full context: Flyway migrations + Hibernate validate, command registration on startup, idempotency table.
@SpringBootTest(properties = {
        "telegram.bot-token=test-token",
        "telegram.webhook-secret=test-secret",
        "jwt.secret=tmpGFRC21ovxZhKhbd11YOC3Kw8YDyg5KcBb1dlMY0c=",
        "jwt.expiration=3600000"
})
public class TelegramBotContextTest {

    //---- No reset: the startup interactions must survive until the test that checks them.
    @MockitoBean(reset = MockReset.NONE)
    TelegramApiClient telegramApiClient;

    @Autowired
    TelegramProcessedUpdateService processedUpdateService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    long updateId = ThreadLocalRandom.current().nextLong(1_000_000_000L, Long.MAX_VALUE);

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM telegram_processed_update WHERE update_id = ?", updateId);
    }

    @Test
    void commandsAreRegisteredOnStartupInPortugueseAndEnglish() {
        Mockito.verify(telegramApiClient).setMyCommands(ArgumentMatchers.argThat(commands ->
                commands.stream().anyMatch(c -> c.getCommand().equals("ajuda"))), ArgumentMatchers.isNull());
        Mockito.verify(telegramApiClient).setMyCommands(ArgumentMatchers.argThat(commands ->
                commands.stream().anyMatch(c -> c.getCommand().equals("help"))), ArgumentMatchers.eq("en"));
    }

    @Test
    void updateIdIsOnlyProcessedOnce() {
        Assertions.assertTrue(processedUpdateService.markAsProcessed(updateId));
        Assertions.assertFalse(processedUpdateService.markAsProcessed(updateId));
    }

    @Test
    void purgeKeepsRecentUpdates() {
        processedUpdateService.markAsProcessed(updateId);

        processedUpdateService.purgeOldUpdates();

        Assertions.assertFalse(processedUpdateService.markAsProcessed(updateId));
    }

    @Test
    void registeredCommandsAreValidTelegramCommands() {
        for (var command : TelegramBotCommands.portuguese())
            Assertions.assertTrue(command.getCommand().matches("[a-z0-9_]{1,32}"), command.getCommand());
        for (var command : TelegramBotCommands.english())
            Assertions.assertTrue(command.getCommand().matches("[a-z0-9_]{1,32}"), command.getCommand());
    }
}
