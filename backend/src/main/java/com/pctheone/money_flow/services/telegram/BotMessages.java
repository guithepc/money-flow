package com.pctheone.money_flow.services.telegram;

import com.pctheone.money_flow.enums.LanguageEnum;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.stereotype.Component;

import java.util.Locale;

//---- Bot replies in PT/EN, from messages/bot_pt.properties and messages/bot_en.properties.
//---- Messages always go through MessageFormat: a literal apostrophe must be written as ''.
@Component
public class BotMessages {

    private final ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();

    public BotMessages() {
        messageSource.setBasename("messages/bot");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);
        messageSource.setAlwaysUseMessageFormat(true);
    }

    public String get(LanguageEnum language, String key, Object... args) {
        return messageSource.getMessage(key, args, Locale.of(language.code()));
    }
}
