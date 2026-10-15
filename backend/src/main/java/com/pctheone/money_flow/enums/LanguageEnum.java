package com.pctheone.money_flow.enums;

//---- Bot language, stored lowercase in owner.language ("pt" / "en").
public enum LanguageEnum {
    PT("pt"),
    EN("en");

    private final String code;

    LanguageEnum(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    //---- Returns null for an unknown/empty code.
    public static LanguageEnum fromCode(String code) {
        for (LanguageEnum language : values())
            if (language.code.equalsIgnoreCase(code))
                return language;
        return null;
    }

    //---- Telegram language_code ("pt-br", "en", null). Same rule as /ajuda: Portuguese only for "pt*".
    public static LanguageEnum fromTelegram(String languageCode) {
        return languageCode != null && languageCode.toLowerCase().startsWith("pt") ? PT : EN;
    }

    //---- Saved language wins; otherwise follow the Telegram client.
    public static LanguageEnum resolve(String ownerLanguage, String telegramLanguageCode) {
        LanguageEnum saved = fromCode(ownerLanguage);
        return saved != null ? saved : fromTelegram(telegramLanguageCode);
    }
}
