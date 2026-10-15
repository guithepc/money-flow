package com.pctheone.money_flow.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

//---- Deterministic parsing of a typed/spoken balance: "50 mil", "1.250,90", "1250.90", "-300", "R$ 2,5k".
//---- Only accepts input that is an amount (plus currency and multiplier words); anything else is empty.
public final class AmountParser {

    private AmountParser() {
    }

    private static final Pattern CURRENCY_WORDS = Pattern.compile(
            "(?<!\\p{L})(reais|real|brl|euros|euro|eur|d[oó]lares|d[oó]lar|dollars|dollar|usd|bucks)(?!\\p{L})");
    private static final Pattern CURRENCY_SYMBOLS = Pattern.compile("(r\\$|us\\$|€|\\$)");
    private static final Pattern NEGATIVE_WORDS = Pattern.compile("(?<!\\p{L})(menos|negativo|negativa|negative|minus)(?!\\p{L})");
    private static final Pattern MILLION = Pattern.compile("(?<!\\p{L})(milh[ãa]o|milh[õo]es|millions?|mi)(?!\\p{L})");
    private static final Pattern THOUSAND = Pattern.compile("(?<!\\p{L})(mil|k|thousand)(?!\\p{L})");
    private static final Pattern NUMBER = Pattern.compile("[0-9]+([.,][0-9]+)*");

    public static Optional<BigDecimal> parse(String input) {
        if (input == null || input.isBlank())
            return Optional.empty();

        String text = input.toLowerCase(Locale.ROOT).replace('−', '-').trim();
        text = CURRENCY_SYMBOLS.matcher(text).replaceAll(" ");
        text = CURRENCY_WORDS.matcher(text).replaceAll(" ");

        boolean negative = false;
        if (NEGATIVE_WORDS.matcher(text).find()) {
            negative = true;
            text = NEGATIVE_WORDS.matcher(text).replaceAll(" ");
        }

        BigDecimal multiplier = BigDecimal.ONE;
        if (MILLION.matcher(text).find()) {
            multiplier = BigDecimal.valueOf(1_000_000);
            text = MILLION.matcher(text).replaceAll(" ");
        } else if (THOUSAND.matcher(text).find()) {
            multiplier = BigDecimal.valueOf(1_000);
            text = THOUSAND.matcher(text).replaceAll(" ");
        }

        text = text.replaceAll("\\s+", "").replaceAll("[.!?]+$", "");
        if (text.startsWith("-")) {
            negative = true;
            text = text.substring(1);
        } else if (text.startsWith("+")) {
            text = text.substring(1);
        }

        if (!NUMBER.matcher(text).matches())
            return Optional.empty();

        BigDecimal value = new BigDecimal(normalize(text, !multiplier.equals(BigDecimal.ONE)))
                .multiply(multiplier)
                .setScale(2, RoundingMode.HALF_UP);
        return Optional.of(negative ? value.negate() : value);
    }

    //---- "1.250,90" -> "1250.90", "1,250.90" -> "1250.90", "1.250" -> "1250", "25,5" -> "25.5".
    //---- With a multiplier ("1,5 mil") a single separator is always the decimal one.
    static String normalize(String number, boolean hasMultiplier) {
        int lastDot = number.lastIndexOf('.');
        int lastComma = number.lastIndexOf(',');

        if (lastDot >= 0 && lastComma >= 0) {
            char decimal = lastDot > lastComma ? '.' : ',';
            char grouping = decimal == '.' ? ',' : '.';
            return number.replace(String.valueOf(grouping), "").replace(decimal, '.');
        }

        int lastSeparator = Math.max(lastDot, lastComma);
        if (lastSeparator < 0)
            return number;

        char separator = number.charAt(lastSeparator);
        long separators = number.chars().filter(c -> c == separator).count();
        int digitsAfter = number.length() - lastSeparator - 1;
        boolean grouping = separators > 1 || (digitsAfter == 3 && !hasMultiplier);
        return grouping
                ? number.replace(String.valueOf(separator), "")
                : number.replace(separator, '.');
    }
}
