package com.pctheone.money_flow.services;

import com.pctheone.money_flow.dto.ExpenseRegistrationResultDTO;
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

    public void telegramRouter(Update update){
        //---- /gasto mercado carne 25 1
        String message = update.getMessage().getText();

        List<String> command = Arrays.asList(message.split(" "));
        if (command.get(3).contains(","))
            throw new InvalidMoneyFormatException("Wrong money format");

        switch (command.getFirst()){
            case "/gasto":
                log.info("Expense being registered.");
                ExpenseRegistrationResultDTO expense = transactionsService.registerExpense(1, command.get(1), Integer.valueOf(command.getLast()), command.get(3), command.get(2));
                log.info("Expense registered successfully, amount:  {} - Actual balance: {}", expense.getAmount(), expense.getNewBalance());
                String telegramMessage = "Expense registered successfully. Actual balance: " + expense.getNewBalance().toString();
                sendReply(update.getMessage().getChatId(), telegramMessage);
                break;
            case "/entrada":
                break;
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
}
