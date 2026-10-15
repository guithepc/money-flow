package com.pctheone.money_flow.services;

import com.pctheone.money_flow.dto.*;
import com.pctheone.money_flow.entities.CategoryEntity;
import com.pctheone.money_flow.entities.OwnerEntity;
import com.pctheone.money_flow.enums.CategoryTypeEnum;
import com.pctheone.money_flow.enums.CurrencyEnum;
import com.pctheone.money_flow.enums.LanguageEnum;
import com.pctheone.money_flow.enums.OnboardingStepEnum;
import com.pctheone.money_flow.enums.OperationTypeEnum;
import com.pctheone.money_flow.exceptions.CategoryNotFoundException;
import com.pctheone.money_flow.exceptions.DuplicateAccountNameException;
import com.pctheone.money_flow.exceptions.InvalidMoneyFormatException;
import com.pctheone.money_flow.exceptions.TelegramUserInputException;
import com.pctheone.money_flow.repositories.OwnerRepository;
import com.pctheone.money_flow.services.telegram.BotMessages;
import com.pctheone.money_flow.services.telegram.CategoryCallbackHandler;
import com.pctheone.money_flow.services.telegram.OpenAITextClient;
import com.pctheone.money_flow.services.telegram.TelegramApiClient;
import com.pctheone.money_flow.services.telegram.TelegramBotCommands;
import com.pctheone.money_flow.services.telegram.TelegramCallbackHandler;
import com.pctheone.money_flow.services.telegram.TelegramCallbackRegistry;
import com.pctheone.money_flow.services.telegram.TelegramProcessedUpdateService;
import com.pctheone.money_flow.services.telegram.VoiceTranscriptionService;
import com.pctheone.money_flow.utils.BotMessageFormat;
import com.pctheone.money_flow.utils.Json;
import com.pctheone.money_flow.utils.MoneyFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class TelegramBotServices {

    private static final Logger log = LoggerFactory.getLogger(TelegramBotServices.class);

    //---- Message keys in messages/bot_*.properties.
    static final String CATEGORY_NOT_FOUND_MESSAGE = "error.categoryNotFound";
    static final String INVALID_AMOUNT_MESSAGE = "error.invalidAmount";
    static final String ZERO_AMOUNT_MESSAGE = "error.zeroAmount";
    static final String INVALID_ACCOUNT_MESSAGE = "error.invalidAccount";
    static final String INVALID_TRANSACTION_MESSAGE = "error.invalidTransaction";
    static final String NOT_ALLOWED_MESSAGE = "error.notAllowed";
    static final String GENERIC_ERROR_MESSAGE = "error.generic";
    static final String VOICE_ERROR_MESSAGE = "error.voice";
    static final String NOT_LINKED_MESSAGE = "error.notLinked";

    @Value("classpath:prompts/voice-parser.txt")
    Resource voiceParserPrompt;

    @Autowired
    TelegramApiClient telegramApiClient;

    @Autowired
    TelegramProcessedUpdateService processedUpdateService;

    @Autowired
    TelegramCallbackRegistry callbackRegistry;

    @Autowired
    OpenAITextClient openAITextClient;

    @Autowired
    VoiceTranscriptionService voiceTranscriptionService;

    @Autowired
    TransactionsService transactionsService;

    @Autowired
    CategoryService categoryService;

    @Autowired
    AccountService accountService;

    @Autowired
    OwnerRepository ownerRepository;

    @Autowired
    OwnerService ownerService;

    @Autowired
    OnboardingService onboardingService;

    @Autowired
    BotMessages messages;

    public void telegramRouter(Update update){
        try {
            //---- Claimed before processing: a retried update never registers twice, but a failure after the claim is not retried.
            if (update.getUpdateId() != null && !processedUpdateService.markAsProcessed(update.getUpdateId())) {
                log.info("Duplicate update {} skipped.", update.getUpdateId());
                return;
            }

            if (update.hasCallbackQuery()) {
                handleCallbackQuery(update.getCallbackQuery());
                return;
            }

            if (!update.hasMessage()) {
                log.info("No message in update.");
                return;
            }

            routeMessage(update);
        } catch (Exception e) {
            Long chatId = chatIdOf(update);
            if (chatId == null) {
                log.error("Failed to process Telegram update {}: {}", update.getUpdateId(), e.getMessage(), e);
                return;
            }
            handleError(chatId, languageOf(chatId, telegramLanguageOf(update)), e, GENERIC_ERROR_MESSAGE);
            //---- handleCallbackQuery answers by itself; reaching here means it never ran (e.g. the claim failed).
            if (update.hasCallbackQuery())
                telegramApiClient.answerCallbackQuery(update.getCallbackQuery().getId(), null);
        }
    }

    private static Long chatIdOf(Update update){
        if (update.hasMessage())
            return update.getMessage().getChatId();
        if (update.hasCallbackQuery())
            return callbackChatId(update.getCallbackQuery());
        return null;
    }

    private static Long callbackChatId(CallbackQuery callbackQuery){
        return callbackQuery.getMessage() != null ? callbackQuery.getMessage().getChatId() : callbackQuery.getFrom().getId();
    }

    private static String telegramLanguageOf(Update update){
        User from = null;
        if (update.hasMessage())
            from = update.getMessage().getFrom();
        else if (update.hasCallbackQuery())
            from = update.getCallbackQuery().getFrom();
        return from != null ? from.getLanguageCode() : null;
    }

    //---- Language for error replies: the owner's saved language, else the Telegram client's. Never throws.
    private LanguageEnum languageOf(Long chatId, String telegramLanguageCode){
        try {
            return ownerRepository.findByTelegramChatId(chatId)
                    .map(owner -> LanguageEnum.resolve(owner.getLanguage(), telegramLanguageCode))
                    .orElse(LanguageEnum.fromTelegram(telegramLanguageCode));
        } catch (Exception e) {
            return LanguageEnum.fromTelegram(telegramLanguageCode);
        }
    }

    //---- Sends a message with inline buttons. Each inner list is a row; clicks are routed by TelegramCallbackRegistry.
    public void sendMessageWithInlineKeyboard(Long chatId, String text, List<List<TelegramInlineButtonDTO>> keyboard){
        telegramApiClient.sendMessageWithInlineKeyboard(chatId, text, keyboard);
    }

    private void routeMessage(Update update){
        Message message = update.getMessage();
        Long chatId = message.getChatId();
        String telegramLanguage = message.getFrom() != null ? message.getFrom().getLanguageCode() : null;
        String text = message.getText();
        List<String> command = text == null || text.isBlank() ? List.of() : Arrays.asList(text.trim().split("\\s+"));

        //---- /start <uuid> links the chat, so it runs before owner resolution.
        if (!command.isEmpty() && "/start".equals(normalizeCommand(command.getFirst()))) {
            handleStart(command, chatId, telegramLanguage);
            return;
        }

        Optional<OwnerEntity> owner = ownerRepository.findByTelegramChatId(chatId);
        if (owner.isEmpty()) {
            log.info("Unlinked chatId {} tried to use the bot.", chatId);
            LanguageEnum unlinkedLanguage = LanguageEnum.fromTelegram(telegramLanguage);
            //---- The command list is useful before linking too; the link instructions follow it.
            if (!command.isEmpty() && List.of("/ajuda", "/help").contains(normalizeCommand(command.getFirst())))
                sendReply(chatId, TelegramBotCommands.helpText(unlinkedLanguage.code()));
            sendReply(chatId, messages.get(unlinkedLanguage, NOT_LINKED_MESSAGE));
            return;
        }
        Integer ownerId = owner.get().getOwnerId();
        OnboardingStepEnum onboardingStep = owner.get().getOnboardingStep();
        LanguageEnum language = LanguageEnum.resolve(owner.get().getLanguage(), telegramLanguage);

        //---- While onboarding is not DONE, the onboarding flow gets the message first.
        if (onboardingStep != OnboardingStepEnum.DONE) {
            String voiceFileId = message.getVoice() != null ? message.getVoice().getFileId() : null;
            if (onboardingService.handleMessage(ownerId, chatId, new OnboardingService.Input(text, voiceFileId, telegramLanguage)))
                return;
        }

        boolean transactionRegistered = false;
        if (message.getVoice() != null) {
            transactionRegistered = audioCommand(update, ownerId, language);
        } else if (command.isEmpty()) {
            log.info("No text in message.");
        } else {
            transactionRegistered = routeCommand(command, chatId, ownerId, language);
        }

        //---- The first transaction during the onboarding tutorial finishes onboarding.
        if (transactionRegistered && onboardingStep == OnboardingStepEnum.TUTORIAL)
            onboardingService.completeTutorial(ownerId, chatId, telegramLanguage);
    }

    //---- /gasto mercado carne moida 25,90 1. Returns true when a transaction was registered.
    private boolean routeCommand(List<String> command, Long chatId, Integer ownerId, LanguageEnum language){
        switch (normalizeCommand(command.getFirst())){
            case "/gasto":{
                if (isCommandToShort(command, 5, chatId, language))
                    return false;
                log.info("Expense being registered.");
                TransactionRegistrationResultDTO expense = withAccountErrors(() -> transactionsService.registerExpense(ownerId, command.get(1), parseId(command.getLast(), INVALID_ACCOUNT_MESSAGE), parseTransactionAmount(command.get(command.size() - 2)), transactionDescription(command)));
                log.info("Expense registered successfully, amount:  {} -  Actual balance: {}", expense.getAmount(), expense.getNewBalance());
                sendReply(chatId, BotMessageFormat.transactionConfirmation(messages, language, OperationTypeEnum.EXPENSE, expense));
                return true;
            }
            case "/entrada":{
                if (isCommandToShort(command, 5, chatId, language))
                    return false;
                log.info("Income being registered.");
                TransactionRegistrationResultDTO income = withAccountErrors(() -> transactionsService.registerIncome(ownerId, command.get(1), parseId(command.getLast(), INVALID_ACCOUNT_MESSAGE), parseTransactionAmount(command.get(command.size() - 2)), transactionDescription(command)));
                log.info("Income registered successfully, amount:  {} -  Actual balance: {}", income.getAmount(), income.getNewBalance());
                sendReply(chatId, BotMessageFormat.transactionConfirmation(messages, language, OperationTypeEnum.INCOME, income));
                return true;
            }
            case "/categorias":{
                log.info("Categories requested.");
                List<CategoryDTO> categoryDTOList = categoryService.listAllCategories(ownerId);
                log.info("Categories in database:  {}", categoryDTOList.size());
                StringBuilder telegramMessage = new StringBuilder(messages.get(language, "categories.header")).append("\n");
                for (CategoryDTO categoryDTO : categoryDTOList){
                    telegramMessage
                            .append("ID: ")
                            .append(categoryDTO.getCategoryId())
                            .append(" - ")
                            .append(categoryDTO.getCategoryDescription())
                            .append("\n");
                }
                sendReply(chatId, telegramMessage.toString());
                return false;
            }
            case "/nova_categoria", "/add-categoria":{
                if (isCommandToShort(command, 2, chatId, language))
                    return false;
                log.info("Add category requested.");
                Optional<CategoryTypeEnum> categoryType = parseCategoryType(command);
                if (categoryType.isEmpty()) {
                    sendReply(chatId, messages.get(language, "category.invalidType"));
                    return false;
                }
                CategoryCreationResultDTO result = categoryService.addCategory(command.get(1), categoryType.get(), ownerId);
                sendReply(chatId, categoryCreationReply(result, categoryType.get(), language));
                return false;
            }
            case "/nova_conta", "/add-conta":{
                if (isCommandToShort(command, 3, chatId, language))
                    return false;
                log.info("Add account requested.");
                Optional<CurrencyEnum> currency = parseCurrency(command);
                if (currency.isEmpty()) {
                    sendReply(chatId, messages.get(language, "account.invalidCurrency"));
                    return false;
                }
                AccountDTO account = accountService.addAccount(command.get(1), parseBalance(command.get(2)), currency.get(), ownerId);
                sendReply(chatId, messages.get(language, "account.added", account.getDescription(), MoneyFormat.format(account.getAmount(), account.getCurrency())));
                return false;
            }
            case "/contas":{
                log.info("Accounts requested.");
                List<AccountDTO> accountDTOList = accountService.listAllAccounts(ownerId);
                log.info("Accounts in database:  {}", accountDTOList.size());
                StringBuilder telegramMessage = new StringBuilder(messages.get(language, "accounts.header")).append("\n");
                for (AccountDTO accountDTO : accountDTOList){
                    telegramMessage.append(BotMessageFormat.accountLine(accountDTO)).append("\n");
                }
                sendReply(chatId, telegramMessage.toString());
                return false;
            }
            case "/transacoes":{
                log.info("Transactions requested.");
                List<TransactionDTO> transactionDTOS = transactionsService.listAllTransactions(LocalDate.now().minusDays(7), LocalDate.now(), null, null, ownerId).reversed();
                log.info("Last transactions:  {}", transactionDTOS.size());
                StringBuilder telegramMessage = new StringBuilder(messages.get(language, "transactions.header")).append("\n");

                for (TransactionDTO transactionDTO : transactionDTOS){
                    telegramMessage.append(BotMessageFormat.transactionLine(transactionDTO)).append("\n");
                }
                sendReply(chatId, telegramMessage.toString());
                return false;
            }
            case "/deletar_transacao", "/deletar-transacao":{
                if (isCommandToShort(command, 2, chatId, language))
                    return false;
                log.info("Transaction deletion requested");
                Integer transactionId = parseId(command.get(1), INVALID_TRANSACTION_MESSAGE);
                Optional<DeletedTransactionDTO> deleted = transactionsService.deleteTransaction(transactionId, ownerId);
                sendReply(chatId, deleted
                        .map(transaction -> messages.get(language, "transaction.deleted", transaction.getDescription(), MoneyFormat.format(transaction.getAmount(), transaction.getCurrency())))
                        .orElseGet(() -> messages.get(language, "transaction.notFound", String.valueOf(transactionId))));
                return false;
            }
            case "/ajuda", "/help":{
                sendReply(chatId, TelegramBotCommands.helpText(language.code()));
                return false;
            }
            case "/cancelar", "/cancel":{
                sendReply(chatId, messages.get(language, "onboarding.nothingToCancel"));
                return false;
            }
            default:
                return false;
        }
    }

    private void handleStart(List<String> command, Long chatId, String telegramLanguage){
        LanguageEnum language = LanguageEnum.fromTelegram(telegramLanguage);
        if (command.size() < 2) {
            log.info("Plain /start received from chatId {}.", chatId);
            //---- Linked but onboarding unfinished: /start repeats the current question.
            Optional<OwnerEntity> owner = ownerRepository.findByTelegramChatId(chatId);
            if (owner.isPresent() && owner.get().getOnboardingStep() != OnboardingStepEnum.DONE) {
                onboardingService.resume(owner.get().getOwnerId(), chatId, telegramLanguage);
                return;
            }
            sendReply(chatId, messages.get(owner.map(o -> LanguageEnum.resolve(o.getLanguage(), telegramLanguage)).orElse(language), "start.welcome"));
            return;
        }

        UUID inviteUuid;
        try {
            inviteUuid = UUID.fromString(command.get(1));
        } catch (IllegalArgumentException e) {
            log.info("Invalid invite uuid received from chatId {}.", chatId);
            sendReply(chatId, messages.get(language, "start.invalidInvite"));
            return;
        }

        if (!ownerService.linkTelegramChat(inviteUuid, chatId)) {
            sendReply(chatId, messages.get(language, "start.expiredInvite"));
            return;
        }

        //---- Re-linking an owner that already finished onboarding does not restart it.
        Optional<OwnerEntity> owner = ownerRepository.findByTelegramChatId(chatId);
        LanguageEnum ownerLanguage = owner.map(o -> LanguageEnum.resolve(o.getLanguage(), telegramLanguage)).orElse(language);
        sendReply(chatId, messages.get(ownerLanguage, "link.success"));
        if (owner.isPresent() && owner.get().getOnboardingStep() != OnboardingStepEnum.DONE)
            onboardingService.resume(owner.get().getOwnerId(), chatId, telegramLanguage);
    }

    //---- Returns true when a transaction was registered.
    public boolean audioCommand(Update update, Integer ownerId, LanguageEnum language){
        Long chatId = update.getMessage().getChatId();
        log.info("Voice message received from chatId: {}", chatId);
        String fileId = update.getMessage().getVoice().getFileId();

        try{
            String transcription = voiceTranscriptionService.transcribe(fileId);

            String template = new String(voiceParserPrompt.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String prompt = String.format(template, this.listCategories(ownerId), this.listAccounts(ownerId));

            log.info("Sending transcription to GPT for parsing");
            String response = openAITextClient.complete(prompt, transcription).orElse("{\"error\": \"NOT_UNDERSTOOD\"}");

            return proccessTranscriptedMessage(response, chatId, ownerId, language);
        } catch (Exception e){
            if (e instanceof InterruptedException)
                Thread.currentThread().interrupt();
            handleError(chatId, language, e, VOICE_ERROR_MESSAGE);
            return false;
        }
    }

    //---- GPT returns the transaction, {"error": "CATEGORY_NOT_FOUND", "category": ..., "operationType": ...} or {"error": "NOT_UNDERSTOOD"}.
    boolean proccessTranscriptedMessage(String response, Long chatId, Integer ownerId, LanguageEnum language){
        log.info("GPT parser response: {}", response);

        JsonNode node = Json.MAPPER.readTree(response);
        if (node.hasNonNull("error")) {
            String category = node.hasNonNull("category") ? node.get("category").asString().trim() : "";
            if ("CATEGORY_NOT_FOUND".equals(node.get("error").asString()) && !category.isEmpty()) {
                offerCategoryCreation(chatId, language, category, operationTypeOf(node));
                return false;
            }
            log.info("GPT could not parse the voice message, notifying user");
            sendReply(chatId, messages.get(language, "voice.notRecognized"));
            return false;
        }

        OpenAITransactionDTO transactionDTO = Json.MAPPER.treeToValue(node, OpenAITransactionDTO.class);
        log.info("Parsed transaction - operation: {}, category: {}, description: {}, amount: {}, accountId: {}",
                transactionDTO.getOperationType(), transactionDTO.getCategory(), transactionDTO.getDescription(),
                transactionDTO.getAmount(), transactionDTO.getAccountId());

        OperationTypeEnum operationType = transactionDTO.getOperationType();
        if (operationType != OperationTypeEnum.EXPENSE && operationType != OperationTypeEnum.INCOME) {
            sendReply(chatId, messages.get(language, "voice.notRecognized"));
            return false;
        }
        CategoryTypeEnum expectedType = operationType == OperationTypeEnum.INCOME ? CategoryTypeEnum.INCOME : CategoryTypeEnum.EXPENSE;

        Optional<CategoryEntity> category = transactionDTO.getCategory() == null || transactionDTO.getCategory().isBlank()
                ? Optional.empty()
                : categoryService.findByDescription(transactionDTO.getCategory(), ownerId);
        if (category.isEmpty()) {
            if (transactionDTO.getCategory() == null || transactionDTO.getCategory().isBlank()) {
                sendReply(chatId, messages.get(language, "voice.notRecognized"));
                return false;
            }
            offerCategoryCreation(chatId, language, transactionDTO.getCategory().trim(), expectedType);
            return false;
        }
        if (category.get().getType() != expectedType) {
            sendReply(chatId, messages.get(language, "voice.categoryTypeMismatch",
                    category.get().getDescription(), messages.get(language, "category.type." + category.get().getType().name())));
            return false;
        }

        TransactionRegistrationResultDTO result = operationType == OperationTypeEnum.EXPENSE
                ? transactionsService.registerExpense(ownerId, transactionDTO.getCategory(), transactionDTO.getAccountId(), transactionDTO.getAmount(), transactionDTO.getDescription())
                : transactionsService.registerIncome(ownerId, transactionDTO.getCategory(), transactionDTO.getAccountId(), transactionDTO.getAmount(), transactionDTO.getDescription());
        log.info("Voice {} registered successfully", operationType);
        sendReply(chatId, BotMessageFormat.transactionConfirmation(messages, language, operationType, result));
        return true;
    }

    //---- Created / already exists / name taken by a category of the other type.
    private String categoryCreationReply(CategoryCreationResultDTO result, CategoryTypeEnum requestedType, LanguageEnum language){
        CategoryEntity category = result.getCategory();
        if (result.isCreated())
            return messages.get(language, "category.created", category.getDescription());
        if (category.getType() != requestedType)
            return messages.get(language, "category.nameTakenByOtherType", category.getDescription(), messages.get(language, "category.type." + category.getType().name()));
        return messages.get(language, "category.exists", category.getDescription());
    }

    private static CategoryTypeEnum operationTypeOf(JsonNode node){
        return node.hasNonNull("operationType") && "INCOME".equals(node.get("operationType").asString())
                ? CategoryTypeEnum.INCOME : CategoryTypeEnum.EXPENSE;
    }

    //---- "Couldn't find 'Pharmacy'. Create it as expense? [Yes] [No]"
    private void offerCategoryCreation(Long chatId, LanguageEnum language, String category, CategoryTypeEnum type){
        log.info("Category '{}' not found, offering to create it", category);
        String typeLabel = messages.get(language, "category.type." + type.name());
        Optional<List<List<TelegramInlineButtonDTO>>> buttons = CategoryCallbackHandler.createButtons(category, type,
                messages.get(language, "voice.createCategory.yes"), messages.get(language, "voice.createCategory.no"));
        if (buttons.isPresent())
            telegramApiClient.sendMessageWithInlineKeyboard(chatId, messages.get(language, "voice.categoryNotFound", category, typeLabel), buttons.get());
        else
            sendReply(chatId, messages.get(language, "voice.categoryNotFoundNoButtons", category));
    }

    private void sendReply(Long chatId, String text){
        telegramApiClient.sendMessage(chatId, text);
    }

    //---- Callback data is "<prefix>:<payload>", dispatched to the TelegramCallbackHandler registered for the prefix.
    private void handleCallbackQuery(CallbackQuery callbackQuery){
        String answerText = null;
        Long chatId = callbackChatId(callbackQuery);
        String telegramLanguage = callbackQuery.getFrom() != null ? callbackQuery.getFrom().getLanguageCode() : null;
        LanguageEnum language = LanguageEnum.fromTelegram(telegramLanguage);
        try {
            Optional<OwnerEntity> owner = ownerRepository.findByTelegramChatId(chatId);
            if (owner.isEmpty()) {
                log.info("Unlinked chatId {} clicked an inline button.", chatId);
                sendReply(chatId, messages.get(language, NOT_LINKED_MESSAGE));
                return;
            }
            language = LanguageEnum.resolve(owner.get().getLanguage(), telegramLanguage);

            String data = callbackQuery.getData();
            Optional<TelegramCallbackHandler> handler = callbackRegistry.find(data);
            if (handler.isEmpty()) {
                log.info("No callback handler for data '{}' from chatId {}.", data, chatId);
                return;
            }

            log.info("Callback '{}' received from chatId {}.", data, chatId);
            answerText = handler.get().handle(new TelegramCallbackHandler.Context(
                    callbackQuery, chatId, owner.get().getOwnerId(), TelegramCallbackRegistry.payloadOf(data)));
        } catch (Exception e) {
            handleError(chatId, language, e, GENERIC_ERROR_MESSAGE);
        } finally {
            telegramApiClient.answerCallbackQuery(callbackQuery.getId(), answerText);
        }
    }

    //---- Never rethrows: the user always gets a reply and the webhook still answers 200.
    private void handleError(Long chatId, LanguageEnum language, Exception e, String fallbackMessageKey){
        String message = switch (e) {
            case TelegramUserInputException userInput -> messages.get(language, userInput.getMessageKey(), userInput.getArgs());
            case DuplicateAccountNameException duplicate -> messages.get(language, "account.duplicate", duplicate.getName());
            case CategoryNotFoundException ignored -> messages.get(language, CATEGORY_NOT_FOUND_MESSAGE);
            case InvalidMoneyFormatException ignored -> messages.get(language, INVALID_AMOUNT_MESSAGE);
            case NumberFormatException ignored -> messages.get(language, INVALID_AMOUNT_MESSAGE);
            case ResponseStatusException status when status.getStatusCode().isSameCodeAs(HttpStatus.FORBIDDEN)
                    || status.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND) -> messages.get(language, NOT_ALLOWED_MESSAGE);
            default -> null;
        };
        if (message == null) {
            log.error("Failed to process Telegram update from chatId {}: {}", chatId, e.getMessage(), e);
            message = messages.get(language, fallbackMessageKey);
        } else {
            log.info("Telegram request from chatId {} rejected: {}", chatId, e.getMessage());
        }

        //---- TelegramApiClient logs and swallows its own failures, so this never throws.
        sendReply(chatId, message);
    }

    //---- "/gasto@MoneyFlowBot" -> "/gasto" (Telegram appends the bot username in groups).
    static String normalizeCommand(String command){
        int at = command.indexOf('@');
        return (at > 0 ? command.substring(0, at) : command).toLowerCase();
    }

    private Integer parseId(String token, String errorMessageKey){
        try {
            return Integer.valueOf(token);
        } catch (NumberFormatException e) {
            throw new TelegramUserInputException(errorMessageKey);
        }
    }

    //---- The services check account ownership; a FORBIDDEN/NOT_FOUND from them means the account id is wrong.
    private <T> T withAccountErrors(Supplier<T> transactionCall){
        try {
            return transactionCall.get();
        } catch (ResponseStatusException e) {
            if (e.getStatusCode().isSameCodeAs(HttpStatus.FORBIDDEN) || e.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND))
                throw new TelegramUserInputException(INVALID_ACCOUNT_MESSAGE);
            throw e;
        }
    }

    //---- Words between the category (index 1) and the amount (size - 2).
    private static String transactionDescription(List<String> command){
        return String.join(" ", command.subList(2, command.size() - 2));
    }

    //---- Transaction amounts: Brazilian format, positive. Returned untouched (services parse it with MoneyFormat).
    private String parseTransactionAmount(String token){
        if (token.startsWith("-"))
            throw new TelegramUserInputException(INVALID_AMOUNT_MESSAGE);
        BigDecimal amount = MoneyFormat.parseBrazilian(token)
                .orElseThrow(() -> new TelegramUserInputException(INVALID_AMOUNT_MESSAGE));
        if (amount.signum() == 0)
            throw new TelegramUserInputException(ZERO_AMOUNT_MESSAGE);
        return token;
    }

    //---- Starting balances may be zero or negative (credit card, overdraft).
    private BigDecimal parseBalance(String token){
        return MoneyFormat.parseBrazilian(token)
                .orElseThrow(() -> new TelegramUserInputException(INVALID_AMOUNT_MESSAGE));
    }


    private boolean isCommandToShort(List<String> command, int expectedSize, Long chatId, LanguageEnum language){
        if (command.size() < expectedSize){
            sendReply(chatId, messages.get(language, "error.commandTooShort", normalizeCommand(command.getFirst())));
            return true;
        }
        return false;
    }

    //---- Optional 3rd token, defaults to EXPENSE.
    private Optional<CategoryTypeEnum> parseCategoryType(List<String> command){
        if (command.size() < 3)
            return Optional.of(CategoryTypeEnum.EXPENSE);
        return switch (command.get(2).toLowerCase()) {
            case "gasto", "expense" -> Optional.of(CategoryTypeEnum.EXPENSE);
            case "entrada", "income" -> Optional.of(CategoryTypeEnum.INCOME);
            default -> Optional.empty();
        };
    }

    //---- Optional 4th token, defaults to EUR.
    private Optional<CurrencyEnum> parseCurrency(List<String> command){
        if (command.size() < 4)
            return Optional.of(CurrencyEnum.EUR);
        try {
            return Optional.of(CurrencyEnum.valueOf(command.get(3).toUpperCase()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private StringBuilder listAccounts(Integer ownerId){
        List<AccountDTO> accountDTOList = accountService.listAllAccounts(ownerId);
        StringBuilder accountListText = new StringBuilder();
        for (AccountDTO accountDTO : accountDTOList){
            accountListText.append("ID: ")
                    .append(accountDTO.getAccountId())
                    .append(" - ")
                    .append(accountDTO.getDescription())
                    .append(" - ")
                    .append(accountDTO.getAmount())
                    .append("\n");
        }
        return accountListText;
    }

    //---- "ID: 1 - Mercado (EXPENSE)": the type lets the parser match "gastei" only with expense categories.
    private StringBuilder listCategories(Integer ownerId){
        List<CategoryDTO> categoryDTOList = categoryService.listAllCategories(ownerId);
        StringBuilder categoryListText = new StringBuilder();
        for (CategoryDTO categoryDTO : categoryDTOList){
            categoryListText.append("ID: ")
                    .append(categoryDTO.getCategoryId())
                    .append(" - ")
                    .append(categoryDTO.getCategoryDescription())
                    .append(" (")
                    .append(categoryDTO.getType())
                    .append(")\n");
        }
        return categoryListText;
    }
}
