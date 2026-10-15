package com.pctheone.money_flow.utils;

import com.pctheone.money_flow.enums.CurrencyEnum;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

//---- Deterministic currency detection from free text: "R$", "reais", "BRL", "€", "euros", "US$", "dólares", "USD"...
public final class CurrencyParser {

    private CurrencyParser() {
    }

    private static final Pattern BRL = Pattern.compile("r\\$|(?<!\\p{L})(reais|real|brl)(?!\\p{L})");
    private static final Pattern USD = Pattern.compile("us\\$|\\$|(?<!\\p{L})(d[oó]lares|d[oó]lar|dollars?|usd|bucks)(?!\\p{L})");
    private static final Pattern EUR = Pattern.compile("€|(?<!\\p{L})(euros?|eur)(?!\\p{L})");

    public static Optional<CurrencyEnum> detect(String input) {
        if (input == null || input.isBlank())
            return Optional.empty();
        String text = input.toLowerCase(Locale.ROOT);
        //---- BRL first: "R$" also contains "$".
        if (BRL.matcher(text).find())
            return Optional.of(CurrencyEnum.BRL);
        if (EUR.matcher(text).find())
            return Optional.of(CurrencyEnum.EUR);
        if (USD.matcher(text).find())
            return Optional.of(CurrencyEnum.USD);
        return Optional.empty();
    }
}
