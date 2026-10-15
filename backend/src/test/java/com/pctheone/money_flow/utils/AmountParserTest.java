package com.pctheone.money_flow.utils;

import com.pctheone.money_flow.enums.CurrencyEnum;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.Optional;

public class AmountParserTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "50 mil              | 50000.00",
            "50mil               | 50000.00",
            "50 mil reais        | 50000.00",
            "R$ 50 mil           | 50000.00",
            "1,5 mil             | 1500.00",
            "2.5k                | 2500.00",
            "1.250,90            | 1250.90",
            "R$ 1.250,90         | 1250.90",
            "1250.90             | 1250.90",
            "1,250.90            | 1250.90",
            "1250,90             | 1250.90",
            "1.250               | 1250.00",
            "1.250.000           | 1250000.00",
            "25,5                | 25.50",
            "300                 | 300.00",
            "-300                | -300.00",
            "- 300               | -300.00",
            "-1.250,90           | -1250.90",
            "menos 300           | -300.00",
            "negativo 2 mil      | -2000.00",
            "€ 681,03            | 681.03",
            "US$ 10              | 10.00",
            "10 dólares          | 10.00",
            "2 milhões           | 2000000.00",
            "0                   | 0.00",
            "300.                | 300.00"
    })
    void parsesAmounts(String input, String expected) {
        Assertions.assertEquals(Optional.of(new BigDecimal(expected)), AmountParser.parse(input));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "abc", "Nubank, 50 mil reais", "mil", "1,2,3x", "R$"})
    void rejectsNonAmounts(String input) {
        Assertions.assertEquals(Optional.empty(), AmountParser.parse(input));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Nubank, 50 mil reais | BRL",
            "R$ 100               | BRL",
            "brl                  | BRL",
            "€ 50                 | EUR",
            "2000 euros           | EUR",
            "EUR                  | EUR",
            "US$ 10               | USD",
            "$10                  | USD",
            "100 dólares          | USD",
            "usd                  | USD"
    })
    void detectsCurrency(String input, CurrencyEnum expected) {
        Assertions.assertEquals(Optional.of(expected), CurrencyParser.detect(input));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Nubank 500", "Europa", "Realidade"})
    void detectsNoCurrency(String input) {
        Assertions.assertEquals(Optional.empty(), CurrencyParser.detect(input));
    }
}
