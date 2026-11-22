package com.pctheone.money_flow.services.telegram;

import com.pctheone.money_flow.enums.LanguageEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

//---- Daily caps per owner on messages that cost OpenAI calls.
//---- Voice (#27): a Whisper transcription plus a GPT parse, checked before the audio is downloaded, on every voice path.
//---- Text (PCT-66): a GPT parse of a typed message without a command. Separate and higher cap, so typing stays the
//---- fallback when the voice cap is reached.
@Service
public class VoiceUsageService {

    private static final Logger log = LoggerFactory.getLogger(VoiceUsageService.class);

    //---- Same zone as the server; revisit when there is a per-user timezone.
    static final ZoneId ZONE = ZoneId.of("Europe/Lisbon");

    //---- Voice messages left today that trigger the "N left" notice.
    static final int LOW_QUOTA_NOTICE_AT = 3;

    //---- Message keys in messages/bot_*.properties.
    static final String LIMIT_REACHED_MESSAGE = "voice.dailyLimitReached";
    static final String LIMIT_REACHED_ONBOARDING_MESSAGE = "voice.dailyLimitReached.onboarding";
    static final String REMAINING_MESSAGE = "voice.remainingToday";
    static final String TEXT_LIMIT_REACHED_MESSAGE = "text.dailyLimitReached";

    @Value("${voice.daily-limit:20}")
    int dailyLimit;

    @Value("${text.daily-limit:100}")
    int textDailyLimit;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    BotMessages messages;

    Clock clock = Clock.system(ZONE);

    //---- used includes the message being checked. Over the limit, nothing that costs money may run.
    public record Quota(int used, int limit) {

        public boolean allowed() {
            return used <= limit;
        }

        public int remaining() {
            return Math.max(0, limit - used);
        }
    }

    //---- Counts the voice message (atomic upsert, also over the limit) and tells whether it may be transcribed.
    public Quota consume(Integer ownerId) {
        Integer used = jdbcTemplate.queryForObject("""
                INSERT INTO voice_usage (owner_id, usage_date, count) VALUES (?, ?, 1)
                ON CONFLICT (owner_id, usage_date) DO UPDATE SET count = voice_usage.count + 1
                RETURNING count
                """, Integer.class, ownerId, LocalDate.now(clock));
        Quota quota = new Quota(used == null ? 0 : used, dailyLimit);
        if (!quota.allowed())
            log.info("Owner {} is over the daily voice limit ({}/{}), voice message skipped before transcription.",
                    ownerId, quota.used(), quota.limit());
        return quota;
    }

    //---- Same as consume, for typed messages parsed by GPT.
    public Quota consumeText(Integer ownerId) {
        Integer used = jdbcTemplate.queryForObject("""
                INSERT INTO voice_usage (owner_id, usage_date, text_count) VALUES (?, ?, 1)
                ON CONFLICT (owner_id, usage_date) DO UPDATE SET text_count = voice_usage.text_count + 1
                RETURNING text_count
                """, Integer.class, ownerId, LocalDate.now(clock));
        Quota quota = new Quota(used == null ? 0 : used, textDailyLimit);
        if (!quota.allowed())
            log.info("Owner {} is over the daily text limit ({}/{}), text message skipped before parsing.",
                    ownerId, quota.used(), quota.limit());
        return quota;
    }

    public String textLimitReachedMessage(LanguageEnum language) {
        return messages.get(language, TEXT_LIMIT_REACHED_MESSAGE, textDailyLimit);
    }

    public int dailyLimit() {
        return dailyLimit;
    }

    //---- During onboarding the /gasto hint makes no sense: the user is asked to type the answer instead.
    public String limitReachedMessage(LanguageEnum language, boolean onboarding) {
        return messages.get(language, onboarding ? LIMIT_REACHED_ONBOARDING_MESSAGE : LIMIT_REACHED_MESSAGE, dailyLimit);
    }

    //---- "3 áudios restantes hoje", sent once a day right after the reply to the message that leaves 3.
    public Optional<String> lowQuotaNotice(Quota quota, LanguageEnum language) {
        if (!quota.allowed() || quota.remaining() != LOW_QUOTA_NOTICE_AT)
            return Optional.empty();
        return Optional.of(messages.get(language, REMAINING_MESSAGE, quota.remaining()));
    }
}
