package com.pctheone.money_flow.utils;

import com.pctheone.money_flow.dto.TransactionRegistrationResultDTO;
import com.pctheone.money_flow.enums.CurrencyEnum;
import com.pctheone.money_flow.enums.LanguageEnum;
import com.pctheone.money_flow.enums.OperationTypeEnum;
import com.pctheone.money_flow.services.telegram.BotMessages;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public class MoneyFormatTest {

    @Test
    void acceptsBrazilianAmounts() {
        Assertions.assertEquals(Optional.of(new BigDecimal("1250.90")), MoneyFormat.parseBrazilian("1.250,90"));
        Assertions.assertEquals(Optional.of(new BigDecimal("1250.90")), MoneyFormat.parseBrazilian("1250,90"));
        Assertions.assertEquals(Optional.of(new BigDecimal("1250")), MoneyFormat.parseBrazilian("1250"));
        Assertions.assertEquals(Optional.of(new BigDecimal("1000000")), MoneyFormat.parseBrazilian("1.000.000"));
        Assertions.assertEquals(Optional.of(new BigDecimal("-300.5")), MoneyFormat.parseBrazilian("-300,5"));
    }

    @Test
    void rejectsAmbiguousOrMalformedAmounts() {
        for (String amount : List.of("25.90", "1.25", "12.3456", "1e5", "1E5", "12a", "", ",5", "25,", "25,999", "1,250.90", "+5", "1.250.9"))
            Assertions.assertTrue(MoneyFormat.parseBrazilian(amount).isEmpty(), amount);
        Assertions.assertTrue(MoneyFormat.parseBrazilian(null).isEmpty());
    }

    @Test
    void formatsWithAccountCurrencySymbol(){
        Assertions.assertEquals("R$ 1.250,90", MoneyFormat.format(new BigDecimal("1250.9"), CurrencyEnum.BRL));
        Assertions.assertEquals("€ 681,03", MoneyFormat.format(new BigDecimal("681.03"), CurrencyEnum.EUR));
        Assertions.assertEquals("US$ 10,00", MoneyFormat.format(new BigDecimal("10"), CurrencyEnum.USD));
        Assertions.assertEquals("-R$ 12,00", MoneyFormat.format(new BigDecimal("-12"), CurrencyEnum.BRL));
        Assertions.assertEquals("R$ 0,01", MoneyFormat.format(new BigDecimal("0.005"), CurrencyEnum.BRL));
    }

    @Test
    void transactionConfirmationUsesAccountCurrency(){
        TransactionRegistrationResultDTO result = new TransactionRegistrationResultDTO(
                new BigDecimal("30"), new BigDecimal("470"), CurrencyEnum.BRL, "Nubank", "Mercado");
        BotMessages messages = new BotMessages();
        Assertions.assertEquals("📉 R$ 30,00 · Mercado · Nubank — balance R$ 470,00",
                BotMessageFormat.transactionConfirmation(messages, LanguageEnum.EN, OperationTypeEnum.EXPENSE, result));
        Assertions.assertEquals("📉 R$ 30,00 · Mercado · Nubank — saldo R$ 470,00",
                BotMessageFormat.transactionConfirmation(messages, LanguageEnum.PT, OperationTypeEnum.EXPENSE, result));

        TransactionRegistrationResultDTO income = new TransactionRegistrationResultDTO(
                new BigDecimal("100"), new BigDecimal("1100"), CurrencyEnum.EUR, "Revolut", "Salario");
        Assertions.assertEquals("📈 € 100,00 · Salario · Revolut — balance € 1.100,00",
                BotMessageFormat.transactionConfirmation(messages, LanguageEnum.EN, OperationTypeEnum.INCOME, income));
    }
}
