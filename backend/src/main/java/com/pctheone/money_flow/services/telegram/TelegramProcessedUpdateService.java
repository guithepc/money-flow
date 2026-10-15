package com.pctheone.money_flow.services.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

//---- Idempotency for Telegram webhook retries: an update_id is claimed once, before it is processed.
@Service
public class TelegramProcessedUpdateService {

    private static final Logger log = LoggerFactory.getLogger(TelegramProcessedUpdateService.class);

    //---- Telegram keeps undelivered updates for 24h, so a week is plenty.
    static final int RETENTION_DAYS = 7;

    @Autowired
    JdbcTemplate jdbcTemplate;

    //---- Returns true only for the first caller with this update_id.
    public boolean markAsProcessed(long updateId) {
        int inserted = jdbcTemplate.update(
                "INSERT INTO telegram_processed_update (update_id) VALUES (?) ON CONFLICT (update_id) DO NOTHING",
                updateId);
        return inserted == 1;
    }

    @Scheduled(cron = "0 0 4 * * *")
    public void purgeOldUpdates() {
        int deleted = jdbcTemplate.update(
                "DELETE FROM telegram_processed_update WHERE processed_at < now() - make_interval(days => ?)",
                RETENTION_DAYS);
        log.info("Purged {} processed Telegram updates older than {} days.", deleted, RETENTION_DAYS);
    }
}
