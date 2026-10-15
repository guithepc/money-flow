package com.pctheone.money_flow.services.telegram;

import com.pctheone.money_flow.enums.CategoryTypeEnum;
import com.pctheone.money_flow.enums.CurrencyEnum;
import com.pctheone.money_flow.enums.LanguageEnum;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public class BotMessagesTest {

    BotMessages messages = new BotMessages();

    @Test
    void portugueseAndEnglishBundlesHaveTheSameKeys() throws Exception {
        Properties pt = load("messages/bot_pt.properties");
        Properties en = load("messages/bot_en.properties");

        Assertions.assertEquals(pt.stringPropertyNames(), en.stringPropertyNames());
    }

    @Test
    void everyMessageRendersInBothLanguages() throws Exception {
        for (String key : load("messages/bot_pt.properties").stringPropertyNames()) {
            for (LanguageEnum language : LanguageEnum.values()) {
                String text = messages.get(language, key, "A", "B", "C", "D", "E");
                Assertions.assertFalse(text.isBlank(), key);
                Assertions.assertFalse(text.contains("{"), language + " " + key + " -> " + text);
            }
        }
    }

    @Test
    void apostrophesAndArgumentsAreRendered() {
        Assertions.assertEquals("🤔 Couldn't find \"Pharmacy\". Create it as expense?",
                messages.get(LanguageEnum.EN, "voice.categoryNotFound", "Pharmacy", messages.get(LanguageEnum.EN, "category.type." + CategoryTypeEnum.EXPENSE)));
        Assertions.assertEquals("🤔 Não encontrei a categoria \"Farmácia\". Quer criar como gasto?",
                messages.get(LanguageEnum.PT, "voice.categoryNotFound", "Farmácia", messages.get(LanguageEnum.PT, "category.type." + CategoryTypeEnum.EXPENSE)));
        Assertions.assertEquals("🧐 Nubank · R$ 50.000,00 — correct?",
                messages.get(LanguageEnum.EN, "onboarding.confirm.question", "Nubank",
                        com.pctheone.money_flow.utils.MoneyFormat.format(new BigDecimal("50000"), CurrencyEnum.BRL)));
    }

    private Properties load(String path) throws Exception {
        Properties properties = new Properties();
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(path)) {
            Assertions.assertNotNull(stream, path);
            properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        return properties;
    }
}
