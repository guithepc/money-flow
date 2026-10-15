package com.pctheone.money_flow.utils;

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
}
