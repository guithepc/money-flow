package com.pctheone.money_flow.controllers;

import com.pctheone.money_flow.services.TelegramBotServices;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.telegram.telegrambots.meta.api.objects.Update;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class TelegramWebhookControllerTest {

    private static final String URL = "/api/integration/telegram-webhook";
    private static final String SECRET_HEADER = "X-Telegram-Bot-Api-Secret-Token";
    private static final String MESSAGE_UPDATE = """
            {"update_id": 1, "message": {"message_id": 1, "date": 0, "chat": {"id": 42, "type": "private"}, "text": "/contas"}}
            """;

    TelegramBotServices telegramBotServices;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        telegramBotServices = Mockito.mock(TelegramBotServices.class);
        TelegramWebhookController controller = new TelegramWebhookController();
        controller.telegramBotServices = telegramBotServices;
        controller.telegramSecret = "secret";
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void missingSecretHeaderReturns200WithoutProcessing() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(MESSAGE_UPDATE))
                .andExpect(status().isOk());

        Mockito.verifyNoInteractions(telegramBotServices);
    }

    @Test
    void wrongSecretReturns200WithoutProcessing() throws Exception {
        mockMvc.perform(post(URL).header(SECRET_HEADER, "wrong").contentType(MediaType.APPLICATION_JSON).content(MESSAGE_UPDATE))
                .andExpect(status().isOk());

        Mockito.verifyNoInteractions(telegramBotServices);
    }

    @Test
    void routerFailureStillReturns200() throws Exception {
        Mockito.doThrow(new RuntimeException("boom")).when(telegramBotServices).telegramRouter(ArgumentMatchers.any());

        mockMvc.perform(post(URL).header(SECRET_HEADER, "secret").contentType(MediaType.APPLICATION_JSON).content(MESSAGE_UPDATE))
                .andExpect(status().isOk());
    }

    @Test
    void malformedBodyReturns200() throws Exception {
        mockMvc.perform(post(URL).header(SECRET_HEADER, "secret").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isOk());

        Mockito.verifyNoInteractions(telegramBotServices);
    }

    @Test
    void emptyBodyReturns200() throws Exception {
        mockMvc.perform(post(URL).header(SECRET_HEADER, "secret").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        mockMvc.perform(post(URL).header(SECRET_HEADER, "secret").contentType(MediaType.APPLICATION_JSON).content("   "))
                .andExpect(status().isOk());

        Mockito.verifyNoInteractions(telegramBotServices);
    }

    @Test
    void callbackQueryUpdateIsParsedAndRouted() throws Exception {
        String callbackUpdate = """
                {"update_id": 2, "callback_query": {"id": "cb", "chat_instance": "ci", "data": "onb:x",
                  "from": {"id": 42, "is_bot": false, "first_name": "G"},
                  "message": {"message_id": 10, "date": 1, "chat": {"id": 42, "type": "private"}, "text": "pick"}}}
                """;

        mockMvc.perform(post(URL).header(SECRET_HEADER, "secret").contentType(MediaType.APPLICATION_JSON).content(callbackUpdate))
                .andExpect(status().isOk());

        ArgumentCaptor<Update> captor = ArgumentCaptor.forClass(Update.class);
        Mockito.verify(telegramBotServices).telegramRouter(captor.capture());
        Update update = captor.getValue();
        Assertions.assertEquals(2, update.getUpdateId());
        Assertions.assertTrue(update.hasCallbackQuery());
        Assertions.assertEquals("onb:x", update.getCallbackQuery().getData());
        Assertions.assertEquals(42L, update.getCallbackQuery().getMessage().getChatId());
    }
}
