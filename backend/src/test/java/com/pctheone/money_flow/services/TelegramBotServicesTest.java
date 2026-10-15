package com.pctheone.money_flow.services;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.pctheone.money_flow.dto.AccountDTO;
import com.pctheone.money_flow.entities.OwnerEntity;
import com.pctheone.money_flow.enums.CategoryTypeEnum;
import com.pctheone.money_flow.enums.CurrencyEnum;
import com.pctheone.money_flow.exceptions.CategoryNotFoundException;
import com.pctheone.money_flow.repositories.OwnerRepository;
import com.pctheone.money_flow.services.telegram.TelegramApiClient;
import com.pctheone.money_flow.services.telegram.TelegramCallbackHandler;
import com.pctheone.money_flow.services.telegram.TelegramCallbackRegistry;
import com.pctheone.money_flow.services.telegram.TelegramProcessedUpdateService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class TelegramBotServicesTest {

    private static final ObjectMapper UPDATE_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final long CHAT_ID = 42L;
    private static final int OWNER_ID = 7;

    TelegramBotServices service;
    TelegramApiClient telegramApiClient;
    TelegramProcessedUpdateService processedUpdateService;
    TransactionsService transactionsService;
    CategoryService categoryService;
    AccountService accountService;
    OwnerRepository ownerRepository;
    OpenAIClient openAIClient;
    RecordingHandler handler;

    int nextUpdateId = 1;

    @BeforeEach
    void setUp() {
        telegramApiClient = Mockito.mock(TelegramApiClient.class);
        processedUpdateService = Mockito.mock(TelegramProcessedUpdateService.class);
        transactionsService = Mockito.mock(TransactionsService.class);
        categoryService = Mockito.mock(CategoryService.class);
        accountService = Mockito.mock(AccountService.class);
        ownerRepository = Mockito.mock(OwnerRepository.class);
        openAIClient = Mockito.mock(OpenAIClient.class);

        Mockito.when(processedUpdateService.markAsProcessed(ArgumentMatchers.anyLong())).thenReturn(true);
        OwnerEntity owner = new OwnerEntity();
        owner.setOwnerId(OWNER_ID);
        Mockito.when(ownerRepository.findByTelegramChatId(CHAT_ID)).thenReturn(Optional.of(owner));

        handler = new RecordingHandler();
        @SuppressWarnings("unchecked")
        ObjectProvider<TelegramCallbackHandler> provider = Mockito.mock(ObjectProvider.class);
        Mockito.when(provider.orderedStream()).thenAnswer(invocation -> Stream.of(handler));

        service = new TelegramBotServices();
        service.telegramApiClient = telegramApiClient;
        service.processedUpdateService = processedUpdateService;
        service.callbackRegistry = new TelegramCallbackRegistry(provider);
        service.transactionsService = transactionsService;
        service.categoryService = categoryService;
        service.accountService = accountService;
        service.ownerRepository = ownerRepository;
        service.openAIClient = openAIClient;
    }

    //---- Error handling

    @Test
    void unknownCategoryRepliesCategoryNotFound() {
        Mockito.when(transactionsService.registerExpense(OWNER_ID, "mercado", 1, "25", "carne"))
                .thenThrow(new CategoryNotFoundException("No category found for this description"));

        service.telegramRouter(textUpdate("/gasto mercado carne 25 1"));

        assertSingleReply(TelegramBotServices.CATEGORY_NOT_FOUND_MESSAGE);
    }

    @Test
    void invalidAmountRepliesInvalidAmount() {
        service.telegramRouter(textUpdate("/gasto mercado carne abc 1"));

        assertSingleReply(TelegramBotServices.INVALID_AMOUNT_MESSAGE);
        Mockito.verifyNoInteractions(transactionsService);
    }

    @Test
    void invalidAmountOnNewAccountRepliesInvalidAmount() {
        service.telegramRouter(textUpdate("/nova_conta Wallet abc"));

        assertSingleReply(TelegramBotServices.INVALID_AMOUNT_MESSAGE);
        Mockito.verifyNoInteractions(accountService);
    }

    @Test
    void nonNumericAccountIdRepliesInvalidAccount() {
        service.telegramRouter(textUpdate("/entrada salario maio 1000 abc"));

        assertSingleReply(TelegramBotServices.INVALID_ACCOUNT_MESSAGE);
        Mockito.verifyNoInteractions(transactionsService);
    }

    @Test
    void accountOfAnotherOwnerRepliesInvalidAccount() {
        Mockito.doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Account does not belong to the authenticated user"))
                .when(accountService).validateAccountOwnership(99, OWNER_ID);

        service.telegramRouter(textUpdate("/gasto mercado carne 25 99"));

        assertSingleReply(TelegramBotServices.INVALID_ACCOUNT_MESSAGE);
        Mockito.verifyNoInteractions(transactionsService);
    }

    @Test
    void invalidTransactionIdRepliesInvalidTransaction() {
        service.telegramRouter(textUpdate("/deletar_transacao abc"));

        assertSingleReply(TelegramBotServices.INVALID_TRANSACTION_MESSAGE);
    }

    @Test
    void unexpectedFailureRepliesGenericMessage() {
        Mockito.when(categoryService.listAllCategories(OWNER_ID)).thenThrow(new IllegalStateException("db down"));

        Assertions.assertDoesNotThrow(() -> service.telegramRouter(textUpdate("/categorias")));

        assertSingleReply(TelegramBotServices.GENERIC_ERROR_MESSAGE);
    }

    @Test
    void voiceFailureRepliesVoiceErrorMessage() throws Exception {
        Mockito.when(telegramApiClient.downloadFile("file-1")).thenThrow(new IOException("telegram down"));

        service.telegramRouter(voiceUpdate("file-1"));

        assertSingleReply(TelegramBotServices.VOICE_ERROR_MESSAGE);
        Assertions.assertFalse(Thread.currentThread().isInterrupted());
        Mockito.verifyNoInteractions(openAIClient);
    }

    //---- Idempotency

    @Test
    void duplicateUpdateIsSkipped() {
        Update update = textUpdate("/categorias");
        Mockito.when(processedUpdateService.markAsProcessed(update.getUpdateId().longValue())).thenReturn(false);

        service.telegramRouter(update);

        Mockito.verifyNoInteractions(categoryService, ownerRepository, telegramApiClient);
    }

    @Test
    void sameUpdateDeliveredTwiceIsProcessedOnce() {
        Mockito.when(processedUpdateService.markAsProcessed(ArgumentMatchers.anyLong())).thenReturn(true, false);
        Mockito.when(categoryService.addCategory("food", CategoryTypeEnum.EXPENSE, OWNER_ID)).thenReturn("Category Food created!");
        Update update = textUpdate("/nova_categoria food");

        service.telegramRouter(update);
        service.telegramRouter(update);

        Mockito.verify(categoryService, Mockito.times(1)).addCategory("food", CategoryTypeEnum.EXPENSE, OWNER_ID);
    }

    //---- Callback queries

    @Test
    void callbackIsDispatchedByPrefixAndAnswered() {
        handler.answer = "Saved";

        service.telegramRouter(callbackUpdate("cb-1", "test:currency:BRL"));

        Assertions.assertEquals(1, handler.received.size());
        TelegramCallbackHandler.Context context = handler.received.getFirst();
        Assertions.assertEquals("currency:BRL", context.payload());
        Assertions.assertEquals(OWNER_ID, context.ownerId());
        Assertions.assertEquals(CHAT_ID, context.chatId());
        Mockito.verify(telegramApiClient).answerCallbackQuery("cb-1", "Saved");
    }

    @Test
    void callbackWithUnknownPrefixIsStillAnswered() {
        service.telegramRouter(callbackUpdate("cb-2", "unknown:x"));

        Assertions.assertTrue(handler.received.isEmpty());
        Mockito.verify(telegramApiClient).answerCallbackQuery("cb-2", null);
    }

    @Test
    void callbackHandlerFailureRepliesAndAnswers() {
        handler.failure = new IllegalStateException("boom");

        service.telegramRouter(callbackUpdate("cb-3", "test:x"));

        assertSingleReply(TelegramBotServices.GENERIC_ERROR_MESSAGE);
        Mockito.verify(telegramApiClient).answerCallbackQuery("cb-3", null);
    }

    @Test
    void callbackFromUnlinkedChatIsNotDispatched() {
        Mockito.when(ownerRepository.findByTelegramChatId(CHAT_ID)).thenReturn(Optional.empty());

        service.telegramRouter(callbackUpdate("cb-4", "test:x"));

        Assertions.assertTrue(handler.received.isEmpty());
        assertSingleReply(TelegramBotServices.NOT_LINKED_MESSAGE);
        Mockito.verify(telegramApiClient).answerCallbackQuery("cb-4", null);
    }

    @Test
    void duplicateCallbackPrefixIsRejected() {
        Assertions.assertThrows(IllegalStateException.class, () -> service.callbackRegistry.register(new RecordingHandler()));
    }

    //---- Commands and aliases

    @Test
    void newCategoryCommandAndAliasBehaveTheSame() {
        Mockito.when(categoryService.addCategory(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn("ok");

        service.telegramRouter(textUpdate("/nova_categoria salario entrada"));
        service.telegramRouter(textUpdate("/add-categoria salario entrada"));

        Mockito.verify(categoryService, Mockito.times(2)).addCategory("salario", CategoryTypeEnum.INCOME, OWNER_ID);
    }

    @Test
    void newAccountCommandAndAliasBehaveTheSame() {
        Mockito.when(accountService.addAccount(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.anyInt()))
                .thenReturn(new AccountDTO(1, "Wallet", BigDecimal.TEN, CurrencyEnum.BRL));

        service.telegramRouter(textUpdate("/nova_conta Wallet 10 BRL"));
        service.telegramRouter(textUpdate("/add-conta Wallet 10 BRL"));

        Mockito.verify(accountService, Mockito.times(2)).addAccount("Wallet", new BigDecimal("10"), CurrencyEnum.BRL, OWNER_ID);
    }

    @Test
    void deleteTransactionCommandAndAliasBehaveTheSame() {
        Mockito.when(transactionsService.deleteTransaction(5, OWNER_ID)).thenReturn("deleted");

        service.telegramRouter(textUpdate("/deletar_transacao 5"));
        service.telegramRouter(textUpdate("/deletar-transacao 5"));

        Mockito.verify(transactionsService, Mockito.times(2)).deleteTransaction(5, OWNER_ID);
    }

    @Test
    void commandWithBotUsernameIsRecognised() {
        Mockito.when(transactionsService.deleteTransaction(5, OWNER_ID)).thenReturn("deleted");

        service.telegramRouter(textUpdate("/deletar_transacao@MoneyFlowBot 5"));

        Mockito.verify(transactionsService).deleteTransaction(5, OWNER_ID);
    }

    @Test
    void helpListsCommandsInUserLanguage() {
        service.telegramRouter(textUpdate("/ajuda", "pt-br"));
        service.telegramRouter(textUpdate("/help", "en"));

        ArgumentCaptor<String> replies = ArgumentCaptor.forClass(String.class);
        Mockito.verify(telegramApiClient, Mockito.times(2)).sendMessage(ArgumentMatchers.eq(CHAT_ID), replies.capture());
        Assertions.assertTrue(replies.getAllValues().get(0).startsWith("🤖 Comandos disponíveis"));
        Assertions.assertTrue(replies.getAllValues().get(1).startsWith("🤖 Available commands"));
        for (String reply : replies.getAllValues()) {
            Assertions.assertTrue(reply.contains("/nova_categoria"));
            Assertions.assertTrue(reply.contains("/nova_conta"));
            Assertions.assertTrue(reply.contains("/deletar_transacao"));
            Assertions.assertFalse(reply.contains("/add-"));
        }
    }

    //---- Helpers

    private void assertSingleReply(String expected) {
        Mockito.verify(telegramApiClient).sendMessage(CHAT_ID, expected);
    }

    private Update textUpdate(String text) {
        return textUpdate(text, "pt-br");
    }

    private Update textUpdate(String text, String languageCode) {
        String json = """
                {"update_id": %d, "message": {"message_id": 1, "date": 0,
                  "chat": {"id": %d, "type": "private"},
                  "from": {"id": %d, "is_bot": false, "first_name": "G", "language_code": "%s"},
                  "text": "%s"}}
                """.formatted(nextUpdateId++, CHAT_ID, CHAT_ID, languageCode, text);
        return parse(json);
    }

    private Update voiceUpdate(String fileId) {
        String json = """
                {"update_id": %d, "message": {"message_id": 1, "date": 0,
                  "chat": {"id": %d, "type": "private"},
                  "voice": {"file_id": "%s", "file_unique_id": "u", "duration": 2}}}
                """.formatted(nextUpdateId++, CHAT_ID, fileId);
        return parse(json);
    }

    private Update callbackUpdate(String callbackId, String data) {
        String json = """
                {"update_id": %d, "callback_query": {"id": "%s", "chat_instance": "ci", "data": "%s",
                  "from": {"id": %d, "is_bot": false, "first_name": "G"},
                  "message": {"message_id": 10, "date": 1, "chat": {"id": %d, "type": "private"}, "text": "pick one"}}}
                """.formatted(nextUpdateId++, callbackId, data, CHAT_ID, CHAT_ID);
        return parse(json);
    }

    private Update parse(String json) {
        try {
            return UPDATE_MAPPER.readValue(json, Update.class);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static class RecordingHandler implements TelegramCallbackHandler {
        final List<Context> received = new ArrayList<>();
        String answer;
        RuntimeException failure;

        @Override
        public String prefix() {
            return "test";
        }

        @Override
        public String handle(Context context) {
            received.add(context);
            if (failure != null)
                throw failure;
            return answer;
        }
    }
}
