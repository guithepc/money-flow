package com.pctheone.money_flow.services;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pctheone.money_flow.entities.OwnerEntity;
import com.pctheone.money_flow.enums.CurrencyEnum;
import com.pctheone.money_flow.enums.LanguageEnum;
import com.pctheone.money_flow.enums.OnboardingStepEnum;
import com.pctheone.money_flow.repositories.OwnerRepository;
import com.pctheone.money_flow.services.telegram.AccountMessageParser;
import com.pctheone.money_flow.services.telegram.BotMessages;
import com.pctheone.money_flow.services.telegram.TelegramApiClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

//---- Full onboarding through the webhook router against a real database (Flyway V6, row locks, JSON draft, seed).
//---- Telegram and the GPT account parser are mocked.
@SpringBootTest(properties = {
        "telegram.bot-token=test-token",
        "telegram.webhook-secret=test-secret",
        "jwt.secret=tmpGFRC21ovxZhKhbd11YOC3Kw8YDyg5KcBb1dlMY0c=",
        "jwt.expiration=3600000"
})
public class OnboardingFlowIntegrationTest {

    private static final ObjectMapper UPDATE_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @MockitoBean
    TelegramApiClient telegramApiClient;

    @MockitoBean
    AccountMessageParser accountMessageParser;

    @Autowired
    TelegramBotServices telegramBotServices;

    @Autowired
    OwnerService ownerService;

    @Autowired
    OwnerRepository ownerRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    BotMessages messages;

    long chatId = ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_000_000_000L);
    int updateId = ThreadLocalRandom.current().nextInt(1_000_000_000, 2_000_000_000);
    Integer ownerId;

    @BeforeEach
    void setUp() {
        OwnerEntity owner = new OwnerEntity();
        owner.setName("onboarding");
        owner.setEmail("onboarding-" + UUID.randomUUID() + "@test.local");
        owner.setPasswordHash("x".repeat(60));
        ownerId = ownerRepository.save(owner).getOwnerId();
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM transactions WHERE owner_id = ?", ownerId);
        jdbcTemplate.update("DELETE FROM account WHERE owner_id = ?", ownerId);
        jdbcTemplate.update("DELETE FROM category WHERE owner_id = ?", ownerId);
        jdbcTemplate.update("DELETE FROM owner WHERE owner_id = ?", ownerId);
        jdbcTemplate.update("DELETE FROM telegram_processed_update WHERE update_id >= ? AND update_id < ?", updateId - 100, updateId + 100);
    }

    @Test
    void newOwnerGoesThroughTheWholeOnboarding() {
        Assertions.assertEquals(OnboardingStepEnum.LANGUAGE, owner().getOnboardingStep());

        //---- /start links and asks for the language
        route(text("/start " + invite()));
        Mockito.verify(telegramApiClient).sendMessage(chatId, messages.get(LanguageEnum.PT, "link.success"));
        Mockito.verify(telegramApiClient).sendMessageWithInlineKeyboard(ArgumentMatchers.eq(chatId),
                ArgumentMatchers.eq(messages.get(LanguageEnum.PT, "onboarding.language.question")), ArgumentMatchers.any());

        //---- Double click on the language button: categories seeded once
        route(callback("onb:lang:pt"));
        route(callback("onb:lang:pt"));
        Assertions.assertEquals(OnboardingStepEnum.ACCOUNT, owner().getOnboardingStep());
        Assertions.assertEquals("pt", owner().getLanguage());
        List<String> categories = jdbcTemplate.queryForList(
                "SELECT description || ':' || type FROM category WHERE owner_id = ? ORDER BY category_id", String.class, ownerId);
        Assertions.assertEquals(List.of("Mercado:EXPENSE", "Restaurantes:EXPENSE", "Transporte:EXPENSE", "Casa:EXPENSE",
                "Salário:INCOME", "Renda extra:INCOME"), categories);

        //---- Account without currency: draft is persisted while the currency is asked
        Mockito.when(accountMessageParser.parse("Nubank, 50 mil"))
                .thenReturn(new AccountMessageParser.ParsedAccount("Nubank", null, new BigDecimal("50000.00")));
        route(text("Nubank, 50 mil"));
        Assertions.assertEquals(OnboardingStepEnum.ACCOUNT, owner().getOnboardingStep());
        Assertions.assertTrue(owner().getOnboardingDraft().contains("Nubank"), owner().getOnboardingDraft());
        route(callback("onb:cur:BRL"));
        Assertions.assertEquals(OnboardingStepEnum.ACCOUNT_CONFIRM, owner().getOnboardingStep());

        //---- Double click on "Yes": one account, balance set directly, no transaction
        route(callback("onb:ok"));
        route(callback("onb:ok"));
        List<Map<String, Object>> accounts = jdbcTemplate.queryForList("SELECT account_id, description, balance, currency::text AS currency FROM account WHERE owner_id = ?", ownerId);
        Assertions.assertEquals(1, accounts.size());
        Assertions.assertEquals("Nubank", accounts.getFirst().get("description"));
        Assertions.assertEquals(0, new BigDecimal("50000").compareTo((BigDecimal) accounts.getFirst().get("balance")));
        Assertions.assertEquals(CurrencyEnum.BRL.name(), accounts.getFirst().get("currency"));
        Assertions.assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM transactions WHERE owner_id = ?", Integer.class, ownerId));
        Assertions.assertEquals(OnboardingStepEnum.MORE_ACCOUNTS, owner().getOnboardingStep());
        Assertions.assertNull(owner().getOnboardingDraft());

        //---- Same name again is refused
        route(callback("onb:more"));
        Mockito.when(accountMessageParser.parse("nubank 10 reais"))
                .thenReturn(new AccountMessageParser.ParsedAccount("nubank", CurrencyEnum.BRL, new BigDecimal("10")));
        route(text("nubank 10 reais"));
        Mockito.verify(telegramApiClient).sendMessage(chatId, messages.get(LanguageEnum.PT, "onboarding.account.duplicate", "nubank"));
        route(text("Cartão"));
        route(text("sim"));
        Assertions.assertEquals(2, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM account WHERE owner_id = ?", Integer.class, ownerId));

        //---- Continue -> tutorial; first transaction finishes onboarding
        route(callback("onb:next"));
        Assertions.assertEquals(OnboardingStepEnum.TUTORIAL, owner().getOnboardingStep());
        Integer nubankId = (Integer) accounts.getFirst().get("account_id");
        route(text("/gasto mercado feira 30 " + nubankId));
        Assertions.assertEquals(OnboardingStepEnum.DONE, owner().getOnboardingStep());
        Assertions.assertEquals(0, new BigDecimal("49970").compareTo(
                jdbcTemplate.queryForObject("SELECT balance FROM account WHERE account_id = ?", BigDecimal.class, nubankId)));

        //---- Re-linking a DONE owner does not restart onboarding
        Mockito.reset(telegramApiClient);
        route(text("/start " + invite()));
        Mockito.verify(telegramApiClient).sendMessage(chatId, messages.get(LanguageEnum.PT, "link.success"));
        Mockito.verify(telegramApiClient, Mockito.never()).sendMessageWithInlineKeyboard(ArgumentMatchers.anyLong(), ArgumentMatchers.anyString(), ArgumentMatchers.any());
        Assertions.assertEquals(OnboardingStepEnum.DONE, owner().getOnboardingStep());
    }

    @Test
    void cancelAtAnyStepFinishesWithCategories() {
        route(text("/start " + invite()));

        route(text("/cancelar"));

        Assertions.assertEquals(OnboardingStepEnum.DONE, owner().getOnboardingStep());
        Assertions.assertEquals(6, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM category WHERE owner_id = ?", Integer.class, ownerId));
    }

    private OwnerEntity owner() {
        return ownerRepository.findById(ownerId).orElseThrow();
    }

    private String invite() {
        String link = ownerService.generateTelegramInvite(ownerId);
        return link.substring(link.indexOf("start=") + "start=".length());
    }

    private void route(Update update) {
        telegramBotServices.telegramRouter(update);
    }

    private Update text(String text) {
        return parse("""
                {"update_id": %d, "message": {"message_id": 1, "date": 0,
                  "chat": {"id": %d, "type": "private"},
                  "from": {"id": %d, "is_bot": false, "first_name": "G", "language_code": "pt-br"},
                  "text": "%s"}}
                """.formatted(updateId++, chatId, chatId, text));
    }

    private Update callback(String data) {
        return parse("""
                {"update_id": %d, "callback_query": {"id": "cb", "chat_instance": "ci", "data": "%s",
                  "from": {"id": %d, "is_bot": false, "first_name": "G", "language_code": "pt-br"},
                  "message": {"message_id": 10, "date": 1, "chat": {"id": %d, "type": "private"}, "text": "pick one"}}}
                """.formatted(updateId++, data, chatId, chatId));
    }

    private Update parse(String json) {
        try {
            return UPDATE_MAPPER.readValue(json, Update.class);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
