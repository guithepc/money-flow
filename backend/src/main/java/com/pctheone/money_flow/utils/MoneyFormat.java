package com.pctheone.money_flow.utils;

import com.pctheone.money_flow.enums.CurrencyEnum;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

public class MoneyFormat {

    private static final Locale PT_BR = Locale.of("pt", "BR");

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

    //---- Display rule (bot + dashboard): currency symbol first, then the amount in pt-BR notation
    //---- (dot thousands, comma decimals, always 2 decimals). A negative sign goes before the symbol.
    //---- BRL -> "R$ 1.250,90" | EUR -> "€ 681,03" | USD -> "US$ 10,00" | negative -> "-R$ 12,00"
    //---- Same notation users type amounts in, so it never depends on the currency's home locale.
    public static String format(BigDecimal amount, CurrencyEnum currency){
        BigDecimal value = amount == null ? BigDecimal.ZERO : amount.setScale(2, RoundingMode.HALF_UP);
        DecimalFormat decimalFormat = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(PT_BR));
        String number = decimalFormat.format(value.abs());
        String sign = value.signum() < 0 ? "-" : "";
        return sign + symbol(currency) + " " + number;
    }

    public static String symbol(CurrencyEnum currency){
        if (currency == null) return "";
        return switch (currency) {
            case BRL -> "R$";
            case EUR -> "€";
            case USD -> "US$";
        };
    }
}
