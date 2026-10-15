package com.pctheone.money_flow.services;

import com.pctheone.money_flow.dto.AccountDTO;
import com.pctheone.money_flow.dto.CategoryDTO;
import com.pctheone.money_flow.dto.OnboardingAccountDraftDTO;
import com.pctheone.money_flow.dto.TelegramInlineButtonDTO;
import com.pctheone.money_flow.entities.OwnerEntity;
import com.pctheone.money_flow.enums.CategoryTypeEnum;
import com.pctheone.money_flow.enums.CurrencyEnum;
import com.pctheone.money_flow.enums.LanguageEnum;
import com.pctheone.money_flow.enums.OnboardingStepEnum;
import com.pctheone.money_flow.repositories.OwnerRepository;
import com.pctheone.money_flow.services.telegram.AccountMessageParser;
import com.pctheone.money_flow.services.telegram.BotMessages;
import com.pctheone.money_flow.services.telegram.TelegramBotCommands;
import com.pctheone.money_flow.services.telegram.TelegramCallbackRegistry;
import com.pctheone.money_flow.services.telegram.TelegramReplySender;
import com.pctheone.money_flow.utils.AmountParser;
import com.pctheone.money_flow.utils.CurrencyParser;
import com.pctheone.money_flow.utils.MoneyFormat;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

//---- Onboarding steps: LANGUAGE -> ACCOUNT <-> ACCOUNT_CONFIRM -> MORE_ACCOUNTS (-> ACCOUNT) -> TUTORIAL -> DONE.
//---- Every public method is one short transaction holding the owner row lock (double clicks run one after the other).
//---- No slow I/O here: transcription and GPT parsing are done by OnboardingService before calling in.
//---- Replies go through TelegramReplySender, so they are sent only after the transaction commits.
@Service
public class OnboardingStateMachine {

    private static final Logger log = LoggerFactory.getLogger(OnboardingStateMachine.class);

    static final String CALLBACK_PREFIX = "onb";

    private static final Set<String> YES_WORDS = Set.of("sim", "s", "yes", "y", "ok", "isso", "certo", "correct");
    private static final Set<String> FIX_WORDS = Set.of("corrigir", "fix", "nao", "não", "no", "n", "errado", "wrong");
    private static final Set<String> CONTINUE_WORDS = Set.of("continuar", "continue", "nao", "não", "no", "n", "next");

    //---- Result of the slow work done before locking: text of the account message (typed or transcribed) and,
    //---- when needed for the field being asked, the parsed account. Applied only if the step/field did not change meanwhile.
    public record PreparedAccountInput(OnboardingAccountDraftDTO.Field awaiting, String text, AccountMessageParser.ParsedAccount parsed) {
    }

    @Value("${app.dashboard-url:https://moneyflow.pctheone.com}")
    String dashboardUrl;

    @Autowired
    OwnerRepository ownerRepository;

    @Autowired
    AccountService accountService;

    @Autowired
    CategoryService categoryService;

    @Autowired
    TelegramReplySender replySender;

    @Autowired
    BotMessages messages;

    //---- Sends the current step's question again (after /start or a re-link). Does nothing once DONE.
    @Transactional
    public void resume(Integer ownerId, Long chatId, String telegramLanguageCode) {
        OwnerEntity owner = lockOwner(ownerId);
        if (owner.getOnboardingStep() == OnboardingStepEnum.DONE)
            return;
        log.info("Resuming onboarding of owner {} at step {}", ownerId, owner.getOnboardingStep());
        askCurrentStep(owner, chatId, language(owner, telegramLanguageCode));
    }

    //---- Returns false when the message must go through the normal router (DONE, or a command/voice during TUTORIAL).
    @Transactional
    public boolean handleMessage(Integer ownerId, Long chatId, OnboardingService.Input input, PreparedAccountInput prepared) {
        OwnerEntity owner = lockOwner(ownerId);
        OnboardingStepEnum step = owner.getOnboardingStep();
        if (step == OnboardingStepEnum.DONE)
            return false;

        LanguageEnum language = language(owner, input.telegramLanguageCode());
        String text = input.text() == null ? null : input.text().trim();

        if (OnboardingService.isCommand(text)) {
            String command = TelegramBotServices.normalizeCommand(text.split("\\s+")[0]);
            switch (command) {
                case "/ajuda", "/help" -> {
                    replySender.send(chatId, TelegramBotCommands.helpText(language.code()));
                    askCurrentStep(owner, chatId, language);
                    return true;
                }
                case "/cancelar", "/cancel" -> {
                    cancel(owner, chatId, language);
                    return true;
                }
                default -> {
                    //---- During the tutorial every command works, e.g. /nova_categoria from the categories message.
                    if (step == OnboardingStepEnum.TUTORIAL)
                        return false;
                    replySender.send(chatId, messages.get(language, "onboarding.finishFirst"));
                    askCurrentStep(owner, chatId, language);
                    return true;
                }
            }
        }

        log.info("Onboarding message from owner {} at step {}", ownerId, step);
        switch (step) {
            case LANGUAGE -> {
                LanguageEnum chosen = languageFromText(text);
                if (chosen == null)
                    askCurrentStep(owner, chatId, language);
                else
                    chooseLanguage(owner, chatId, chosen);
            }
            case ACCOUNT -> {
                //---- Prepared for another step/field (state changed while transcribing/parsing): just ask again.
                if (prepared == null || prepared.awaiting() != OnboardingDrafts.read(owner).getAwaiting())
                    askCurrentStep(owner, chatId, language);
                else
                    handleAccountInput(owner, chatId, language, prepared);
            }
            case ACCOUNT_CONFIRM -> {
                String word = normalizeWord(text);
                if (YES_WORDS.contains(word))
                    confirmAccount(owner, chatId, language);
                else if (FIX_WORDS.contains(word))
                    fixAccount(owner, chatId, language);
                else
                    askCurrentStep(owner, chatId, language);
            }
            case MORE_ACCOUNTS -> {
                String word = normalizeWord(text);
                if (YES_WORDS.contains(word))
                    addAnotherAccount(owner, chatId, language);
                else if (CONTINUE_WORDS.contains(word))
                    startTutorial(owner, chatId, language);
                else
                    askCurrentStep(owner, chatId, language);
            }
            case TUTORIAL -> {
                //---- The first voice message is a normal transaction; the router calls completeTutorial on success.
                if (input.voiceFileId() != null)
                    return false;
                askCurrentStep(owner, chatId, language);
            }
            default -> {
                return false;
            }
        }
        ownerRepository.save(owner);
        return true;
    }

    //---- Inline button clicks: "onb:lang:pt", "onb:cur:BRL", "onb:ok", "onb:fix", "onb:more", "onb:next".
    //---- A button from an earlier step (stale or double click) is ignored.
    @Transactional
    public void handleCallback(Integer ownerId, Long chatId, String payload, String telegramLanguageCode) {
        OwnerEntity owner = lockOwner(ownerId);
        OnboardingStepEnum step = owner.getOnboardingStep();
        LanguageEnum language = language(owner, telegramLanguageCode);
        String[] parts = payload.split(":", 2);
        String action = parts[0];
        String argument = parts.length > 1 ? parts[1] : "";

        boolean handled = switch (action) {
            case "lang" -> {
                LanguageEnum chosen = LanguageEnum.fromCode(argument);
                if (step != OnboardingStepEnum.LANGUAGE || chosen == null)
                    yield false;
                chooseLanguage(owner, chatId, chosen);
                yield true;
            }
            case "cur" -> {
                OnboardingAccountDraftDTO draft = OnboardingDrafts.read(owner);
                CurrencyEnum currency = parseCurrencyCode(argument);
                if (step != OnboardingStepEnum.ACCOUNT || draft.getAwaiting() != OnboardingAccountDraftDTO.Field.CURRENCY || currency == null)
                    yield false;
                draft.setCurrency(currency);
                advanceAccount(owner, chatId, language, draft);
                yield true;
            }
            case "ok" -> {
                if (step != OnboardingStepEnum.ACCOUNT_CONFIRM)
                    yield false;
                confirmAccount(owner, chatId, language);
                yield true;
            }
            case "fix" -> {
                if (step != OnboardingStepEnum.ACCOUNT_CONFIRM)
                    yield false;
                fixAccount(owner, chatId, language);
                yield true;
            }
            case "more" -> {
                if (step != OnboardingStepEnum.MORE_ACCOUNTS)
                    yield false;
                addAnotherAccount(owner, chatId, language);
                yield true;
            }
            case "next" -> {
                if (step != OnboardingStepEnum.MORE_ACCOUNTS)
                    yield false;
                startTutorial(owner, chatId, language);
                yield true;
            }
            default -> false;
        };

        if (!handled) {
            log.info("Ignoring stale onboarding button '{}' from owner {} at step {}", payload, ownerId, step);
            return;
        }
        ownerRepository.save(owner);
    }

    //---- Called by the router after a transaction was registered. Finishes onboarding if the owner was in the tutorial.
    @Transactional
    public void completeTutorial(Integer ownerId, Long chatId, String telegramLanguageCode) {
        OwnerEntity owner = lockOwner(ownerId);
        if (owner.getOnboardingStep() != OnboardingStepEnum.TUTORIAL)
            return;
        LanguageEnum language = language(owner, telegramLanguageCode);
        owner.setOnboardingStep(OnboardingStepEnum.DONE);
        ownerRepository.save(owner);
        log.info("Onboarding finished for owner {}", ownerId);
        replySender.sendWithKeyboard(chatId, messages.get(language, "onboarding.done", dashboardUrl),
                List.of(List.of(TelegramInlineButtonDTO.url(messages.get(language, "onboarding.dashboardButton"), dashboardUrl))));
    }

    //---- The account insert hit the unique name index (a concurrent insert won the race). The failed transaction was
    //---- rolled back; this one asks for another name.
    @Transactional
    public void rejectDuplicateName(Integer ownerId, Long chatId, String telegramLanguageCode, String name) {
        OwnerEntity owner = lockOwner(ownerId);
        if (owner.getOnboardingStep() != OnboardingStepEnum.ACCOUNT && owner.getOnboardingStep() != OnboardingStepEnum.ACCOUNT_CONFIRM)
            return;
        LanguageEnum language = language(owner, telegramLanguageCode);
        OnboardingAccountDraftDTO draft = OnboardingDrafts.read(owner);
        replySender.send(chatId, messages.get(language, "onboarding.account.duplicate", name));
        draft.setName(null);
        draft.setAwaiting(OnboardingAccountDraftDTO.Field.NAME);
        owner.setOnboardingStep(OnboardingStepEnum.ACCOUNT);
        OnboardingDrafts.write(owner, draft);
        ownerRepository.save(owner);
    }

    //---- ---------------------------------------------------------------- Steps

    private void chooseLanguage(OwnerEntity owner, Long chatId, LanguageEnum chosen) {
        owner.setLanguage(chosen.code());
        categoryService.seedBaseCategories(owner.getOwnerId(), chosen);
        owner.setOnboardingStep(OnboardingStepEnum.ACCOUNT);
        owner.setOnboardingDraft(null);
        log.info("Owner {} chose language {}", owner.getOwnerId(), chosen.code());
        replySender.send(chatId, messages.get(chosen, "onboarding.account.question"));
    }

    private void handleAccountInput(OwnerEntity owner, Long chatId, LanguageEnum language, PreparedAccountInput prepared) {
        String input = prepared.text() == null ? "" : prepared.text().trim();
        if (input.isEmpty()) {
            askCurrentStep(owner, chatId, language);
            return;
        }

        OnboardingAccountDraftDTO draft = OnboardingDrafts.read(owner);
        AccountMessageParser.ParsedAccount parsed = prepared.parsed();
        OnboardingAccountDraftDTO.Field awaiting = draft.getAwaiting();
        if (awaiting == null) {
            if (parsed == null || parsed.isEmpty()) {
                replySender.send(chatId, messages.get(language, "onboarding.account.notUnderstood") + "\n"
                        + messages.get(language, "onboarding.account.question"));
                return;
            }
            fill(draft, parsed);
        } else {
            switch (awaiting) {
                case NAME -> {
                    //---- "Nubank, 500 reais" as the answer: take only the name from it, plus the other fields.
                    if (parsed != null && (parsed.name() != null || parsed.balance() != null || parsed.currency() != null)) {
                        fill(draft, parsed);
                    } else {
                        String name = AccountMessageParser.cleanName(input);
                        if (name == null) {
                            askCurrentStep(owner, chatId, language);
                            return;
                        }
                        draft.setName(name);
                    }
                }
                case BALANCE -> {
                    BigDecimal balance = AmountParser.parse(input)
                            .orElse(parsed == null ? null : parsed.balance());
                    if (balance == null) {
                        replySender.send(chatId, messages.get(language, "onboarding.account.invalidBalance"));
                        return;
                    }
                    draft.setBalance(balance);
                }
                case CURRENCY -> {
                    CurrencyEnum currency = CurrencyParser.detect(input).orElse(parseCurrencyCode(input));
                    if (currency == null) {
                        askCurrentStep(owner, chatId, language);
                        return;
                    }
                    draft.setCurrency(currency);
                }
            }
        }
        advanceAccount(owner, chatId, language, draft);
    }

    private static void fill(OnboardingAccountDraftDTO draft, AccountMessageParser.ParsedAccount parsed) {
        if (parsed.name() != null)
            draft.setName(parsed.name());
        if (parsed.currency() != null)
            draft.setCurrency(parsed.currency());
        if (parsed.balance() != null)
            draft.setBalance(parsed.balance());
    }

    //---- Asks for the first missing field (name -> balance -> currency), or moves to the confirmation.
    private void advanceAccount(OwnerEntity owner, Long chatId, LanguageEnum language, OnboardingAccountDraftDTO draft) {
        if (draft.getName() != null && accountService.existsByDescription(draft.getName(), owner.getOwnerId())) {
            replySender.send(chatId, messages.get(language, "onboarding.account.duplicate", draft.getName()));
            draft.setName(null);
            draft.setAwaiting(OnboardingAccountDraftDTO.Field.NAME);
            owner.setOnboardingStep(OnboardingStepEnum.ACCOUNT);
            OnboardingDrafts.write(owner, draft);
            return;
        }

        if (draft.getName() == null)
            draft.setAwaiting(OnboardingAccountDraftDTO.Field.NAME);
        else if (draft.getBalance() == null)
            draft.setAwaiting(OnboardingAccountDraftDTO.Field.BALANCE);
        else if (draft.getCurrency() == null)
            draft.setAwaiting(OnboardingAccountDraftDTO.Field.CURRENCY);
        else
            draft.setAwaiting(null);

        owner.setOnboardingStep(draft.isComplete() ? OnboardingStepEnum.ACCOUNT_CONFIRM : OnboardingStepEnum.ACCOUNT);
        OnboardingDrafts.write(owner, draft);
        askCurrentStep(owner, chatId, language);
    }

    //---- The initial balance goes straight into account.balance: it is not a transaction.
    //---- A DuplicateAccountNameException (lost race on the unique index) propagates: OnboardingService retries via rejectDuplicateName.
    private void confirmAccount(OwnerEntity owner, Long chatId, LanguageEnum language) {
        OnboardingAccountDraftDTO draft = OnboardingDrafts.read(owner);
        if (!draft.isComplete() || accountService.existsByDescription(draft.getName(), owner.getOwnerId())) {
            advanceAccount(owner, chatId, language, draft);
            return;
        }

        BigDecimal balance = draft.getBalance().setScale(2, RoundingMode.HALF_UP);
        AccountDTO account = accountService.addAccount(draft.getName(), balance, draft.getCurrency(), owner.getOwnerId());
        log.info("Onboarding account {} created for owner {}", account.getAccountId(), owner.getOwnerId());

        owner.setOnboardingDraft(null);
        owner.setOnboardingStep(OnboardingStepEnum.MORE_ACCOUNTS);
        replySender.send(chatId, messages.get(language, "onboarding.confirm.saved", account.getDescription(), MoneyFormat.format(balance, draft.getCurrency())));
        askCurrentStep(owner, chatId, language);
    }

    private void fixAccount(OwnerEntity owner, Long chatId, LanguageEnum language) {
        owner.setOnboardingDraft(null);
        owner.setOnboardingStep(OnboardingStepEnum.ACCOUNT);
        replySender.send(chatId, messages.get(language, "onboarding.account.question"));
    }

    private void addAnotherAccount(OwnerEntity owner, Long chatId, LanguageEnum language) {
        owner.setOnboardingDraft(null);
        owner.setOnboardingStep(OnboardingStepEnum.ACCOUNT);
        replySender.send(chatId, messages.get(language, "onboarding.account.next"));
    }

    private void startTutorial(OwnerEntity owner, Long chatId, LanguageEnum language) {
        owner.setOnboardingStep(OnboardingStepEnum.TUTORIAL);
        List<CategoryDTO> categories = categoryService.listAllCategories(owner.getOwnerId());
        replySender.send(chatId, messages.get(language, "onboarding.categories",
                categoryNames(categories, CategoryTypeEnum.EXPENSE), categoryNames(categories, CategoryTypeEnum.INCOME)));
        askCurrentStep(owner, chatId, language);
    }

    //---- Leaves the guided flow; whatever was already confirmed is kept. Categories are seeded if the language was never chosen.
    private void cancel(OwnerEntity owner, Long chatId, LanguageEnum language) {
        if (owner.getLanguage() == null) {
            owner.setLanguage(language.code());
            categoryService.seedBaseCategories(owner.getOwnerId(), language);
        }
        owner.setOnboardingDraft(null);
        owner.setOnboardingStep(OnboardingStepEnum.DONE);
        ownerRepository.save(owner);
        log.info("Onboarding cancelled by owner {}", owner.getOwnerId());
        replySender.send(chatId, messages.get(language, "onboarding.cancelled"));
    }

    //---- ---------------------------------------------------------------- Questions

    private void askCurrentStep(OwnerEntity owner, Long chatId, LanguageEnum language) {
        switch (owner.getOnboardingStep()) {
            case LANGUAGE -> askLanguage(chatId, language);
            case ACCOUNT -> askAccountField(owner, chatId, language);
            case ACCOUNT_CONFIRM -> {
                OnboardingAccountDraftDTO draft = OnboardingDrafts.read(owner);
                if (!draft.isComplete()) {
                    owner.setOnboardingStep(OnboardingStepEnum.ACCOUNT);
                    askAccountField(owner, chatId, language);
                    return;
                }
                replySender.sendWithKeyboard(chatId,
                        messages.get(language, "onboarding.confirm.question", draft.getName(), MoneyFormat.format(draft.getBalance(), draft.getCurrency())),
                        List.of(List.of(
                                button(messages.get(language, "onboarding.confirm.yes"), "ok"),
                                button(messages.get(language, "onboarding.confirm.fix"), "fix"))));
            }
            case MORE_ACCOUNTS -> replySender.sendWithKeyboard(chatId, messages.get(language, "onboarding.more.question"),
                    List.of(List.of(
                            button(messages.get(language, "onboarding.more.yes"), "more"),
                            button(messages.get(language, "onboarding.more.no"), "next"))));
            case TUTORIAL -> replySender.send(chatId, messages.get(language, "onboarding.tutorial", tutorialAccountName(owner)));
            case DONE -> {
            }
        }
    }

    //---- Telegram's language is shown first and used for the question itself.
    private void askLanguage(Long chatId, LanguageEnum detected) {
        TelegramInlineButtonDTO portuguese = button("🇧🇷 Português", "lang:" + LanguageEnum.PT.code());
        TelegramInlineButtonDTO english = button("🇬🇧 English", "lang:" + LanguageEnum.EN.code());
        replySender.sendWithKeyboard(chatId, messages.get(detected, "onboarding.language.question"),
                List.of(detected == LanguageEnum.PT ? List.of(portuguese, english) : List.of(english, portuguese)));
    }

    private void askAccountField(OwnerEntity owner, Long chatId, LanguageEnum language) {
        OnboardingAccountDraftDTO draft = OnboardingDrafts.read(owner);
        if (draft.getAwaiting() == null) {
            replySender.send(chatId, messages.get(language, "onboarding.account.question"));
            return;
        }
        switch (draft.getAwaiting()) {
            case NAME -> replySender.send(chatId, messages.get(language, "onboarding.account.askName"));
            case BALANCE -> replySender.send(chatId, messages.get(language, "onboarding.account.askBalance", draft.getName()));
            case CURRENCY -> replySender.sendWithKeyboard(chatId, messages.get(language, "onboarding.account.askCurrency", draft.getName()),
                    List.of(List.of(
                            button("R$ BRL", "cur:" + CurrencyEnum.BRL.name()),
                            button("€ EUR", "cur:" + CurrencyEnum.EUR.name()),
                            button("US$ USD", "cur:" + CurrencyEnum.USD.name()))));
        }
    }

    //---- ---------------------------------------------------------------- Helpers

    private OwnerEntity lockOwner(Integer ownerId) {
        return ownerRepository.findByIdForUpdate(ownerId)
                .orElseThrow(() -> new IllegalStateException("Owner not found: " + ownerId));
    }

    static LanguageEnum language(OwnerEntity owner, String telegramLanguageCode) {
        return LanguageEnum.resolve(owner.getLanguage(), telegramLanguageCode);
    }

    private String tutorialAccountName(OwnerEntity owner) {
        List<AccountDTO> accounts = accountService.listAllAccounts(owner.getOwnerId());
        return accounts.isEmpty() ? "Nubank" : accounts.getFirst().getDescription();
    }

    private static String categoryNames(List<CategoryDTO> categories, CategoryTypeEnum type) {
        return categories.stream()
                .filter(category -> category.getType() == type)
                .map(CategoryDTO::getCategoryDescription)
                .collect(Collectors.joining(", "));
    }

    static LanguageEnum languageFromText(String text) {
        String word = normalizeWord(text);
        return switch (word) {
            case "pt", "portugues", "português", "portuguese", "br" -> LanguageEnum.PT;
            case "en", "english", "ingles", "inglês" -> LanguageEnum.EN;
            default -> null;
        };
    }

    private static String normalizeWord(String text) {
        if (text == null)
            return "";
        return text.trim().toLowerCase(Locale.ROOT).replaceAll("[\\p{Punct}\\s]+$", "").replaceAll("^[\\p{Punct}\\s]+", "");
    }

    private static CurrencyEnum parseCurrencyCode(String code) {
        try {
            return CurrencyEnum.valueOf(code.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static TelegramInlineButtonDTO button(String text, String payload) {
        return TelegramInlineButtonDTO.callback(text, TelegramCallbackRegistry.callbackData(CALLBACK_PREFIX, payload));
    }
}
