package com.pctheone.money_flow.services.telegram;

import com.openai.client.OpenAIClient;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.pctheone.money_flow.enums.CurrencyEnum;
import com.pctheone.money_flow.utils.AmountParser;
import com.pctheone.money_flow.utils.CurrencyParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

//---- Extracts name / currency / balance from a free-form onboarding message ("Nubank, 50 mil reais").
//---- GPT only splits the message; the amount is parsed deterministically by AmountParser.
@Service
public class AccountMessageParser {

    private static final Logger log = LoggerFactory.getLogger(AccountMessageParser.class);

    public record ParsedAccount(String name, CurrencyEnum currency, BigDecimal balance) {
        public boolean isEmpty() {
            return name == null && currency == null && balance == null;
        }
    }

    @Value("classpath:prompts/account-parser.txt")
    Resource accountParserPrompt;

    @Lazy
    @Autowired
    OpenAIClient openAIClient;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int MAX_NAME_LENGTH = 255;

    //---- Never throws: a GPT/parsing failure just means nothing was understood.
    public ParsedAccount parse(String text) {
        ParsedAccount parsed;
        try {
            parsed = parseWithGpt(text);
        } catch (Exception e) {
            log.error("Failed to parse account message with GPT: {}", e.getMessage(), e);
            parsed = new ParsedAccount(null, null, null);
        }

        //---- Deterministic fallbacks when GPT missed something the text clearly has.
        CurrencyEnum currency = parsed.currency() != null ? parsed.currency() : CurrencyParser.detect(text).orElse(null);
        BigDecimal balance = parsed.balance() != null ? parsed.balance() : AmountParser.parse(text).orElse(null);
        return new ParsedAccount(parsed.name(), currency, balance);
    }

    private ParsedAccount parseWithGpt(String text) throws IOException {
        String prompt = accountParserPrompt.getContentAsString(StandardCharsets.UTF_8);

        log.info("Sending account message to GPT for parsing");
        ResponseCreateParams params = ResponseCreateParams.builder()
                .model("gpt-4o-mini")
                .instructions(prompt)
                .input(text)
                .build();
        Response response = openAIClient.responses().create(params);

        String json = response.output().stream()
                .flatMap(item -> item.asMessage().content().stream())
                .map(content -> content.asOutputText().text())
                .findFirst()
                .orElse("{}");
        log.info("GPT account parser response: {}", json);
        return fromJson(json);
    }

    static ParsedAccount fromJson(String json) {
        JsonNode node = OBJECT_MAPPER.readTree(json);
        String name = textOrNull(node, "name");
        String currencyText = textOrNull(node, "currency");
        String amountText = textOrNull(node, "amount");

        CurrencyEnum currency = null;
        if (currencyText != null) {
            try {
                currency = CurrencyEnum.valueOf(currencyText.toUpperCase());
            } catch (IllegalArgumentException e) {
                currency = CurrencyParser.detect(currencyText).orElse(null);
            }
        }
        BigDecimal balance = amountText == null ? null : AmountParser.parse(amountText).orElse(null);
        return new ParsedAccount(cleanName(name), currency, balance);
    }

    //---- "Nubank." -> "Nubank". Returns null when nothing is left.
    public static String cleanName(String name) {
        if (name == null)
            return null;
        String cleaned = name.trim()
                .replaceAll("^[\\p{Punct}\\s]+|[\\p{Punct}\\s]+$", "")
                .replaceAll("\\s+", " ");
        if (cleaned.isEmpty())
            return null;
        return cleaned.length() > MAX_NAME_LENGTH ? cleaned.substring(0, MAX_NAME_LENGTH) : cleaned;
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull())
            return null;
        String text = value.asString().trim();
        return text.isEmpty() ? null : text;
    }
}
