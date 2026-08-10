package com.pctheone.money_flow.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.Arrays;
import java.util.List;

@Service
public class TelegramBotServices {

    @Autowired
    TransactionsService transactionsService;

    public void telegramRouter(Update update){
        //---- /gasto mercado carne 25 1
        String message = update.getMessage().getText();

        List<String> command = Arrays.asList(message.split(" "));


        switch (command.getFirst()){
            case "/gasto":
                transactionsService.registerExpense(1, command.get(1), Integer.valueOf(command.getLast()), command.get(3), command.get(2));
                break;
            case "/entrada":
                break;
            default:
                break;

        }
    }
}
