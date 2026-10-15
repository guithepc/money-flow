package com.pctheone.money_flow.utils;


import java.math.BigDecimal;
import java.util.Optional;
import java.util.regex.Pattern;

public class MoneyFormat {

    //---- Brazilian format: period only as thousands grouping, comma as decimal separator (max 2 decimals).
    //---- Accepts 1250 | 1250,90 | 1.250,90 | -300,00. Rejects 25.90 (ambiguous), 1e5, 12a.
    private static final Pattern BRAZILIAN_AMOUNT = Pattern.compile("-?(\\d{1,3}(\\.\\d{3})+|\\d+)(,\\d{1,2})?");

    public static String format(String amount){
        return amount.replace(".", "").replace(",", ".");
    }

    public static Optional<BigDecimal> parseBrazilian(String amount){
        if (amount == null || !BRAZILIAN_AMOUNT.matcher(amount).matches())
            return Optional.empty();
        return Optional.of(new BigDecimal(format(amount)));
    }
}
