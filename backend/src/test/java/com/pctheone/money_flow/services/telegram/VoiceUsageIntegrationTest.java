package com.pctheone.money_flow.services.telegram;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pctheone.money_flow.entities.OwnerEntity;
import com.pctheone.money_flow.enums.CurrencyEnum;
import com.pctheone.money_flow.enums.OnboardingStepEnum;
import com.pctheone.money_flow.repositories.OwnerRepository;
import com.pctheone.money_flow.services.TelegramBotServices;
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
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

//---- Daily voice limit (#27) against a real database (Flyway V8, atomic upsert). Telegram and OpenAI are mocked.
@SpringBootTest(properties = {
        "telegram.bot-token=test-token",
        "telegram.webhook-secret=test-secret",
        "jwt.secret=tmpGFRC21ovxZhKhbd11YOC3Kw8YDyg5KcBb1dlMY0c=",
        "jwt.expiration=3600000",
        "voice.daily-limit=20"
})
public class VoiceUsageIntegrationTest {

    private static final ObjectMapper UPDATE_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final String LIMIT_REACHED_PT =
            "🎙️ Você atingiu o limite de 20 áudios por hoje. Amanhã libera de novo — até lá, dá pra registrar por texto: /gasto …";
    private static final String LIMIT_REACHED_ONBOARDING_PT =
            "🎙️ Você atingiu o limite de 20 áudios por hoje. Amanhã libera de novo — até lá, manda a resposta por texto.";

    @MockitoBean
    TelegramApiClient telegramApiClient;

    @MockitoBean
    VoiceTranscriptionService voiceTranscriptionService;

    @MockitoBean
    OpenAITextClient openAITextClient;

    @MockitoBean
    AccountMessageParser accountMessageParser;

    @Autowired
    TelegramBotServices telegramBotServices;

    @Autowired
    VoiceUsageService voiceUsageService;

    @Autowired
    OwnerRepository ownerRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    List<Integer> ownerIds = new ArrayList<>();
    int updateId = ThreadLocalRandom.current().nextInt(1_000_000_000, 2_000_000_000);
    int firstUpdateId = updateId;
    Clock originalClock;

    @BeforeEach
    void setUp() throws Exception {
        originalClock = voiceUsageService.clock;
        Mockito.when(voiceTranscriptionService.transcribe(ArgumentMatchers.anyString())).thenReturn("gastei 30 no mercado");
        Mockito.when(openAITextClient.complete(ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
                .thenReturn(Optional.of("{\"error\": \"NOT_UNDERSTOOD\"}"));
        Mockito.when(accountMessageParser.parse(ArgumentMatchers.anyString()))
                .thenReturn(new AccountMessageParser.ParsedAccount("Nubank", CurrencyEnum.BRL, new BigDecimal("50000")));
    }

    @AfterEach
    void cleanUp() {
        voiceUsageService.clock = originalClock;
        for (Integer ownerId : ownerIds) {
            jdbcTemplate.update("DELETE FROM category WHERE owner_id = ?", ownerId);
            jdbcTemplate.update("DELETE FROM owner WHERE owner_id = ?", ownerId);
        }
        jdbcTemplate.update("DELETE FROM telegram_processed_update WHERE update_id >= ? AND update_id <= ?", firstUpdateId, updateId);
    }

    @Test
    void twentyFirstVoiceMessageOfTheDayNeverReachesOpenAI() throws Exception {
        OwnerEntity owner = owner(OnboardingStepEnum.DONE);

        for (int i = 0; i < 20; i++)
            telegramBotServices.telegramRouter(voice(owner.getTelegramChatId()));
        Mockito.verify(voiceTranscriptionService, Mockito.times(20)).transcribe(ArgumentMatchers.anyString());
        Mockito.verify(openAITextClient, Mockito.times(20)).complete(ArgumentMatchers.anyString(), ArgumentMatchers.anyString());
        Mockito.verify(telegramApiClient, Mockito.never()).sendMessage(owner.getTelegramChatId(), LIMIT_REACHED_PT);

        telegramBotServices.telegramRouter(voice(owner.getTelegramChatId()));

        Mockito.verify(voiceTranscriptionService, Mockito.times(20)).transcribe(ArgumentMatchers.anyString());
        Mockito.verify(openAITextClient, Mockito.times(20)).complete(ArgumentMatchers.anyString(), ArgumentMatchers.anyString());
        Mockito.verify(telegramApiClient).sendMessage(owner.getTelegramChatId(), LIMIT_REACHED_PT);
        Mockito.verify(telegramApiClient).sendMessage(owner.getTelegramChatId(), "🎙️ 3 áudios restantes hoje.");
        Assertions.assertEquals(21, usedToday(owner));
    }

    @Test
    void quotaIsPerOwner() {
        OwnerEntity a = owner(OnboardingStepEnum.DONE);
        OwnerEntity b = owner(OnboardingStepEnum.DONE);

        for (int i = 1; i <= 20; i++)
            Assertions.assertTrue(voiceUsageService.consume(a.getOwnerId()).allowed(), "message " + i);
        Assertions.assertFalse(voiceUsageService.consume(a.getOwnerId()).allowed());

        VoiceUsageService.Quota quotaB = voiceUsageService.consume(b.getOwnerId());
        Assertions.assertTrue(quotaB.allowed());
        Assertions.assertEquals(1, quotaB.used());
        Assertions.assertEquals(19, quotaB.remaining());
    }

    @Test
    void quotaResetsAtMidnightInLisbon() {
        OwnerEntity owner = owner(OnboardingStepEnum.DONE);
        //---- 22:59 UTC = 23:59 in Lisbon (summer time) on the 23rd; 23:00 UTC is already the 24th there.
        voiceUsageService.clock = Clock.fixed(Instant.parse("2026-10-23T22:59:00Z"), VoiceUsageService.ZONE);
        jdbcTemplate.update("INSERT INTO voice_usage (owner_id, usage_date, count) VALUES (?, ?, 20)",
                owner.getOwnerId(), LocalDate.of(2026, 10, 23));
        Assertions.assertFalse(voiceUsageService.consume(owner.getOwnerId()).allowed());

        voiceUsageService.clock = Clock.fixed(Instant.parse("2026-10-23T23:00:00Z"), VoiceUsageService.ZONE);
        VoiceUsageService.Quota nextDay = voiceUsageService.consume(owner.getOwnerId());

        Assertions.assertTrue(nextDay.allowed());
        Assertions.assertEquals(1, nextDay.used());
        Assertions.assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT count FROM voice_usage WHERE owner_id = ? AND usage_date = ?", Integer.class, owner.getOwnerId(), LocalDate.of(2026, 10, 24)));
    }

    @Test
    void onboardingVoiceConsumesTheSameQuota() throws Exception {
        OwnerEntity owner = owner(OnboardingStepEnum.ACCOUNT);

        //---- Onboarding voice and transaction voice share one counter.
        telegramBotServices.telegramRouter(voice(owner.getTelegramChatId()));
        Assertions.assertEquals(1, usedToday(owner));
        jdbcTemplate.update("UPDATE owner SET onboarding_step = 'DONE', onboarding_draft = NULL WHERE owner_id = ?", owner.getOwnerId());
        telegramBotServices.telegramRouter(voice(owner.getTelegramChatId()));
        Assertions.assertEquals(2, usedToday(owner));
        Mockito.verify(voiceTranscriptionService, Mockito.times(2)).transcribe(ArgumentMatchers.anyString());

        //---- Over the limit during onboarding: nothing is transcribed and the step is kept.
        jdbcTemplate.update("UPDATE owner SET onboarding_step = 'ACCOUNT' WHERE owner_id = ?", owner.getOwnerId());
        jdbcTemplate.update("UPDATE voice_usage SET count = 20 WHERE owner_id = ?", owner.getOwnerId());
        telegramBotServices.telegramRouter(voice(owner.getTelegramChatId()));

        Mockito.verify(voiceTranscriptionService, Mockito.times(2)).transcribe(ArgumentMatchers.anyString());
        Mockito.verify(telegramApiClient).sendMessage(owner.getTelegramChatId(), LIMIT_REACHED_ONBOARDING_PT);
        Assertions.assertEquals(OnboardingStepEnum.ACCOUNT, ownerRepository.findById(owner.getOwnerId()).orElseThrow().getOnboardingStep());
    }

    @Test
    void deletingTheOwnerDeletesItsUsage() {
        OwnerEntity owner = owner(OnboardingStepEnum.DONE);
        voiceUsageService.consume(owner.getOwnerId());

        jdbcTemplate.update("DELETE FROM owner WHERE owner_id = ?", owner.getOwnerId());

        Assertions.assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM voice_usage WHERE owner_id = ?", Integer.class, owner.getOwnerId()));
    }

    private OwnerEntity owner(OnboardingStepEnum step) {
        OwnerEntity owner = new OwnerEntity();
        owner.setName("voice");
        owner.setEmail("voice-" + UUID.randomUUID() + "@test.local");
        owner.setPasswordHash("x".repeat(60));
        owner.setTelegramChatId(ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_000_000_000L));
        owner.setLanguage("pt");
        owner.setOnboardingStep(step);
        OwnerEntity saved = ownerRepository.save(owner);
        ownerIds.add(saved.getOwnerId());
        return saved;
    }

    private int usedToday(OwnerEntity owner) {
        return jdbcTemplate.queryForObject("SELECT count FROM voice_usage WHERE owner_id = ? AND usage_date = ?",
                Integer.class, owner.getOwnerId(), LocalDate.now(voiceUsageService.clock));
    }

    private Update voice(long chatId) throws Exception {
        String json = """
                {"update_id": %d, "message": {"message_id": 1, "date": 0,
                  "chat": {"id": %d, "type": "private"},
                  "from": {"id": %d, "is_bot": false, "first_name": "G", "language_code": "pt-br"},
                  "voice": {"file_id": "voice-%d", "file_unique_id": "u", "duration": 2}}}
                """.formatted(updateId++, chatId, chatId, updateId);
        return UPDATE_MAPPER.readValue(json, Update.class);
    }
}
