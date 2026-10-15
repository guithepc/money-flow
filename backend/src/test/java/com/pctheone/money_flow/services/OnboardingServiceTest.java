package com.pctheone.money_flow.services;

import com.pctheone.money_flow.dto.AccountDTO;
import com.pctheone.money_flow.dto.CategoryDTO;
import com.pctheone.money_flow.dto.TelegramInlineButtonDTO;
import com.pctheone.money_flow.entities.OwnerEntity;
import com.pctheone.money_flow.enums.CategoryTypeEnum;
import com.pctheone.money_flow.enums.CurrencyEnum;
import com.pctheone.money_flow.enums.LanguageEnum;
import com.pctheone.money_flow.enums.OnboardingStepEnum;
import com.pctheone.money_flow.exceptions.DuplicateAccountNameException;
import com.pctheone.money_flow.repositories.OwnerRepository;
import com.pctheone.money_flow.services.telegram.AccountMessageParser;
import com.pctheone.money_flow.services.telegram.BotMessages;
import com.pctheone.money_flow.services.telegram.TelegramApiClient;
import com.pctheone.money_flow.services.telegram.TelegramReplySender;
import com.pctheone.money_flow.services.telegram.VoiceTranscriptionService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public class OnboardingServiceTest {

    private static final long CHAT_ID = 42L;
    private static final int OWNER_ID = 7;
    private static final String DASHBOARD = "https://moneyflow.pctheone.com";

    OnboardingService service;
    OnboardingStateMachine stateMachine;
    OwnerEntity owner;
    OwnerRepository ownerRepository;
    AccountService accountService;
    CategoryService categoryService;
    TelegramApiClient telegramApiClient;
    AccountMessageParser accountMessageParser;
    VoiceTranscriptionService voiceTranscriptionService;
    BotMessages messages = new BotMessages();

    @BeforeEach
    void setUp() {
        owner = new OwnerEntity();
        owner.setOwnerId(OWNER_ID);

        ownerRepository = Mockito.mock(OwnerRepository.class);
        accountService = Mockito.mock(AccountService.class);
        categoryService = Mockito.mock(CategoryService.class);
        telegramApiClient = Mockito.mock(TelegramApiClient.class);
        accountMessageParser = Mockito.mock(AccountMessageParser.class);
        voiceTranscriptionService = Mockito.mock(VoiceTranscriptionService.class);

        Mockito.when(ownerRepository.findByIdForUpdate(OWNER_ID)).thenReturn(Optional.of(owner));
        Mockito.when(accountService.addAccount(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.anyInt()))
                .thenAnswer(invocation -> new AccountDTO(1, invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)));
        Mockito.when(accountService.listAllAccounts(OWNER_ID)).thenReturn(List.of(new AccountDTO(1, "Nubank", BigDecimal.TEN, CurrencyEnum.BRL)));
        Mockito.when(categoryService.listAllCategories(OWNER_ID)).thenReturn(List.of(
                new CategoryDTO("Mercado", 1, CategoryTypeEnum.EXPENSE),
                new CategoryDTO("Casa", 2, CategoryTypeEnum.EXPENSE),
                new CategoryDTO("Salário", 3, CategoryTypeEnum.INCOME)));

        Mockito.when(ownerRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));
        TelegramReplySender replySender = new TelegramReplySender(telegramApiClient);

        stateMachine = new OnboardingStateMachine();
        stateMachine.ownerRepository = ownerRepository;
        stateMachine.accountService = accountService;
        stateMachine.categoryService = categoryService;
        stateMachine.replySender = replySender;
        stateMachine.messages = messages;
        stateMachine.dashboardUrl = DASHBOARD;

        service = new OnboardingService();
        service.stateMachine = stateMachine;
        service.ownerRepository = ownerRepository;
        service.accountMessageParser = accountMessageParser;
        service.voiceTranscriptionService = voiceTranscriptionService;
        service.replySender = replySender;
        service.messages = messages;
    }

    //---- LANGUAGE

    @Test
    void languageQuestionIsAskedInTelegramLanguageWithItFirst() {
        service.resume(OWNER_ID, CHAT_ID, "en");
        List<List<TelegramInlineButtonDTO>> english = lastKeyboard(en("onboarding.language.question"));
        Assertions.assertEquals("onb:lang:en", english.getFirst().getFirst().getCallbackData());

        Mockito.reset(telegramApiClient);
        service.resume(OWNER_ID, CHAT_ID, "pt-br");
        List<List<TelegramInlineButtonDTO>> portuguese = lastKeyboard(pt("onboarding.language.question"));
        Assertions.assertEquals("onb:lang:pt", portuguese.getFirst().getFirst().getCallbackData());
        Assertions.assertEquals("onb:lang:en", portuguese.getFirst().get(1).getCallbackData());
    }

    @Test
    void choosingLanguageSeedsCategoriesAndAsksForAccount() {
        service.handleCallback(OWNER_ID, CHAT_ID, "lang:en", "pt-br");

        Assertions.assertEquals("en", owner.getLanguage());
        Assertions.assertEquals(OnboardingStepEnum.ACCOUNT, owner.getOnboardingStep());
        Mockito.verify(categoryService).seedBaseCategories(OWNER_ID, LanguageEnum.EN);
        Mockito.verify(telegramApiClient).sendMessage(CHAT_ID, en("onboarding.account.question"));
    }

    @Test
    void doubleClickOnLanguageSeedsOnlyOnce() {
        service.handleCallback(OWNER_ID, CHAT_ID, "lang:pt", "pt-br");
        service.handleCallback(OWNER_ID, CHAT_ID, "lang:pt", "pt-br");

        Mockito.verify(categoryService, Mockito.times(1)).seedBaseCategories(OWNER_ID, LanguageEnum.PT);
        Mockito.verify(telegramApiClient, Mockito.times(1)).sendMessage(CHAT_ID, pt("onboarding.account.question"));
    }

    @Test
    void languageCanBeTyped() {
        Assertions.assertTrue(service.handleMessage(OWNER_ID, CHAT_ID, text("English")));

        Assertions.assertEquals("en", owner.getLanguage());
        Assertions.assertEquals(OnboardingStepEnum.ACCOUNT, owner.getOnboardingStep());
    }

    @Test
    void invalidLanguageRepeatsTheQuestion() {
        service.handleMessage(OWNER_ID, CHAT_ID, text("oi"));

        Assertions.assertEquals(OnboardingStepEnum.LANGUAGE, owner.getOnboardingStep());
        lastKeyboard(pt("onboarding.language.question"));
        Mockito.verifyNoInteractions(categoryService);
    }

    @Test
    void unknownLanguageButtonIsIgnored() {
        service.handleCallback(OWNER_ID, CHAT_ID, "lang:fr", "pt-br");

        Assertions.assertEquals(OnboardingStepEnum.LANGUAGE, owner.getOnboardingStep());
        Mockito.verifyNoInteractions(categoryService, telegramApiClient);
    }

    //---- ACCOUNT

    @Test
    void completeAccountMessageGoesToConfirmation() {
        atAccountStep();
        parserReturns("Nubank, 50 mil reais", "Nubank", CurrencyEnum.BRL, "50000");

        service.handleMessage(OWNER_ID, CHAT_ID, text("Nubank, 50 mil reais"));

        Assertions.assertEquals(OnboardingStepEnum.ACCOUNT_CONFIRM, owner.getOnboardingStep());
        List<List<TelegramInlineButtonDTO>> keyboard = lastKeyboard("🧐 Nubank · R$ 50.000,00 — está certo?");
        Assertions.assertEquals("onb:ok", keyboard.getFirst().get(0).getCallbackData());
        Assertions.assertEquals("onb:fix", keyboard.getFirst().get(1).getCallbackData());
        Mockito.verify(accountService, Mockito.never()).addAccount(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.anyInt());
    }

    @Test
    void missingCurrencyIsAskedWithButtons() {
        atAccountStep();
        parserReturns("Wise 2000", "Wise", null, "2000");

        service.handleMessage(OWNER_ID, CHAT_ID, text("Wise 2000"));

        Assertions.assertEquals(OnboardingStepEnum.ACCOUNT, owner.getOnboardingStep());
        List<List<TelegramInlineButtonDTO>> keyboard = lastKeyboard(pt("onboarding.account.askCurrency", "Wise"));
        Assertions.assertEquals(List.of("onb:cur:BRL", "onb:cur:EUR", "onb:cur:USD"),
                keyboard.getFirst().stream().map(TelegramInlineButtonDTO::getCallbackData).toList());

        service.handleCallback(OWNER_ID, CHAT_ID, "cur:EUR", "pt-br");

        Assertions.assertEquals(OnboardingStepEnum.ACCOUNT_CONFIRM, owner.getOnboardingStep());
        lastKeyboard("🧐 Wise · € 2.000,00 — está certo?");
    }

    @Test
    void missingCurrencyCanBeTyped() {
        atAccountStep();
        parserReturns("Wise 2000", "Wise", null, "2000");
        service.handleMessage(OWNER_ID, CHAT_ID, text("Wise 2000"));

        service.handleMessage(OWNER_ID, CHAT_ID, text("dólares"));

        lastKeyboard("🧐 Wise · US$ 2.000,00 — está certo?");
    }

    @Test
    void invalidCurrencyRepeatsTheCurrencyQuestion() {
        atAccountStep();
        parserReturns("Wise 2000", "Wise", null, "2000");
        service.handleMessage(OWNER_ID, CHAT_ID, text("Wise 2000"));
        Mockito.reset(telegramApiClient);

        service.handleMessage(OWNER_ID, CHAT_ID, text("bitcoin"));

        Assertions.assertEquals(OnboardingStepEnum.ACCOUNT, owner.getOnboardingStep());
        lastKeyboard(pt("onboarding.account.askCurrency", "Wise"));
    }

    @Test
    void missingBalanceIsAskedAndNegativeBalanceIsAccepted() {
        atAccountStep();
        parserReturns("Itaú em reais", "Itaú", CurrencyEnum.BRL, null);

        service.handleMessage(OWNER_ID, CHAT_ID, text("Itaú em reais"));
        Mockito.verify(telegramApiClient).sendMessage(CHAT_ID, pt("onboarding.account.askBalance", "Itaú"));

        Mockito.when(accountMessageParser.parse("abc")).thenReturn(new AccountMessageParser.ParsedAccount(null, null, null));
        service.handleMessage(OWNER_ID, CHAT_ID, text("abc"));
        Mockito.verify(telegramApiClient).sendMessage(CHAT_ID, pt("onboarding.account.invalidBalance"));
        Assertions.assertEquals(OnboardingStepEnum.ACCOUNT, owner.getOnboardingStep());

        service.handleMessage(OWNER_ID, CHAT_ID, text("-300"));
        Assertions.assertEquals(OnboardingStepEnum.ACCOUNT_CONFIRM, owner.getOnboardingStep());
        lastKeyboard("🧐 Itaú · -R$ 300,00 — está certo?");
    }

    @Test
    void balanceAnswerAcceptsBrazilianAndThousandsFormats() {
        atAccountStep();
        parserReturns("Itaú reais", "Itaú", CurrencyEnum.BRL, null);
        service.handleMessage(OWNER_ID, CHAT_ID, text("Itaú reais"));

        service.handleMessage(OWNER_ID, CHAT_ID, text("1.250,90"));

        lastKeyboard("🧐 Itaú · R$ 1.250,90 — está certo?");
    }

    @Test
    void missingNameIsAsked() {
        atAccountStep();
        parserReturns("100 reais", null, CurrencyEnum.BRL, "100");

        service.handleMessage(OWNER_ID, CHAT_ID, text("100 reais"));
        Mockito.verify(telegramApiClient).sendMessage(CHAT_ID, pt("onboarding.account.askName"));

        service.handleMessage(OWNER_ID, CHAT_ID, text("Carteira."));
        lastKeyboard("🧐 Carteira · R$ 100,00 — está certo?");
    }

    @Test
    void notUnderstoodRepeatsTheAccountQuestion() {
        atAccountStep();
        parserReturns("bom dia", null, null, null);

        service.handleMessage(OWNER_ID, CHAT_ID, text("bom dia"));

        Mockito.verify(telegramApiClient).sendMessage(CHAT_ID, pt("onboarding.account.notUnderstood") + "\n" + pt("onboarding.account.question"));
        Assertions.assertEquals(OnboardingStepEnum.ACCOUNT, owner.getOnboardingStep());
        Assertions.assertNull(owner.getOnboardingDraft());
    }

    @Test
    void duplicateAccountNameAsksForAnotherName() {
        atAccountStep();
        Mockito.when(accountService.existsByDescription("Nubank", OWNER_ID)).thenReturn(true);
        parserReturns("Nubank, 50 mil reais", "Nubank", CurrencyEnum.BRL, "50000");

        service.handleMessage(OWNER_ID, CHAT_ID, text("Nubank, 50 mil reais"));

        Mockito.verify(telegramApiClient).sendMessage(CHAT_ID, pt("onboarding.account.duplicate", "Nubank"));
        Assertions.assertEquals(OnboardingStepEnum.ACCOUNT, owner.getOnboardingStep());

        service.handleMessage(OWNER_ID, CHAT_ID, text("Nubank PJ"));
        lastKeyboard("🧐 Nubank PJ · R$ 50.000,00 — está certo?");
    }

    @Test
    void voiceAccountMessageIsTranscribed() throws Exception {
        atAccountStep();
        Mockito.when(voiceTranscriptionService.transcribe("voice-1")).thenReturn("Nubank, 50 mil reais");
        parserReturns("Nubank, 50 mil reais", "Nubank", CurrencyEnum.BRL, "50000");

        service.handleMessage(OWNER_ID, CHAT_ID, new OnboardingService.Input(null, "voice-1", "pt-br"));

        Assertions.assertEquals(OnboardingStepEnum.ACCOUNT_CONFIRM, owner.getOnboardingStep());
    }

    @Test
    void voiceTranscriptionFailureKeepsTheStep() throws Exception {
        atAccountStep();
        Mockito.when(voiceTranscriptionService.transcribe("voice-1")).thenThrow(new IOException("down"));

        Assertions.assertTrue(service.handleMessage(OWNER_ID, CHAT_ID, new OnboardingService.Input(null, "voice-1", "pt-br")));

        Mockito.verify(telegramApiClient).sendMessage(CHAT_ID, pt("error.voice"));
        Assertions.assertEquals(OnboardingStepEnum.ACCOUNT, owner.getOnboardingStep());
    }

    @Test
    void staleCurrencyButtonIsIgnored() {
        atAccountStep();

        service.handleCallback(OWNER_ID, CHAT_ID, "cur:BRL", "pt-br");

        Assertions.assertNull(owner.getOnboardingDraft());
        Mockito.verifyNoInteractions(telegramApiClient);
    }

    @Test
    void nameAnswerWithBalanceAndCurrencyKeepsOnlyTheExtractedName() {
        atAccountStep();
        parserReturns("100 reais", null, CurrencyEnum.BRL, "100");
        service.handleMessage(OWNER_ID, CHAT_ID, text("100 reais"));
        Mockito.verify(telegramApiClient).sendMessage(CHAT_ID, pt("onboarding.account.askName"));

        parserReturns("Nubank com 200 dólares", "Nubank", CurrencyEnum.USD, "200");
        service.handleMessage(OWNER_ID, CHAT_ID, text("Nubank com 200 dólares"));

        lastKeyboard("🧐 Nubank · US$ 200,00 — está certo?");
    }

    @Test
    void nameAnswerWithOnlyBalanceKeepsAskingTheName() {
        atAccountStep();
        parserReturns("100 reais", null, CurrencyEnum.BRL, "100");
        service.handleMessage(OWNER_ID, CHAT_ID, text("100 reais"));
        Mockito.reset(telegramApiClient);

        parserReturns("não, 300 reais", null, CurrencyEnum.BRL, "300");
        service.handleMessage(OWNER_ID, CHAT_ID, text("não, 300 reais"));

        Mockito.verify(telegramApiClient).sendMessage(CHAT_ID, pt("onboarding.account.askName"));
        Assertions.assertEquals(OnboardingStepEnum.ACCOUNT, owner.getOnboardingStep());
        Assertions.assertTrue(owner.getOnboardingDraft().contains("300"), owner.getOnboardingDraft());
    }

    @Test
    void transcriptionAndParsingHappenBeforeTheLock() throws Exception {
        atAccountStep();
        Mockito.when(voiceTranscriptionService.transcribe("voice-1")).thenReturn("Nubank, 50 mil reais");
        parserReturns("Nubank, 50 mil reais", "Nubank", CurrencyEnum.BRL, "50000");

        service.handleMessage(OWNER_ID, CHAT_ID, new OnboardingService.Input(null, "voice-1", "pt-br"));

        InOrder order = Mockito.inOrder(voiceTranscriptionService, accountMessageParser, ownerRepository);
        order.verify(voiceTranscriptionService).transcribe("voice-1");
        order.verify(accountMessageParser).parse("Nubank, 50 mil reais");
        order.verify(ownerRepository).findByIdForUpdate(OWNER_ID);
    }

    @Test
    void stepChangedWhilePreparingRepeatsTheCurrentQuestion() {
        atConfirmStep();
        OwnerEntity staleSnapshot = new OwnerEntity();
        staleSnapshot.setOwnerId(OWNER_ID);
        staleSnapshot.setOnboardingStep(OnboardingStepEnum.ACCOUNT);
        Mockito.when(ownerRepository.findById(OWNER_ID)).thenReturn(Optional.of(staleSnapshot));
        parserReturns("Wise 10 euros", "Wise", CurrencyEnum.EUR, "10");
        Mockito.reset(telegramApiClient);

        service.handleMessage(OWNER_ID, CHAT_ID, text("Wise 10 euros"));

        Assertions.assertEquals(OnboardingStepEnum.ACCOUNT_CONFIRM, owner.getOnboardingStep());
        lastKeyboard("🧐 Nubank · R$ 50.000,00 — está certo?");
    }

    @Test
    void fieldChangedWhilePreparingRepeatsTheCurrentQuestion() {
        atAccountStep();
        parserReturns("Wise 2000", "Wise", null, "2000");
        service.handleMessage(OWNER_ID, CHAT_ID, text("Wise 2000"));
        OwnerEntity staleSnapshot = new OwnerEntity();
        staleSnapshot.setOwnerId(OWNER_ID);
        staleSnapshot.setOnboardingStep(OnboardingStepEnum.ACCOUNT);
        Mockito.when(ownerRepository.findById(OWNER_ID)).thenReturn(Optional.of(staleSnapshot));
        parserReturns("Revolut", "Revolut", null, null);
        Mockito.reset(telegramApiClient);

        service.handleMessage(OWNER_ID, CHAT_ID, text("Revolut"));

        lastKeyboard(pt("onboarding.account.askCurrency", "Wise"));
    }

    //---- ACCOUNT_CONFIRM

    @Test
    void lostRaceOnTheUniqueNameIndexAsksForAnotherName() {
        atConfirmStep();
        Mockito.when(accountService.addAccount(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.anyInt()))
                .thenThrow(new DuplicateAccountNameException("Nubank"));

        service.handleCallback(OWNER_ID, CHAT_ID, "ok", "pt-br");

        Mockito.verify(telegramApiClient).sendMessage(CHAT_ID, pt("onboarding.account.duplicate", "Nubank"));
        Assertions.assertEquals(OnboardingStepEnum.ACCOUNT, owner.getOnboardingStep());
        Assertions.assertTrue(owner.getOnboardingDraft().contains("\"awaiting\":\"NAME\""), owner.getOnboardingDraft());
    }

    @Test
    void confirmationSavesTheAccountWithInitialBalanceOnce() {
        atConfirmStep();

        service.handleCallback(OWNER_ID, CHAT_ID, "ok", "pt-br");
        service.handleCallback(OWNER_ID, CHAT_ID, "ok", "pt-br");

        Mockito.verify(accountService, Mockito.times(1)).addAccount("Nubank", new BigDecimal("50000.00"), CurrencyEnum.BRL, OWNER_ID);
        Mockito.verify(telegramApiClient).sendMessage(CHAT_ID, pt("onboarding.confirm.saved", "Nubank", "R$ 50.000,00"));
        Assertions.assertEquals(OnboardingStepEnum.MORE_ACCOUNTS, owner.getOnboardingStep());
        Assertions.assertNull(owner.getOnboardingDraft());
        List<List<TelegramInlineButtonDTO>> keyboard = lastKeyboard(pt("onboarding.more.question"));
        Assertions.assertEquals("onb:more", keyboard.getFirst().get(0).getCallbackData());
        Assertions.assertEquals("onb:next", keyboard.getFirst().get(1).getCallbackData());
    }

    @Test
    void confirmationCanBeTyped() {
        atConfirmStep();

        service.handleMessage(OWNER_ID, CHAT_ID, text("Sim!"));

        Mockito.verify(accountService).addAccount("Nubank", new BigDecimal("50000.00"), CurrencyEnum.BRL, OWNER_ID);
    }

    @Test
    void anythingElseAtConfirmationRepeatsTheQuestion() {
        atConfirmStep();
        Mockito.reset(telegramApiClient);

        service.handleMessage(OWNER_ID, CHAT_ID, text("hmm"));

        lastKeyboard("🧐 Nubank · R$ 50.000,00 — está certo?");
        Assertions.assertEquals(OnboardingStepEnum.ACCOUNT_CONFIRM, owner.getOnboardingStep());
    }

    @Test
    void fixDiscardsTheDraftAndAsksAgain() {
        atConfirmStep();

        service.handleCallback(OWNER_ID, CHAT_ID, "fix", "pt-br");

        Assertions.assertEquals(OnboardingStepEnum.ACCOUNT, owner.getOnboardingStep());
        Assertions.assertNull(owner.getOnboardingDraft());
        Mockito.verify(telegramApiClient).sendMessage(CHAT_ID, pt("onboarding.account.question"));
        Mockito.verify(accountService, Mockito.never()).addAccount(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.anyInt());
    }

    @Test
    void accountCreatedMeanwhileWithSameNameIsNotDuplicated() {
        atConfirmStep();
        Mockito.when(accountService.existsByDescription("Nubank", OWNER_ID)).thenReturn(true);

        service.handleCallback(OWNER_ID, CHAT_ID, "ok", "pt-br");

        Mockito.verify(accountService, Mockito.never()).addAccount(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.anyInt());
        Mockito.verify(telegramApiClient).sendMessage(CHAT_ID, pt("onboarding.account.duplicate", "Nubank"));
        Assertions.assertEquals(OnboardingStepEnum.ACCOUNT, owner.getOnboardingStep());
    }

    //---- MORE_ACCOUNTS

    @Test
    void anotherAccountGoesBackToAccountStep() {
        owner.setLanguage("pt");
        owner.setOnboardingStep(OnboardingStepEnum.MORE_ACCOUNTS);

        service.handleCallback(OWNER_ID, CHAT_ID, "more", "pt-br");

        Assertions.assertEquals(OnboardingStepEnum.ACCOUNT, owner.getOnboardingStep());
        Mockito.verify(telegramApiClient).sendMessage(CHAT_ID, pt("onboarding.account.next"));
    }

    @Test
    void continuePresentsCategoriesAndTheTutorial() {
        owner.setLanguage("pt");
        owner.setOnboardingStep(OnboardingStepEnum.MORE_ACCOUNTS);

        service.handleMessage(OWNER_ID, CHAT_ID, text("continuar"));

        Assertions.assertEquals(OnboardingStepEnum.TUTORIAL, owner.getOnboardingStep());
        Mockito.verify(telegramApiClient).sendMessage(CHAT_ID, pt("onboarding.categories", "Mercado, Casa", "Salário"));
        Mockito.verify(telegramApiClient).sendMessage(CHAT_ID, pt("onboarding.tutorial", "Nubank"));
    }

    @Test
    void anythingElseAtMoreAccountsRepeatsTheQuestion() {
        owner.setLanguage("pt");
        owner.setOnboardingStep(OnboardingStepEnum.MORE_ACCOUNTS);

        service.handleMessage(OWNER_ID, CHAT_ID, text("talvez"));

        Assertions.assertEquals(OnboardingStepEnum.MORE_ACCOUNTS, owner.getOnboardingStep());
        lastKeyboard(pt("onboarding.more.question"));
    }

    //---- TUTORIAL / DONE

    @Test
    void tutorialLetsVoiceAndCommandsThroughToTheRouter() {
        owner.setLanguage("pt");
        owner.setOnboardingStep(OnboardingStepEnum.TUTORIAL);

        Assertions.assertFalse(service.handleMessage(OWNER_ID, CHAT_ID, new OnboardingService.Input(null, "voice-1", "pt-br")));
        Assertions.assertFalse(service.handleMessage(OWNER_ID, CHAT_ID, text("/nova_categoria Academia")));
        Assertions.assertTrue(service.handleMessage(OWNER_ID, CHAT_ID, text("oi")));
        Mockito.verify(telegramApiClient).sendMessage(CHAT_ID, pt("onboarding.tutorial", "Nubank"));
    }

    @Test
    void firstTransactionFinishesOnboardingWithDashboardLink() {
        owner.setLanguage("pt");
        owner.setOnboardingStep(OnboardingStepEnum.TUTORIAL);

        service.completeTutorial(OWNER_ID, CHAT_ID, "pt-br");

        Assertions.assertEquals(OnboardingStepEnum.DONE, owner.getOnboardingStep());
        List<List<TelegramInlineButtonDTO>> keyboard = lastKeyboard(pt("onboarding.done", DASHBOARD));
        Assertions.assertEquals(DASHBOARD, keyboard.getFirst().getFirst().getUrl());
    }

    @Test
    void completeTutorialDoesNothingOutsideTheTutorial() {
        owner.setOnboardingStep(OnboardingStepEnum.DONE);

        service.completeTutorial(OWNER_ID, CHAT_ID, "pt-br");

        Mockito.verifyNoInteractions(telegramApiClient);
    }

    @Test
    void doneOwnerIsNotHandled() {
        owner.setOnboardingStep(OnboardingStepEnum.DONE);

        Assertions.assertFalse(service.handleMessage(OWNER_ID, CHAT_ID, text("oi")));
        service.resume(OWNER_ID, CHAT_ID, "pt-br");

        Mockito.verifyNoInteractions(telegramApiClient);
    }

    //---- Commands at any step, resume

    @Test
    void helpWorksAtAnyStepAndRepeatsTheQuestion() {
        atConfirmStep();
        Mockito.reset(telegramApiClient);

        Assertions.assertTrue(service.handleMessage(OWNER_ID, CHAT_ID, text("/ajuda")));

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        Mockito.verify(telegramApiClient).sendMessage(ArgumentMatchers.eq(CHAT_ID), text.capture());
        Assertions.assertTrue(text.getValue().startsWith("🤖 Comandos disponíveis"));
        lastKeyboard("🧐 Nubank · R$ 50.000,00 — está certo?");
    }

    @Test
    void cancelLeavesOnboardingAndKeepsWhatWasSaved() {
        atConfirmStep();

        service.handleMessage(OWNER_ID, CHAT_ID, text("/cancelar"));

        Assertions.assertEquals(OnboardingStepEnum.DONE, owner.getOnboardingStep());
        Assertions.assertNull(owner.getOnboardingDraft());
        Mockito.verify(telegramApiClient).sendMessage(CHAT_ID, pt("onboarding.cancelled"));
        Mockito.verify(accountService, Mockito.never()).addAccount(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.anyInt());
    }

    @Test
    void cancelBeforeChoosingLanguageStillSeedsCategories() {
        service.handleMessage(OWNER_ID, CHAT_ID, new OnboardingService.Input("/cancelar", null, "en"));

        Assertions.assertEquals(OnboardingStepEnum.DONE, owner.getOnboardingStep());
        Assertions.assertEquals("en", owner.getLanguage());
        Mockito.verify(categoryService).seedBaseCategories(OWNER_ID, LanguageEnum.EN);
        Mockito.verify(telegramApiClient).sendMessage(CHAT_ID, en("onboarding.cancelled"));
    }

    @Test
    void otherCommandsAskToFinishFirst() {
        atAccountStep();

        Assertions.assertTrue(service.handleMessage(OWNER_ID, CHAT_ID, text("/contas")));

        Mockito.verify(telegramApiClient).sendMessage(CHAT_ID, pt("onboarding.finishFirst"));
        Mockito.verify(telegramApiClient).sendMessage(CHAT_ID, pt("onboarding.account.question"));
    }

    @Test
    void resumeRepeatsTheCurrentQuestion() {
        atAccountStep();
        parserReturns("Itaú reais", "Itaú", CurrencyEnum.BRL, null);
        service.handleMessage(OWNER_ID, CHAT_ID, text("Itaú reais"));
        Mockito.reset(telegramApiClient);

        service.resume(OWNER_ID, CHAT_ID, "pt-br");

        Mockito.verify(telegramApiClient).sendMessage(CHAT_ID, pt("onboarding.account.askBalance", "Itaú"));
    }

    //---- Helpers

    private void atAccountStep() {
        service.handleCallback(OWNER_ID, CHAT_ID, "lang:pt", "pt-br");
        Mockito.reset(telegramApiClient);
    }

    private void atConfirmStep() {
        atAccountStep();
        parserReturns("Nubank, 50 mil reais", "Nubank", CurrencyEnum.BRL, "50000");
        service.handleMessage(OWNER_ID, CHAT_ID, text("Nubank, 50 mil reais"));
        Assertions.assertEquals(OnboardingStepEnum.ACCOUNT_CONFIRM, owner.getOnboardingStep());
    }

    private void parserReturns(String input, String name, CurrencyEnum currency, String balance) {
        Mockito.when(accountMessageParser.parse(input))
                .thenReturn(new AccountMessageParser.ParsedAccount(name, currency, balance == null ? null : new BigDecimal(balance)));
    }

    private static OnboardingService.Input text(String text) {
        return new OnboardingService.Input(text, null, "pt-br");
    }

    @SuppressWarnings("unchecked")
    private List<List<TelegramInlineButtonDTO>> lastKeyboard(String expectedText) {
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<List<List<TelegramInlineButtonDTO>>> keyboard = ArgumentCaptor.forClass(List.class);
        Mockito.verify(telegramApiClient, Mockito.atLeastOnce()).sendMessageWithInlineKeyboard(ArgumentMatchers.eq(CHAT_ID), text.capture(), keyboard.capture());
        if (expectedText != null)
            Assertions.assertEquals(expectedText, text.getValue());
        return keyboard.getValue();
    }

    private String pt(String key, Object... args) {
        return messages.get(LanguageEnum.PT, key, args);
    }

    private String en(String key, Object... args) {
        return messages.get(LanguageEnum.EN, key, args);
    }
}
