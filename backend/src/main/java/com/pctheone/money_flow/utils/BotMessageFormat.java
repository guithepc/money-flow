package com.pctheone.money_flow.utils;

import com.pctheone.money_flow.dto.AccountDTO;
import com.pctheone.money_flow.dto.TransactionDTO;
import com.pctheone.money_flow.dto.TransactionRegistrationResultDTO;
import com.pctheone.money_flow.enums.OperationTypeEnum;

//---- Telegram reply lines that show money. Every value is formatted with its own account's currency
//---- (see MoneyFormat.format(BigDecimal, CurrencyEnum)); nothing here sums values across accounts.
public class BotMessageFormat {

    //---- "📉 R$ 30,00 · Mercado · Nubank — balance R$ 470,00" (📈 for income)
    public static String transactionConfirmation(OperationTypeEnum operationType, TransactionRegistrationResultDTO result){
        String emoji = OperationTypeEnum.INCOME.equals(operationType) ? "📈" : "📉";
        return emoji + " " + MoneyFormat.format(result.getAmount(), result.getCurrency())
                + " · " + result.getCategoryDescription()
                + " · " + result.getAccountDescription()
                + " — balance " + MoneyFormat.format(result.getNewBalance(), result.getCurrency());
    }

    //---- "ID: 1 - Nubank | R$ 470,00"
    public static String accountLine(AccountDTO account){
        return "ID: " + account.getAccountId() + " - " + account.getDescription()
                + " | " + MoneyFormat.format(account.getAmount(), account.getCurrency());
    }

    //---- "ID: 12 - Mercado - R$ 30,00"
    public static String transactionLine(TransactionDTO transaction){
        return "ID: " + transaction.getId() + " - " + transaction.getDescription()
                + " - " + MoneyFormat.format(transaction.getAmount(), transaction.getCurrency());
    }
}
