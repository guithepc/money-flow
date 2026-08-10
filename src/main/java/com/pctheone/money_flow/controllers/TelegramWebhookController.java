package com.pctheone.money_flow.controllers;

import com.pctheone.money_flow.services.TransactionsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.math.BigDecimal;

@RestController
@RequestMapping("/integration")
public class TelegramWebhookController {

    @Autowired
    TransactionsService transactionsService;

    @PostMapping("/telegram-webhook")
    public void expenseFromTelegram(@RequestBody String update){
        transactionsService.registerExpense(1, "Supermarket", 1, "28", "Mingau");
    }
}
