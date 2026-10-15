package com.pctheone.money_flow.services;

import com.pctheone.money_flow.services.telegram.TelegramBotCommands;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pctheone.money_flow.dto.AccountDTO;
import com.pctheone.money_flow.dto.CategoryCreationResultDTO;
import com.pctheone.money_flow.dto.DeletedTransactionDTO;
import com.pctheone.money_flow.dto.TelegramInlineButtonDTO;
import com.pctheone.money_flow.dto.TransactionDTO;
import com.pctheone.money_flow.dto.TransactionRegistrationResultDTO;
import com.pctheone.money_flow.entities.CategoryEntity;
import com.pctheone.money_flow.entities.OwnerEntity;
import com.pctheone.money_flow.enums.CategoryTypeEnum;
import com.pctheone.money_flow.enums.CurrencyEnum;
import com.pctheone.money_flow.enums.LanguageEnum;
import com.pctheone.money_flow.enums.OnboardingStepEnum;
import com.pctheone.money_flow.enums.OperationTypeEnum;
import com.pctheone.money_flow.exceptions.CategoryNotFoundException;
import com.pctheone.money_flow.exceptions.DuplicateAccountNameException;
import com.pctheone.money_flow.repositories.OwnerRepository;
import com.pctheone.money_flow.services.telegram.BotMessages;
import com.pctheone.money_flow.services.telegram.OpenAITextClient;
import com.pctheone.money_flow.services.telegram.TelegramApiClient;
import com.pctheone.money_flow.services.telegram.TelegramCallbackHandler;
import com.pctheone.money_flow.services.telegram.TelegramCallbackRegistry;
import com.pctheone.money_flow.services.telegram.TelegramProcessedUpdateService;
import com.pctheone.money_flow.services.telegram.VoiceTranscriptionService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.mockito.InOrder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
    OpenAITextClient openAITextClient;
    OwnerService ownerService;
    OnboardingService onboardingService;
    VoiceTranscriptionService voiceTranscriptionService;
    BotMessages messages = new BotMessages();
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
        openAITextClient = Mockito.mock(OpenAITextClient.class);
        ownerService = Mockito.mock(OwnerService.class);
        onboardingService = Mockito.mock(OnboardingService.class);
        voiceTranscriptionService = Mockito.mock(VoiceTranscriptionService.class);

        Mockito.when(processedUpdateService.markAsProcessed(ArgumentMatchers.anyLong())).thenReturn(true);
        OwnerEntity owner = new OwnerEntity();
        owner.setOwnerId(OWNER_ID);
        owner.setOnboardingStep(OnboardingStepEnum.DONE);
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
        service.openAITextClient = openAITextClient;
        service.ownerService = ownerService;
        service.onboardingService = onboardingService;
        service.voiceTranscriptionService = voiceTranscriptionService;
        service.messages = messages;
    }

    //---- Error handling

    @Test
    void unknownCategoryRepliesCategoryNotFound() {
        Mockito.when(transactionsService.registerExpense(OWNER_ID, "mercado", 1, "25", "carne"))
                .thenThrow(new CategoryNotFoundException("No category found for this description"));

        service.telegramRouter(textUpdate("/gasto mercado carne 25 1"));

        assertSingleReply(pt(TelegramBotServices.CATEGORY_NOT_FOUND_MESSAGE));
    }

    @Test
    void invalidAmountRepliesInvalidAmount() {
        service.telegramRouter(textUpdate("/gasto mercado carne abc 1"));

        assertSingleReply(pt(TelegramBotServices.INVALID_AMOUNT_MESSAGE));
        Mockito.verifyNoInteractions(transactionsService);
    }

    @Test
    void invalidAmountOnNewAccountRepliesInvalidAmount() {
        service.telegramRouter(textUpdate("/nova_conta Wallet abc"));

        assertSingleReply(pt(TelegramBotServices.INVALID_AMOUNT_MESSAGE));
        Mockito.verifyNoInteractions(accountService);
    }

    @Test
    void nonNumericAccountIdRepliesInvalidAccount() {
        service.telegramRouter(textUpdate("/entrada salario maio 1000 abc"));

        assertSingleReply(pt(TelegramBotServices.INVALID_ACCOUNT_MESSAGE));
        Mockito.verifyNoInteractions(transactionsService);
    }

    @Test
    void accountOfAnotherOwnerRepliesInvalidAccount() {
        Mockito.when(transactionsService.registerExpense(OWNER_ID, "mercado", 99, "25", "carne"))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Account does not belong to the authenticated user"));

        service.telegramRouter(textUpdate("/gasto mercado carne 25 99"));

        assertSingleReply(pt(TelegramBotServices.INVALID_ACCOUNT_MESSAGE));
        Mockito.verify(accountService, Mockito.never()).validateAccountOwnership(ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    @Test
    void ambiguousPeriodDecimalIsRejectedWithFormatHint() {
        service.telegramRouter(textUpdate("/gasto mercado carne 25.90 1"));

        assertSingleReply(pt(TelegramBotServices.INVALID_AMOUNT_MESSAGE));
        Assertions.assertTrue(pt(TelegramBotServices.INVALID_AMOUNT_MESSAGE).contains("25,90"));
        Mockito.verifyNoInteractions(transactionsService);
    }

    @Test
    void malformedTransactionAmountsAreRejected() {
        for (String amount : List.of("1e5", "12a", "-5", "1.25", "25,999", ",5")) {
            service.telegramRouter(textUpdate("/gasto mercado carne " + amount + " 1"));
        }

        Mockito.verify(telegramApiClient, Mockito.times(6)).sendMessage(CHAT_ID, pt(TelegramBotServices.INVALID_AMOUNT_MESSAGE));
        Mockito.verifyNoInteractions(transactionsService);
    }

    @Test
    void zeroTransactionAmountIsRejected() {
        service.telegramRouter(textUpdate("/entrada salario maio 0,00 1"));

        assertSingleReply(pt(TelegramBotServices.ZERO_AMOUNT_MESSAGE));
        Mockito.verifyNoInteractions(transactionsService);
    }

    @Test
    void brazilianAmountsAreAccepted() {
        for (String amount : List.of("1.250,90", "1250,90", "1250", "25,9")) {
            service.telegramRouter(textUpdate("/gasto mercado carne " + amount + " 1"));
            Mockito.verify(transactionsService).registerExpense(OWNER_ID, "mercado", 1, amount, "carne");
        }
    }

    @Test
    void multiWordDescriptionIsJoined() {
        service.telegramRouter(textUpdate("/gasto mercado carne moida   de primeira 25,90 1"));

        Mockito.verify(transactionsService).registerExpense(OWNER_ID, "mercado", 1, "25,90", "carne moida de primeira");
    }

    @Test
    void newAccountAcceptsNegativeAndZeroBalance() {
        Mockito.when(accountService.addAccount(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.anyInt()))
                .thenReturn(new AccountDTO(1, "Card", BigDecimal.ZERO, CurrencyEnum.BRL));

        service.telegramRouter(textUpdate("/nova_conta Card -1.500,50 BRL"));
        service.telegramRouter(textUpdate("/add-conta Empty 0 BRL"));

        Mockito.verify(accountService).addAccount("Card", new BigDecimal("-1500.50"), CurrencyEnum.BRL, OWNER_ID);
        Mockito.verify(accountService).addAccount("Empty", new BigDecimal("0"), CurrencyEnum.BRL, OWNER_ID);
    }

    @Test
    void forbiddenFromNonAccountCommandRepliesNotAllowed() {
        Mockito.when(transactionsService.deleteTransaction(5, OWNER_ID))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Transaction does not belong to the authenticated user"));

        service.telegramRouter(textUpdate("/deletar_transacao 5"));

        assertSingleReply(pt(TelegramBotServices.NOT_ALLOWED_MESSAGE));
    }

    @Test
    void otherResponseStatusRepliesGenericMessage() {
        Mockito.when(categoryService.listAllCategories(OWNER_ID))
                .thenThrow(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "boom"));

        service.telegramRouter(textUpdate("/categorias"));

        assertSingleReply(pt(TelegramBotServices.GENERIC_ERROR_MESSAGE));
    }

    @Test
    void serviceUnavailableOnTransactionIsNotMappedToInvalidAccount() {
        Mockito.when(transactionsService.registerExpense(OWNER_ID, "mercado", 1, "25", "carne"))
                .thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "down"));

        service.telegramRouter(textUpdate("/gasto mercado carne 25 1"));

        assertSingleReply(pt(TelegramBotServices.GENERIC_ERROR_MESSAGE));
    }

    //---- /start

    @Test
    void startToleratesBotSuffixCaseAndExtraSpaces() {
        UUID invite = UUID.randomUUID();
        Mockito.when(ownerService.linkTelegramChat(invite, CHAT_ID)).thenReturn(true);

        service.telegramRouter(textUpdate("/start@MoneyFlowBot " + invite));
        service.telegramRouter(textUpdate("/START " + invite));
        service.telegramRouter(textUpdate("  /start    " + invite + "  "));

        Mockito.verify(ownerService, Mockito.times(3)).linkTelegramChat(invite, CHAT_ID);
    }

    @Test
    void invalidTransactionIdRepliesInvalidTransaction() {
        service.telegramRouter(textUpdate("/deletar_transacao abc"));

        assertSingleReply(pt(TelegramBotServices.INVALID_TRANSACTION_MESSAGE));
    }

    @Test
    void unexpectedFailureRepliesGenericMessage() {
        Mockito.when(categoryService.listAllCategories(OWNER_ID)).thenThrow(new IllegalStateException("db down"));

        Assertions.assertDoesNotThrow(() -> service.telegramRouter(textUpdate("/categorias")));

        assertSingleReply(pt(TelegramBotServices.GENERIC_ERROR_MESSAGE));
    }

    @Test
    void voiceFailureRepliesVoiceErrorMessage() throws Exception {
        Mockito.when(voiceTranscriptionService.transcribe("file-1")).thenThrow(new IOException("telegram down"));

        service.telegramRouter(voiceUpdate("file-1"));

        assertSingleReply(pt(TelegramBotServices.VOICE_ERROR_MESSAGE));
        Assertions.assertFalse(Thread.currentThread().isInterrupted());
        Mockito.verifyNoInteractions(openAITextClient);
    }

    //---- Idempotency

    @Test
    void claimFailureRepliesGenericMessage() {
        Mockito.when(processedUpdateService.markAsProcessed(ArgumentMatchers.anyLong())).thenThrow(new IllegalStateException("db down"));

        Assertions.assertDoesNotThrow(() -> service.telegramRouter(textUpdate("/categorias")));

        assertSingleReply(pt(TelegramBotServices.GENERIC_ERROR_MESSAGE));
        Mockito.verifyNoInteractions(categoryService);
    }

    @Test
    void claimFailureOnCallbackRepliesAndAnswers() {
        Mockito.when(processedUpdateService.markAsProcessed(ArgumentMatchers.anyLong())).thenThrow(new IllegalStateException("db down"));

        service.telegramRouter(callbackUpdate("cb-9", "test:x"));

        assertSingleReply(pt(TelegramBotServices.GENERIC_ERROR_MESSAGE));
        Mockito.verify(telegramApiClient).answerCallbackQuery("cb-9", null);
        Assertions.assertTrue(handler.received.isEmpty());
    }

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
        Mockito.when(categoryService.addCategory("food", CategoryTypeEnum.EXPENSE, OWNER_ID)).thenReturn(categoryResult("Food", CategoryTypeEnum.EXPENSE, true));
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

        assertSingleReply(pt(TelegramBotServices.GENERIC_ERROR_MESSAGE));
        Mockito.verify(telegramApiClient).answerCallbackQuery("cb-3", null);
    }

    @Test
    void callbackFromUnlinkedChatIsNotDispatched() {
        Mockito.when(ownerRepository.findByTelegramChatId(CHAT_ID)).thenReturn(Optional.empty());

        service.telegramRouter(callbackUpdate("cb-4", "test:x"));

        Assertions.assertTrue(handler.received.isEmpty());
        assertSingleReply(pt(TelegramBotServices.NOT_LINKED_MESSAGE));
        Mockito.verify(telegramApiClient).answerCallbackQuery("cb-4", null);
    }

    @Test
    void duplicateCallbackPrefixIsRejected() {
        Assertions.assertThrows(IllegalStateException.class, () -> service.callbackRegistry.register(new RecordingHandler()));
    }

    //---- Commands and aliases

    @Test
    void newCategoryCommandAndAliasBehaveTheSame() {
        Mockito.when(categoryService.addCategory(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(categoryResult("Salario", CategoryTypeEnum.INCOME, true));

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
        Mockito.when(transactionsService.deleteTransaction(5, OWNER_ID)).thenReturn(Optional.of(new DeletedTransactionDTO("feira", new BigDecimal("30"), CurrencyEnum.BRL)));

        service.telegramRouter(textUpdate("/deletar_transacao 5"));
        service.telegramRouter(textUpdate("/deletar-transacao 5"));

        Mockito.verify(transactionsService, Mockito.times(2)).deleteTransaction(5, OWNER_ID);
    }

    @Test
    void commandWithBotUsernameIsRecognised() {
        Mockito.when(transactionsService.deleteTransaction(5, OWNER_ID)).thenReturn(Optional.of(new DeletedTransactionDTO("feira", new BigDecimal("30"), CurrencyEnum.BRL)));

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

    @Test
    void helpPutsEachCommandNextToItsDescription() {
        String pt = TelegramBotCommands.helpText("pt");
        String en = TelegramBotCommands.helpText("en");

        for (String line : List.of("/gasto — Registrar um gasto", "/entrada — Registrar uma entrada", "/contas — ",
                "/nova_conta — ", "/categorias — ", "/nova_categoria — ", "/transacoes — ", "/deletar_transacao — ",
                "/cancelar — ", "/ajuda — Mostrar esta lista", "Ex.: /gasto mercado carne 25,90 1"))
            Assertions.assertTrue(pt.contains(line), line);
        for (String line : List.of("/gasto — Register an expense", "/entrada — Register an income", "/help — Show this list",
                "e.g. /gasto groceries meat 25,90 1"))
            Assertions.assertTrue(en.contains(line), line);
        //---- English help has no Portuguese descriptions or argument names left.
        for (String portuguese : List.of("Registrar", "<categoria>", "descrição", "Ex.:"))
            Assertions.assertFalse(en.contains(portuguese), portuguese);
        //---- Every command in the Telegram menu shows up in the help.
        for (var command : TelegramBotCommands.portuguese())
            Assertions.assertTrue(pt.contains("/" + command.getCommand() + " — "), command.getCommand());
        for (var command : TelegramBotCommands.english())
            Assertions.assertTrue(en.contains("/" + command.getCommand() + " — "), command.getCommand());
    }

    @Test
    void helpFollowsTheLanguageChosenInOnboarding() {
        OwnerEntity english = new OwnerEntity();
        english.setOwnerId(OWNER_ID);
        english.setOnboardingStep(OnboardingStepEnum.DONE);
        english.setLanguage("en");
        Mockito.when(ownerRepository.findByTelegramChatId(CHAT_ID)).thenReturn(Optional.of(english));

        service.telegramRouter(textUpdate("/ajuda", "pt-br"));

        Mockito.verify(telegramApiClient).sendMessage(CHAT_ID, TelegramBotCommands.helpText("en"));
    }

    @Test
    void unlinkedChatGetsHelpAndLinkInstructions() {
        Mockito.when(ownerRepository.findByTelegramChatId(CHAT_ID)).thenReturn(Optional.empty());

        service.telegramRouter(textUpdate("/help", "en"));

        InOrder order = Mockito.inOrder(telegramApiClient);
        order.verify(telegramApiClient).sendMessage(CHAT_ID, TelegramBotCommands.helpText("en"));
        order.verify(telegramApiClient).sendMessage(CHAT_ID, messages.get(LanguageEnum.EN, TelegramBotServices.NOT_LINKED_MESSAGE));
    }

    //---- Currency-formatted replies (#8)

    @Test
    void expenseAndIncomeConfirmationsUseAccountCurrency() {
        Mockito.when(transactionsService.registerExpense(OWNER_ID, "mercado", 1, "30", "feira"))
                .thenReturn(new TransactionRegistrationResultDTO(new BigDecimal("30"), new BigDecimal("470"), CurrencyEnum.BRL, "Nubank", "Mercado"));
        Mockito.when(transactionsService.registerIncome(OWNER_ID, "salario", 2, "1.000", "freela"))
                .thenReturn(new TransactionRegistrationResultDTO(new BigDecimal("1000"), new BigDecimal("1681.03"), CurrencyEnum.EUR, "Revolut", "Salario"));

        service.telegramRouter(textUpdate("/gasto mercado feira 30 1"));
        service.telegramRouter(textUpdate("/entrada salario freela 1.000 2"));

        Mockito.verify(telegramApiClient).sendMessage(CHAT_ID, "📉 R$ 30,00 · Mercado · Nubank — saldo R$ 470,00");
        Mockito.verify(telegramApiClient).sendMessage(CHAT_ID, "📈 € 1.000,00 · Salario · Revolut — saldo € 1.681,03");
    }

    @Test
    void accountsAndTransactionsListUseEachAccountCurrency() {
        Mockito.when(accountService.listAllAccounts(OWNER_ID)).thenReturn(List.of(
                new AccountDTO(1, "Nubank", new BigDecimal("470"), CurrencyEnum.BRL),
                new AccountDTO(2, "Revolut", new BigDecimal("681.03"), CurrencyEnum.EUR)));
        Mockito.when(transactionsService.listAllTransactions(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.isNull(), ArgumentMatchers.isNull(), ArgumentMatchers.eq(OWNER_ID)))
                .thenReturn(new ArrayList<>(List.of(
                        new TransactionDTO(10, "feira", new BigDecimal("30"), java.time.LocalDate.now(), "Mercado", OperationTypeEnum.EXPENSE, CurrencyEnum.BRL, 1),
                        new TransactionDTO(11, "us store", new BigDecimal("10"), java.time.LocalDate.now(), "Mercado", OperationTypeEnum.EXPENSE, CurrencyEnum.USD, 1))));

        service.telegramRouter(textUpdate("/contas"));
        service.telegramRouter(textUpdate("/transacoes"));

        ArgumentCaptor<String> replies = ArgumentCaptor.forClass(String.class);
        Mockito.verify(telegramApiClient, Mockito.times(2)).sendMessage(ArgumentMatchers.eq(CHAT_ID), replies.capture());
        String accounts = replies.getAllValues().get(0);
        Assertions.assertTrue(accounts.contains("ID: 1 - Nubank | R$ 470,00"), accounts);
        Assertions.assertTrue(accounts.contains("ID: 2 - Revolut | € 681,03"), accounts);
        String transactions = replies.getAllValues().get(1);
        Assertions.assertTrue(transactions.contains("ID: 10 - feira - R$ 30,00"), transactions);
        Assertions.assertTrue(transactions.contains("ID: 11 - us store - US$ 10,00"), transactions);
        Assertions.assertFalse(transactions.contains("€"), transactions);
    }

    //---- Onboarding (#7)

    @Test
    void ownerInOnboardingIsHandledByOnboardingService() {
        owner().setOnboardingStep(OnboardingStepEnum.ACCOUNT);
        Mockito.when(onboardingService.handleMessage(ArgumentMatchers.eq(OWNER_ID), ArgumentMatchers.eq(CHAT_ID), ArgumentMatchers.any())).thenReturn(true);

        service.telegramRouter(textUpdate("Nubank, 50 mil reais"));

        ArgumentCaptor<OnboardingService.Input> input = ArgumentCaptor.forClass(OnboardingService.Input.class);
        Mockito.verify(onboardingService).handleMessage(ArgumentMatchers.eq(OWNER_ID), ArgumentMatchers.eq(CHAT_ID), input.capture());
        Assertions.assertEquals("Nubank, 50 mil reais", input.getValue().text());
        Assertions.assertEquals("pt-br", input.getValue().telegramLanguageCode());
        Mockito.verifyNoInteractions(transactionsService, accountService, categoryService);
    }

    @Test
    void doneOwnerSkipsOnboardingService() {
        service.telegramRouter(textUpdate("/categorias"));

        Mockito.verifyNoInteractions(onboardingService);
        Mockito.verify(categoryService).listAllCategories(OWNER_ID);
    }

    @Test
    void transactionDuringTutorialFinishesOnboarding() {
        owner().setOnboardingStep(OnboardingStepEnum.TUTORIAL);
        Mockito.when(transactionsService.registerExpense(OWNER_ID, "mercado", 1, "30", "feira"))
                .thenReturn(new TransactionRegistrationResultDTO(new BigDecimal("30"), new BigDecimal("470"), CurrencyEnum.BRL, "Nubank", "Mercado"));

        service.telegramRouter(textUpdate("/gasto mercado feira 30 1"));

        Mockito.verify(onboardingService).completeTutorial(OWNER_ID, CHAT_ID, "pt-br");
    }

    @Test
    void failedTransactionDuringTutorialDoesNotFinishOnboarding() {
        owner().setOnboardingStep(OnboardingStepEnum.TUTORIAL);

        service.telegramRouter(textUpdate("/gasto mercado feira abc 1"));

        Mockito.verify(onboardingService, Mockito.never()).completeTutorial(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    @Test
    void linkingStartsOnboardingForNewOwner() {
        UUID invite = UUID.randomUUID();
        Mockito.when(ownerService.linkTelegramChat(invite, CHAT_ID)).thenReturn(true);
        owner().setOnboardingStep(OnboardingStepEnum.LANGUAGE);

        service.telegramRouter(textUpdate("/start " + invite, "en"));

        Mockito.verify(telegramApiClient).sendMessage(CHAT_ID, messages.get(LanguageEnum.EN, "link.success"));
        Mockito.verify(onboardingService).resume(OWNER_ID, CHAT_ID, "en");
    }

    @Test
    void relinkingDoesNotRestartFinishedOnboarding() {
        UUID invite = UUID.randomUUID();
        Mockito.when(ownerService.linkTelegramChat(invite, CHAT_ID)).thenReturn(true);

        service.telegramRouter(textUpdate("/start " + invite));

        Mockito.verify(telegramApiClient).sendMessage(CHAT_ID, pt("link.success"));
        Mockito.verifyNoInteractions(onboardingService);
    }

    @Test
    void plainStartResumesUnfinishedOnboarding() {
        owner().setOnboardingStep(OnboardingStepEnum.MORE_ACCOUNTS);

        service.telegramRouter(textUpdate("/start"));

        Mockito.verify(onboardingService).resume(OWNER_ID, CHAT_ID, "pt-br");
    }

    @Test
    void cancelOutsideOnboardingSaysNothingToCancel() {
        service.telegramRouter(textUpdate("/cancelar"));

        assertSingleReply(pt("onboarding.nothingToCancel"));
    }

    //---- Bundles: owner language wins over Telegram language

    @Test
    void repliesFollowOwnerLanguage() {
        owner().setLanguage("en");

        service.telegramRouter(textUpdate("/deletar_transacao abc", "pt-br"));

        assertSingleReply(messages.get(LanguageEnum.EN, TelegramBotServices.INVALID_TRANSACTION_MESSAGE));
    }

    @Test
    void deleteTransactionReplyUsesAccountCurrency() {
        Mockito.when(transactionsService.deleteTransaction(5, OWNER_ID))
                .thenReturn(Optional.of(new DeletedTransactionDTO("feira", new BigDecimal("30"), CurrencyEnum.EUR)));
        Mockito.when(transactionsService.deleteTransaction(6, OWNER_ID)).thenReturn(Optional.empty());

        service.telegramRouter(textUpdate("/deletar_transacao 5"));
        service.telegramRouter(textUpdate("/deletar_transacao 6"));

        Mockito.verify(telegramApiClient).sendMessage(CHAT_ID, "🗑️ Transação apagada: feira - € 30,00");
        Mockito.verify(telegramApiClient).sendMessage(CHAT_ID, pt("transaction.notFound", "6"));
    }

    @Test
    void newCategoryRepliesWithTheRealOutcome() {
        Mockito.when(categoryService.addCategory("academia", CategoryTypeEnum.EXPENSE, OWNER_ID)).thenReturn(categoryResult("Academia", CategoryTypeEnum.EXPENSE, true));
        Mockito.when(categoryService.addCategory("mercado", CategoryTypeEnum.EXPENSE, OWNER_ID)).thenReturn(categoryResult("Mercado", CategoryTypeEnum.EXPENSE, false));
        Mockito.when(categoryService.addCategory("salario", CategoryTypeEnum.EXPENSE, OWNER_ID)).thenReturn(categoryResult("Salario", CategoryTypeEnum.INCOME, false));

        service.telegramRouter(textUpdate("/nova_categoria academia"));
        service.telegramRouter(textUpdate("/nova_categoria mercado"));
        service.telegramRouter(textUpdate("/nova_categoria salario"));

        Mockito.verify(telegramApiClient).sendMessage(CHAT_ID, pt("category.created", "Academia"));
        Mockito.verify(telegramApiClient).sendMessage(CHAT_ID, pt("category.exists", "Mercado"));
        Mockito.verify(telegramApiClient).sendMessage(CHAT_ID, "🚨 O nome Salario já é usado por uma categoria de entrada. Escolha outro nome.");
    }

    @Test
    void duplicateAccountNameOnNewAccountIsFriendly() {
        Mockito.when(accountService.addAccount("nubank", new BigDecimal("10"), CurrencyEnum.BRL, OWNER_ID))
                .thenThrow(new DuplicateAccountNameException("nubank"));

        service.telegramRouter(textUpdate("/nova_conta nubank 10 BRL"));

        assertSingleReply(pt("account.duplicate", "nubank"));
    }

    //---- Voice parser (#7): category type and "create it?"

    @Test
    void unknownVoiceCategoryOffersToCreateIt() {
        boolean registered = service.proccessTranscriptedMessage(
                "{\"error\": \"CATEGORY_NOT_FOUND\", \"category\": \"Farmácia\", \"operationType\": \"EXPENSE\"}", CHAT_ID, OWNER_ID, LanguageEnum.PT);

        Assertions.assertFalse(registered);
        List<List<TelegramInlineButtonDTO>> keyboard = lastKeyboard(pt("voice.categoryNotFound", "Farmácia", "gasto"));
        Assertions.assertEquals("cat:new:E:Farmácia", keyboard.getFirst().get(0).getCallbackData());
        Assertions.assertEquals("cat:no", keyboard.getFirst().get(1).getCallbackData());
        Mockito.verifyNoInteractions(transactionsService);
    }

    @Test
    void voiceCategoryMissingInDatabaseOffersToCreateItWithOperationType() {
        boolean registered = service.proccessTranscriptedMessage(
                "{\"operationType\": \"INCOME\", \"category\": \"Freela\", \"description\": \"site\", \"amount\": \"500\", \"accountId\": 1}", CHAT_ID, OWNER_ID, LanguageEnum.EN);

        Assertions.assertFalse(registered);
        List<List<TelegramInlineButtonDTO>> keyboard = lastKeyboard(messages.get(LanguageEnum.EN, "voice.categoryNotFound", "Freela", "income"));
        Assertions.assertEquals("cat:new:I:Freela", keyboard.getFirst().get(0).getCallbackData());
    }

    @Test
    void tooLongCategoryNameFallsBackToPlainMessage() {
        String name = "Categoria com um nome realmente muito comprido demais pra caber";
        service.proccessTranscriptedMessage(
                "{\"error\": \"CATEGORY_NOT_FOUND\", \"category\": \"" + name + "\", \"operationType\": \"EXPENSE\"}", CHAT_ID, OWNER_ID, LanguageEnum.PT);

        assertSingleReply(pt("voice.categoryNotFoundNoButtons", name));
    }

    @Test
    void voiceExpenseWithIncomeCategoryIsRejected() {
        CategoryEntity salario = new CategoryEntity();
        salario.setDescription("Salário");
        salario.setType(CategoryTypeEnum.INCOME);
        Mockito.when(categoryService.findByDescription("Salário", OWNER_ID)).thenReturn(Optional.of(salario));

        boolean registered = service.proccessTranscriptedMessage(
                "{\"operationType\": \"EXPENSE\", \"category\": \"Salário\", \"description\": \"x\", \"amount\": \"10\", \"accountId\": 1}", CHAT_ID, OWNER_ID, LanguageEnum.PT);

        Assertions.assertFalse(registered);
        assertSingleReply(pt("voice.categoryTypeMismatch", "Salário", "entrada"));
        Mockito.verifyNoInteractions(transactionsService);
    }

    @Test
    void voiceExpenseIsRegistered() {
        CategoryEntity mercado = new CategoryEntity();
        mercado.setDescription("Mercado");
        mercado.setType(CategoryTypeEnum.EXPENSE);
        Mockito.when(categoryService.findByDescription("Mercado", OWNER_ID)).thenReturn(Optional.of(mercado));
        Mockito.when(transactionsService.registerExpense(OWNER_ID, "Mercado", 1, "30", "feira"))
                .thenReturn(new TransactionRegistrationResultDTO(new BigDecimal("30"), new BigDecimal("470"), CurrencyEnum.BRL, "Nubank", "Mercado"));

        boolean registered = service.proccessTranscriptedMessage(
                "{\"operationType\": \"EXPENSE\", \"category\": \"Mercado\", \"description\": \"feira\", \"amount\": \"30\", \"accountId\": 1}", CHAT_ID, OWNER_ID, LanguageEnum.PT);

        Assertions.assertTrue(registered);
        assertSingleReply("📉 R$ 30,00 · Mercado · Nubank — saldo R$ 470,00");
    }

    @Test
    void notUnderstoodVoiceAsksToRepeat() {
        Assertions.assertFalse(service.proccessTranscriptedMessage("{\"error\": \"NOT_UNDERSTOOD\"}", CHAT_ID, OWNER_ID, LanguageEnum.PT));

        assertSingleReply(pt("voice.notRecognized"));
    }

    //---- Helpers

    private static CategoryCreationResultDTO categoryResult(String description, CategoryTypeEnum type, boolean created) {
        CategoryEntity category = new CategoryEntity();
        category.setDescription(description);
        category.setType(type);
        return new CategoryCreationResultDTO(category, created);
    }

    private OwnerEntity owner() {
        return ownerRepository.findByTelegramChatId(CHAT_ID).orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private List<List<TelegramInlineButtonDTO>> lastKeyboard(String expectedText) {
        ArgumentCaptor<List<List<TelegramInlineButtonDTO>>> keyboard = ArgumentCaptor.forClass(List.class);
        Mockito.verify(telegramApiClient).sendMessageWithInlineKeyboard(ArgumentMatchers.eq(CHAT_ID), ArgumentMatchers.eq(expectedText), keyboard.capture());
        return keyboard.getValue();
    }

    private String pt(String key, Object... args) {
        return messages.get(LanguageEnum.PT, key, args);
    }

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
                  "from": {"id": %d, "is_bot": false, "first_name": "G", "language_code": "pt-br"},
                  "voice": {"file_id": "%s", "file_unique_id": "u", "duration": 2}}}
                """.formatted(nextUpdateId++, CHAT_ID, CHAT_ID, fileId);
        return parse(json);
    }

    private Update callbackUpdate(String callbackId, String data) {
        String json = """
                {"update_id": %d, "callback_query": {"id": "%s", "chat_instance": "ci", "data": "%s",
                  "from": {"id": %d, "is_bot": false, "first_name": "G", "language_code": "pt-br"},
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
