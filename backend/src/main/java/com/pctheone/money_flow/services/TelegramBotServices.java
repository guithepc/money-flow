package com.pctheone.money_flow.services;

import com.openai.models.audio.transcriptions.TranscriptionCreateParams;
import com.pctheone.money_flow.dto.*;
import com.pctheone.money_flow.enums.OperationTypeEnum;
import com.pctheone.money_flow.utils.MoneyFormat;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.Voice;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
@Service
public class TelegramBotServices {

    private static final Logger log = LoggerFactory.getLogger(TelegramBotServices.class);

    @Value("${telegram.url}")
    private String telegramUrl;

    @Value("${telegram.bot-token}")
    private String telegramBotToken;

    @Value("${telegram.timeout}")
    private int telegramTimeout;

    @Value("classpath:prompts/voice-parser.txt")
    private Resource voiceParserPrompt;

    private HttpClient httpClient;

    @PostConstruct
    private void init() {
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(telegramTimeout))
                .build();
    }

    @Autowired
    TransactionsService transactionsService;

    @Autowired
    CategoryService categoryService;

    @Autowired
    AccountService accountService;

    public void telegramRouter(Update update){
        if (update.getMessage().getVoice() != null) {

            audioCommand(update);
            return;
        }


        //---- /gasto mercado carne 25 1
        if (!update.hasMessage() || (update.getMessage().getText() == null && update.getMessage().getAudio() == null)) {
            log.info("No message in update.");
            return;
        }

        String message = update.getMessage().getText();

        List<String> command = Arrays.asList(message.split(" "));
        if ("/start".equals(command.getFirst())){
            log.info("Telegram bot starting.");
            return;
        }


        switch (command.getFirst()){
            case "/gasto":{
                if (isCommandToShort(command, 5, update))
                    break;
                log.info("Expense being registered.");
                TransactionRegistrationResultDTO expense = transactionsService.registerExpense(1, command.get(1), Integer.valueOf(command.getLast()), command.get(3), command.get(2));
                log.info("Expense registered successfully, amount:  {} -  Actual balance: {}", expense.getAmount(), expense.getNewBalance());
                String telegramMessage = "📉 Expense registered successfully. \n💰 Actual balance: €" + expense.getNewBalance().toString();
                sendReply(update.getMessage().getChatId(), telegramMessage);
                break;
            }
            case "/entrada":{
                if (isCommandToShort(command, 5, update))
                    break;
                log.info("Income being registered.");
                TransactionRegistrationResultDTO income = transactionsService.registerIncome(1, command.get(1), Integer.valueOf(command.getLast()), command.get(3), command.get(2));
                log.info("Income registered successfully, amount:  {} -  Actual balance: {}", income.getAmount(), income.getNewBalance());
                String telegramMessage = "📈 Income registered successfully. \n💰 Actual balance: €" + income.getNewBalance().toString();
                sendReply(update.getMessage().getChatId(), telegramMessage);
                break;
            }
            case "/categorias":{
                if (isCommandToShort(command, 1, update))
                    break;
                log.info("Categories requested.");
                List<CategoryDTO> categoryDTOList = categoryService.listAllCategories();
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
            case "/add-categoria":{
                if (isCommandToShort(command, 2, update))
                    break;
                log.info("Add category requested.");
                String telegramMessage = categoryService.addCategory(command.get(1));
                sendReply(update.getMessage().getChatId(), telegramMessage);
                break;
            }
            case "/add-conta":{
                if (isCommandToShort(command, 3, update))
                    break;
                log.info("Add account requested.");
                String telegramMessage = accountService.addAccount(command.get(1), new BigDecimal(MoneyFormat.format(command.get(2))), 1);
                sendReply(update.getMessage().getChatId(), telegramMessage);
                break;
            }
            case "/contas":{
                if (isCommandToShort(command, 1, update))
                    break;
                log.info("Accounts requested.");
                List<AccountDTO> accountDTOList = accountService.listAllAccounts();
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
                List<TransactionDTO> transactionDTOS = transactionsService.listAllTransactions(LocalDate.now().minusDays(7), LocalDate.now(), null).reversed();
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
            case "/deletar-transacao":{
                if (isCommandToShort(command, 2, update))
                    break;
                log.info("Transaction deletion requested");
                String response = transactionsService.deleteTransaction(Integer.valueOf(command.get(1)));
                sendReply(update.getMessage().getChatId(), response);
                break;
            }
            default:
                break;

        }
    }

    public void audioCommand(Update update){
        log.info("Voice message received from chatId: {}", update.getMessage().getChatId());
        Voice voiceAudio = update.getMessage().getVoice();
        String fileId = voiceAudio.getFileId();
        ObjectMapper objectMapper = new ObjectMapper();


        String telegramRequestUrl = telegramUrl + telegramBotToken;
        try{
            log.info("Requesting file metadata from Telegram API, fileId: {}", fileId);
            HttpRequest getFileRequest = HttpRequest.newBuilder()
                    .uri(URI.create(telegramRequestUrl + "/getFile" + "?file_id=" + fileId))
                    .header("Content-Type", "application/json")
                    .build();

            HttpResponse<String> getFileResponse = httpClient.send(getFileRequest, HttpResponse.BodyHandlers.ofString());

            JsonNode node = objectMapper.readTree(getFileResponse.body());

            String filePath = node.get("result").get("file_path").asString();
            log.info("File metadata retrieved, filePath: {}", filePath);

            HttpRequest downloadFileRequest = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.telegram.org/file/bot" + telegramBotToken + "/" + filePath))
                    .build();

            HttpResponse<byte[]> downloadFileResponse = httpClient.send(downloadFileRequest, HttpResponse.BodyHandlers.ofByteArray());
            log.info("Voice file downloaded, size: {} bytes", downloadFileResponse.body().length);

            Path tempFile = Files.createTempFile("voice_", ".ogg");

            Files.write(tempFile, downloadFileResponse.body());

            OpenAIClient client = OpenAIOkHttpClient.fromEnv();

            log.info("Sending audio to Whisper for transcription");
            var result = client.audio().transcriptions().create(
                    TranscriptionCreateParams.builder()
                    .file(tempFile)
                            .model("gpt-transcribe")
                            .build());

            String transcription = result.toString();
            log.info("Whisper transcription result: {}", transcription);

            String template = new String(voiceParserPrompt.getInputStream().readAllBytes());
            String prompt = String.format(template, this.listCategories(), this.listAccounts());

            log.info("Sending transcription to GPT for parsing");
            ResponseCreateParams params =
                ResponseCreateParams.builder().model("gpt-4o-mini").instructions(prompt).input(transcription).build();

            Response openAiResponse = client.responses().create(params);

            proccessTranscriptedMessage(openAiResponse, update);


        } catch (Exception e){
            Thread.currentThread().interrupt();
            log.error("Failed to process voice message: {}", e.getMessage(), e);
        }
    }

    private void proccessTranscriptedMessage(Response openAiResponse, Update update){
        String response = openAiResponse.output().stream().flatMap(item -> item.asMessage().content().stream())
                .map(content -> content.asOutputText().text())
                .findFirst()
                .orElse("");

        log.info("GPT parser response: {}", response);

        ObjectMapper objectMapper = new ObjectMapper();
        if (response.contains("error")) {
            log.info("GPT returned error response, notifying user");
            this.sendReply(update.getMessage().getChatId(), "🚨 Voice command not recognized, send again.");
            return;
        }

        OpenAITransactionDTO transactionDTO = objectMapper.readValue(response, OpenAITransactionDTO.class);
        log.info("Parsed transaction - operation: {}, category: {}, description: {}, amount: {}, accountId: {}",
                transactionDTO.getOperationType(), transactionDTO.getCategory(), transactionDTO.getDescription(),
                transactionDTO.getAmount(), transactionDTO.getAccountId());

        if (OperationTypeEnum.EXPENSE.equals(transactionDTO.getOperationType())) {
            transactionsService.registerExpense(1, transactionDTO.getCategory(), transactionDTO.getAccountId(), transactionDTO.getAmount(), transactionDTO.getDescription());
            log.info("Voice expense registered successfully");
            this.sendReply(update.getMessage().getChatId(), "✅ Voice expense saved successfully!");
        } else if (OperationTypeEnum.INCOME.equals(transactionDTO.getOperationType())) {
            transactionsService.registerIncome(1, transactionDTO.getCategory(), transactionDTO.getAccountId(), transactionDTO.getAmount(), transactionDTO.getDescription());
            log.info("Voice income registered successfully");
            this.sendReply(update.getMessage().getChatId(), "✅ Voice income saved successfully!");
        } else {
        }
    }

    private void sendReply(Long chatId, String text){
        String telegramRequestUrl = telegramUrl + telegramBotToken + "/sendMessage";

        ObjectMapper objectMapper = new ObjectMapper();
        SendMessage sendMessage = new SendMessage(chatId.toString(), text);
        String jsonObj = objectMapper.writeValueAsString(sendMessage);

        try{
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(telegramRequestUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonObj))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200){
                log.info("Telegram send message sent successfully");
            } else {
                log.info("Telegram send message FAILED {} - {}", response.statusCode(), response.body());
            }

        } catch (IOException |InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Failed to call Telegram API {}", e.getMessage(), e);
        }

    }


    private boolean isCommandToShort(List<String> command, int expectedSize, Update update){
        if (command.size() < expectedSize){
            String errorMessage = "Message doesnt have right size. Expected size for command: " + expectedSize;
            sendReply(update.getMessage().getChatId(), errorMessage);
            return true;
        }
        return false;
    }

    private StringBuilder listAccounts(){
        List<AccountDTO> accountDTOList = accountService.listAllAccounts();
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

    private StringBuilder listCategories(){
        List<CategoryDTO> categoryDTOList = categoryService.listAllCategories();
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
