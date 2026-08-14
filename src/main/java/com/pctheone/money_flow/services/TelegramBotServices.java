package com.pctheone.money_flow.services;

import com.pctheone.money_flow.dto.AccountDTO;
import com.pctheone.money_flow.dto.CategoryDTO;
import com.pctheone.money_flow.dto.TransactionRegistrationResultDTO;
import com.pctheone.money_flow.exceptions.InvalidMoneyFormatException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

@Service
public class TelegramBotServices {

    private static final Logger log = LoggerFactory.getLogger(TelegramBotServices.class);

    @Value("${telegram.url}")
    private String telegramUrl;

    @Value("${telegram.bot-token}")
    private String telegramBotToken;

    @Value("${telegram.timeout}")
    private int telegramTimeout;

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
        //---- /gasto mercado carne 25 1
        if (!update.hasMessage()) {
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
                checkMessage(command);
                log.info("Expense being registered.");
                TransactionRegistrationResultDTO expense = transactionsService.registerExpense(1, command.get(1), Integer.valueOf(command.getLast()), command.get(3), command.get(2));
                log.info("Expense registered successfully, amount:  {} -  Actual balance: {}", expense.getAmount(), expense.getNewBalance());
                String telegramMessage = "📉 Expense registered successfully. \n💰 Actual balance: €" + expense.getNewBalance().toString();
                sendReply(update.getMessage().getChatId(), telegramMessage);
                break;
            }
            case "/entrada":{
                checkMessage(command);
                log.info("Income being registered.");
                TransactionRegistrationResultDTO income = transactionsService.registerIncome(1, command.get(1), Integer.valueOf(command.getLast()), command.get(3), command.get(2));
                log.info("Income registered successfully, amount:  {} -  Actual balance: {}", income.getAmount(), income.getNewBalance());
                String telegramMessage = "📈 Income registered successfully. \n💰 Actual balance: €" + income.getNewBalance().toString();
                sendReply(update.getMessage().getChatId(), telegramMessage);
                break;
            }
            case "/categorias":{
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
                log.info("Add category requested.");
                String telegramMessage = categoryService.addCategory(command.get(1));
                sendReply(update.getMessage().getChatId(), telegramMessage);
                break;
            }
            case "/add-conta":{
                log.info("Add account requested.");
                String telegramMessage = accountService.addAccount(command.get(1), new BigDecimal(command.get(2)), 1);
                sendReply(update.getMessage().getChatId(), telegramMessage);
                break;
            }
            case "/contas":{
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
            default:
                break;

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

    private void checkMessage(List<String> command){
        if (command.get(3).contains(","))
            throw new InvalidMoneyFormatException("Wrong money format");
        log.info("Message checked successfully");
    }
}
