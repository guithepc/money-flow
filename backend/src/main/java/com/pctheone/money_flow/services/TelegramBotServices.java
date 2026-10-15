package com.pctheone.money_flow.services;

import com.openai.models.audio.transcriptions.TranscriptionCreateParams;
import com.pctheone.money_flow.dto.*;
import com.pctheone.money_flow.entities.OwnerEntity;
import com.pctheone.money_flow.enums.CategoryTypeEnum;
import com.pctheone.money_flow.enums.CurrencyEnum;
import com.pctheone.money_flow.enums.OperationTypeEnum;
import com.pctheone.money_flow.exceptions.CategoryNotFoundException;
import com.pctheone.money_flow.exceptions.InvalidMoneyFormatException;
import com.pctheone.money_flow.exceptions.TelegramUserInputException;
import com.pctheone.money_flow.repositories.OwnerRepository;
import com.pctheone.money_flow.services.telegram.TelegramApiClient;
import com.pctheone.money_flow.services.telegram.TelegramBotCommands;
import com.pctheone.money_flow.services.telegram.TelegramCallbackHandler;
import com.pctheone.money_flow.services.telegram.TelegramCallbackRegistry;
import com.pctheone.money_flow.services.telegram.TelegramProcessedUpdateService;
import com.pctheone.money_flow.utils.MoneyFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.Voice;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.openai.client.OpenAIClient;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
@Service
public class TelegramBotServices {

    private static final Logger log = LoggerFactory.getLogger(TelegramBotServices.class);

    static final String CATEGORY_NOT_FOUND_MESSAGE = "🚨 Category not found. Use /categorias to see your categories or /nova_categoria to create one.";
    static final String INVALID_AMOUNT_MESSAGE = "🚨 Invalid amount. Use only numbers, e.g. 25 or 25,50.";
    static final String INVALID_ACCOUNT_MESSAGE = "🚨 Invalid account. Use /contas to see your account IDs.";
    static final String INVALID_TRANSACTION_MESSAGE = "🚨 Invalid transaction id. Use /transacoes to see your transaction IDs.";
    static final String NOT_ALLOWED_MESSAGE = "🚨 Not found or it doesn't belong to you.";
    static final String GENERIC_ERROR_MESSAGE = "🚨 Something went wrong processing your request. Please try again.";
    static final String VOICE_ERROR_MESSAGE = "🚨 Couldn't process your voice message. Please try again.";
    static final String NOT_LINKED_MESSAGE = "👋 Your Telegram isn't linked yet. Register at Money Flow and use the \"Link Telegram\" button to connect your account.";

    @Value("classpath:prompts/voice-parser.txt")
    Resource voiceParserPrompt;

    @Autowired
    TelegramApiClient telegramApiClient;

    @Autowired
    TelegramProcessedUpdateService processedUpdateService;

    @Autowired
    TelegramCallbackRegistry callbackRegistry;

    @Lazy
    @Autowired
    OpenAIClient openAIClient;

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

    public void telegramRouter(Update update){
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

        Long chatId = update.getMessage().getChatId();
        try {
            routeMessage(update);
        } catch (Exception e) {
            handleError(chatId, e, GENERIC_ERROR_MESSAGE);
        }
    }

    //---- Sends a message with inline buttons. Each inner list is a row; clicks are routed by TelegramCallbackRegistry.
    public void sendMessageWithInlineKeyboard(Long chatId, String text, List<List<TelegramInlineButtonDTO>> keyboard){
        telegramApiClient.sendMessageWithInlineKeyboard(chatId, text, keyboard);
    }

    private void routeMessage(Update update){
        Long chatId = update.getMessage().getChatId();
        String text = update.getMessage().getText();

        //---- /start <uuid> onboarding runs before owner resolution.
        if (text != null && "/start".equals(text.split(" ")[0])) {
            handleStart(Arrays.asList(text.split(" ")), chatId);
            return;
        }

        Optional<OwnerEntity> owner = ownerRepository.findByTelegramChatId(chatId);
        if (owner.isEmpty()) {
            log.info("Unlinked chatId {} tried to use the bot.", chatId);
            sendReply(chatId, NOT_LINKED_MESSAGE);
            return;
        }
        Integer ownerId = owner.get().getOwnerId();

        if (update.getMessage().getVoice() != null) {
            audioCommand(update, ownerId);
            return;
        }

        //---- /gasto mercado carne 25 1
        if (text == null || text.isBlank()) {
            log.info("No text in message.");
            return;
        }

        String message = text;

        List<String> command = Arrays.asList(message.trim().split("\\s+"));

        switch (normalizeCommand(command.getFirst())){
            case "/gasto":{
                if (isCommandToShort(command, 5, update))
                    break;
                log.info("Expense being registered.");
                TransactionRegistrationResultDTO expense = transactionsService.registerExpense(ownerId, command.get(1), parseAccountId(command.getLast(), ownerId), parseAmount(command.get(3)), command.get(2));
                log.info("Expense registered successfully, amount:  {} -  Actual balance: {}", expense.getAmount(), expense.getNewBalance());
                String telegramMessage = "📉 Expense registered successfully. \n💰 Actual balance: €" + expense.getNewBalance().toString();
                sendReply(update.getMessage().getChatId(), telegramMessage);
                break;
            }
            case "/entrada":{
                if (isCommandToShort(command, 5, update))
                    break;
                log.info("Income being registered.");
                TransactionRegistrationResultDTO income = transactionsService.registerIncome(ownerId, command.get(1), parseAccountId(command.getLast(), ownerId), parseAmount(command.get(3)), command.get(2));
                log.info("Income registered successfully, amount:  {} -  Actual balance: {}", income.getAmount(), income.getNewBalance());
                String telegramMessage = "📈 Income registered successfully. \n💰 Actual balance: €" + income.getNewBalance().toString();
                sendReply(update.getMessage().getChatId(), telegramMessage);
                break;
            }
            case "/categorias":{
                if (isCommandToShort(command, 1, update))
                    break;
                log.info("Categories requested.");
                List<CategoryDTO> categoryDTOList = categoryService.listAllCategories(ownerId);
                log.info("Categories in database:  {}", categoryDTOList.size());
                StringBuilder telegramMessage = new StringBuilder("📈 Your categories:\n");
                for (CategoryDTO categoryDTO : categoryDTOList){
                    telegramMessage
                            .append("ID: ")
                            .append(categoryDTO.getCategoryId())
                            .append(" - ")
                            .append(categoryDTO.getCategoryDescription())
                            .append("\n");
                }
                sendReply(update.getMessage().getChatId(), telegramMessage.toString());
                break;
            }
            case "/nova_categoria", "/add-categoria":{
                if (isCommandToShort(command, 2, update))
                    break;
                log.info("Add category requested.");
                Optional<CategoryTypeEnum> categoryType = parseCategoryType(command);
                if (categoryType.isEmpty()) {
                    sendReply(update.getMessage().getChatId(), "🚨 Invalid category type. Use: /nova_categoria <name> [gasto|entrada]");
                    break;
                }
                String telegramMessage = categoryService.addCategory(command.get(1), categoryType.get(), ownerId);
                sendReply(update.getMessage().getChatId(), telegramMessage);
                break;
            }
            case "/nova_conta", "/add-conta":{
                if (isCommandToShort(command, 3, update))
                    break;
                log.info("Add account requested.");
                Optional<CurrencyEnum> currency = parseCurrency(command);
                if (currency.isEmpty()) {
                    sendReply(update.getMessage().getChatId(), "🚨 Invalid currency. Use: /nova_conta <name> <balance> [BRL|EUR|USD]");
                    break;
                }
                AccountDTO account = accountService.addAccount(command.get(1), new BigDecimal(MoneyFormat.format(parseAmount(command.get(2)))), currency.get(), ownerId);
                sendReply(update.getMessage().getChatId(), "Account added: " + account.getDescription() + " (" + account.getCurrency() + ")");
                break;
            }
            case "/contas":{
                if (isCommandToShort(command, 1, update))
                    break;
                log.info("Accounts requested.");
                List<AccountDTO> accountDTOList = accountService.listAllAccounts(ownerId);
                log.info("Accounts in database:  {}", accountDTOList.size());
                StringBuilder telegramMessage = new StringBuilder("🏦 Your accounts:\n");
                for (AccountDTO accountDTO : accountDTOList){
                    telegramMessage
                            .append("ID: ")
                            .append(accountDTO.getAccountId())
                            .append(" - ")
                            .append(accountDTO.getDescription())
                            .append(" | ")
                            .append(accountDTO.getAmount())
                            .append("\n");
                }
                sendReply(update.getMessage().getChatId(), telegramMessage.toString());
                break;
            }
            case "/transacoes":{
                if (isCommandToShort(command, 1, update))
                    break;
                log.info("Transactions requested.");
                List<TransactionDTO> transactionDTOS = transactionsService.listAllTransactions(LocalDate.now().minusDays(7), LocalDate.now(), null, ownerId).reversed();
                log.info("Last transactions:  {}", transactionDTOS.size());
                StringBuilder telegramMessage = new StringBuilder("⚡️Last transactions:\n");

                for (TransactionDTO transactionDTO : transactionDTOS){
                    telegramMessage.append("ID: ")
                            .append(transactionDTO.getId())
                            .append(" - ")
                            .append(transactionDTO.getDescription())
                            .append(" - ")
                            .append(transactionDTO.getAmount())
                            .append("\n");
                }
                sendReply(update.getMessage().getChatId(), telegramMessage.toString());
                break;
            }
            case "/deletar_transacao", "/deletar-transacao":{
                if (isCommandToShort(command, 2, update))
                    break;
                log.info("Transaction deletion requested");
                String response = transactionsService.deleteTransaction(parseId(command.get(1), INVALID_TRANSACTION_MESSAGE), ownerId);
                sendReply(update.getMessage().getChatId(), response);
                break;
            }
            case "/ajuda", "/help":{
                String languageCode = update.getMessage().getFrom() != null ? update.getMessage().getFrom().getLanguageCode() : null;
                sendReply(update.getMessage().getChatId(), TelegramBotCommands.helpText(languageCode));
                break;
            }
            default:
                break;

        }
    }

    private void handleStart(List<String> command, Long chatId){
        if (command.size() < 2) {
            log.info("Plain /start received from chatId {}.", chatId);
            sendReply(chatId, "👋 Welcome to Money Flow! To connect this chat to your account, open Money Flow and use the \"Link Telegram\" button.");
            return;
        }

        UUID inviteUuid;
        try {
            inviteUuid = UUID.fromString(command.get(1));
        } catch (IllegalArgumentException e) {
            log.info("Invalid invite uuid received from chatId {}.", chatId);
            sendReply(chatId, "🚨 Invalid invite link. Generate a new one from Money Flow.");
            return;
        }

        if (ownerService.linkTelegramChat(inviteUuid, chatId)) {
            sendReply(chatId, "✅ Your Telegram is now linked to your Money Flow account!");
        } else {
            sendReply(chatId, "🚨 This invite link is invalid or expired. Generate a new one from Money Flow.");
        }
    }

    public void audioCommand(Update update, Integer ownerId){
        Long chatId = update.getMessage().getChatId();
        log.info("Voice message received from chatId: {}", chatId);
        Voice voiceAudio = update.getMessage().getVoice();
        String fileId = voiceAudio.getFileId();

        Path tempFile = null;
        try{
            byte[] audio = telegramApiClient.downloadFile(fileId);

            tempFile = Files.createTempFile("voice_", ".ogg");

            Files.write(tempFile, audio);

            log.info("Sending audio to Whisper for transcription");
            var result = openAIClient.audio().transcriptions().create(
                    TranscriptionCreateParams.builder()
                    .file(tempFile)
                            .model("gpt-transcribe")
                            .build());

            String transcription = result.asTranscription().text();
            log.info("Whisper transcription result: {}", transcription);

            String template = new String(voiceParserPrompt.getInputStream().readAllBytes());
            String prompt = String.format(template, this.listCategories(ownerId), this.listAccounts(ownerId));

            log.info("Sending transcription to GPT for parsing");
            ResponseCreateParams params =
                ResponseCreateParams.builder().model("gpt-4o-mini").instructions(prompt).input(transcription).build();

            Response openAiResponse = openAIClient.responses().create(params);

            proccessTranscriptedMessage(openAiResponse, update, ownerId);


        } catch (Exception e){
            if (e instanceof InterruptedException)
                Thread.currentThread().interrupt();
            handleError(chatId, e, VOICE_ERROR_MESSAGE);
        } finally {
            deleteTempFile(tempFile);
        }
    }

    private void deleteTempFile(Path tempFile){
        if (tempFile == null)
            return;
        try {
            Files.deleteIfExists(tempFile);
        } catch (IOException e) {
            log.error("Failed to delete temp voice file {}: {}", tempFile, e.getMessage());
        }
    }

    private void proccessTranscriptedMessage(Response openAiResponse, Update update, Integer ownerId){
        String response = openAiResponse.output().stream().flatMap(item -> item.asMessage().content().stream())
                .map(content -> content.asOutputText().text())
                .findFirst()
                .orElse("");

        log.info("GPT parser response: {}", response);

        ObjectMapper objectMapper = new ObjectMapper();
        if (response.contains("repetir")) {
            log.info("GPT returned error response, notifying user");
            this.sendReply(update.getMessage().getChatId(), "🚨 Voice command not recognized, send again.");
            return;
        } else if (response.contains("categoria")){
            log.info("No category found, notifying user");
            this.sendReply(update.getMessage().getChatId(), "🚨 Category not found, try with valid category");
            return;
        }

        OpenAITransactionDTO transactionDTO = objectMapper.readValue(response, OpenAITransactionDTO.class);
        log.info("Parsed transaction - operation: {}, category: {}, description: {}, amount: {}, accountId: {}",
                transactionDTO.getOperationType(), transactionDTO.getCategory(), transactionDTO.getDescription(),
                transactionDTO.getAmount(), transactionDTO.getAccountId());

        if (OperationTypeEnum.EXPENSE.equals(transactionDTO.getOperationType())) {
            transactionsService.registerExpense(ownerId, transactionDTO.getCategory(), transactionDTO.getAccountId(), transactionDTO.getAmount(), transactionDTO.getDescription());
            log.info("Voice expense registered successfully");
            this.sendReply(update.getMessage().getChatId(), "✅ Voice expense saved successfully!");
        } else if (OperationTypeEnum.INCOME.equals(transactionDTO.getOperationType())) {
            transactionsService.registerIncome(ownerId, transactionDTO.getCategory(), transactionDTO.getAccountId(), transactionDTO.getAmount(), transactionDTO.getDescription());
            log.info("Voice income registered successfully");
            this.sendReply(update.getMessage().getChatId(), "✅ Voice income saved successfully!");
        } 
    }

    private void sendReply(Long chatId, String text){
        telegramApiClient.sendMessage(chatId, text);
    }

    //---- Callback data is "<prefix>:<payload>", dispatched to the TelegramCallbackHandler registered for the prefix.
    private void handleCallbackQuery(CallbackQuery callbackQuery){
        String answerText = null;
        Long chatId = callbackQuery.getMessage() != null ? callbackQuery.getMessage().getChatId() : callbackQuery.getFrom().getId();
        try {
            Optional<OwnerEntity> owner = ownerRepository.findByTelegramChatId(chatId);
            if (owner.isEmpty()) {
                log.info("Unlinked chatId {} clicked an inline button.", chatId);
                sendReply(chatId, NOT_LINKED_MESSAGE);
                return;
            }

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
            handleError(chatId, e, GENERIC_ERROR_MESSAGE);
        } finally {
            telegramApiClient.answerCallbackQuery(callbackQuery.getId(), answerText);
        }
    }

    //---- Never rethrows: the user always gets a reply and the webhook still answers 200.
    private void handleError(Long chatId, Exception e, String fallbackMessage){
        String message = switch (e) {
            case TelegramUserInputException userInput -> userInput.getMessage();
            case CategoryNotFoundException ignored -> CATEGORY_NOT_FOUND_MESSAGE;
            case InvalidMoneyFormatException ignored -> INVALID_AMOUNT_MESSAGE;
            case NumberFormatException ignored -> INVALID_AMOUNT_MESSAGE;
            case ResponseStatusException ignored -> NOT_ALLOWED_MESSAGE;
            default -> null;
        };
        if (message == null) {
            log.error("Failed to process Telegram update from chatId {}: {}", chatId, e.getMessage(), e);
            message = fallbackMessage;
        } else {
            log.info("Telegram request from chatId {} rejected: {}", chatId, e.getMessage());
        }

        try {
            sendReply(chatId, message);
        } catch (Exception replyError) {
            log.error("Failed to send error reply to chatId {}: {}", chatId, replyError.getMessage(), replyError);
        }
    }

    //---- "/gasto@MoneyFlowBot" -> "/gasto" (Telegram appends the bot username in groups).
    static String normalizeCommand(String command){
        int at = command.indexOf('@');
        return (at > 0 ? command.substring(0, at) : command).toLowerCase();
    }

    private Integer parseId(String token, String errorMessage){
        try {
            return Integer.valueOf(token);
        } catch (NumberFormatException e) {
            throw new TelegramUserInputException(errorMessage);
        }
    }

    private Integer parseAccountId(String token, Integer ownerId){
        Integer accountId = parseId(token, INVALID_ACCOUNT_MESSAGE);
        try {
            accountService.validateAccountOwnership(accountId, ownerId);
        } catch (ResponseStatusException e) {
            throw new TelegramUserInputException(INVALID_ACCOUNT_MESSAGE);
        }
        return accountId;
    }

    //---- Validates the amount and returns it untouched (services parse it with MoneyFormat).
    private String parseAmount(String token){
        BigDecimal amount;
        try {
            amount = new BigDecimal(MoneyFormat.format(token));
        } catch (NumberFormatException e) {
            throw new TelegramUserInputException(INVALID_AMOUNT_MESSAGE);
        }
        if (amount.signum() < 0)
            throw new TelegramUserInputException(INVALID_AMOUNT_MESSAGE);
        return token;
    }


    private boolean isCommandToShort(List<String> command, int expectedSize, Update update){
        if (command.size() < expectedSize){
            String errorMessage = "Message doesnt have right size. Expected size for command: " + expectedSize;
            sendReply(update.getMessage().getChatId(), errorMessage);
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

    private StringBuilder listCategories(Integer ownerId){
        List<CategoryDTO> categoryDTOList = categoryService.listAllCategories(ownerId);
        StringBuilder categoryListText = new StringBuilder();
        for (CategoryDTO categoryDTO : categoryDTOList){
            categoryListText.append("ID: ")
                    .append(categoryDTO.getCategoryId())
                    .append(" - ")
                    .append(categoryDTO.getCategoryDescription())
                    .append("\n");
        }
        return categoryListText;
    }
}
