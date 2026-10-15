package com.pctheone.money_flow.services.telegram;

import com.pctheone.money_flow.enums.CurrencyEnum;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

//---- GPT output handling only: the OpenAI call itself is never made in tests.
public class AccountMessageParserTest {

    @Test
    void gptAmountTextIsParsedDeterministically() {
        AccountMessageParser.ParsedAccount parsed = AccountMessageParser.fromJson(
                "{\"name\": \"Nubank\", \"currency\": \"BRL\", \"amount\": \"50 mil\"}");

        Assertions.assertEquals(new AccountMessageParser.ParsedAccount("Nubank", CurrencyEnum.BRL, new BigDecimal("50000.00")), parsed);
    }

    @Test
    void negativeAndDecimalAmounts() {
        Assertions.assertEquals(new BigDecimal("-300.00"),
                AccountMessageParser.fromJson("{\"name\": \"Itaú\", \"currency\": \"BRL\", \"amount\": \"menos 300\"}").balance());
        Assertions.assertEquals(new BigDecimal("1250.90"),
                AccountMessageParser.fromJson("{\"name\": \"Wise\", \"currency\": \"EUR\", \"amount\": \"1.250,90\"}").balance());
        Assertions.assertEquals(new BigDecimal("1250.90"),
                AccountMessageParser.fromJson("{\"name\": \"Chase\", \"currency\": \"USD\", \"amount\": \"1250.90\"}").balance());
    }

    @Test
    void missingOrUnparseableFieldsAreNull() {
        AccountMessageParser.ParsedAccount parsed = AccountMessageParser.fromJson(
                "{\"name\": \"Revolut\", \"currency\": null, \"amount\": \"muito\"}");

        Assertions.assertEquals("Revolut", parsed.name());
        Assertions.assertNull(parsed.currency());
        Assertions.assertNull(parsed.balance());
        Assertions.assertTrue(AccountMessageParser.fromJson("{\"name\": null, \"currency\": null, \"amount\": null}").isEmpty());
    }

    @Test
    void currencyWordsFromGptAreAccepted() {
        Assertions.assertEquals(CurrencyEnum.EUR,
                AccountMessageParser.fromJson("{\"name\": \"Wise\", \"currency\": \"euros\", \"amount\": \"10\"}").currency());
    }

    @Test
    void namesAreCleaned() {
        Assertions.assertEquals("Nubank", AccountMessageParser.cleanName("  Nubank. "));
        Assertions.assertEquals("C6 Bank", AccountMessageParser.cleanName("C6   Bank"));
        Assertions.assertNull(AccountMessageParser.cleanName(" ... "));
        Assertions.assertNull(AccountMessageParser.cleanName(null));
    }
}
