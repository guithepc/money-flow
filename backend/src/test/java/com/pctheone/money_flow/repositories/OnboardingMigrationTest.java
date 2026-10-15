package com.pctheone.money_flow.repositories;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.UUID;

//---- Runs V1..V5 on an empty scratch schema, inserts owners, then V6, and checks who skips onboarding.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class OnboardingMigrationTest {

    @Autowired
    DataSource dataSource;

    @Autowired
    JdbcTemplate jdbcTemplate;

    String schema = "v6_test_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

    @AfterEach
    void dropSchema() {
        jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
    }

    @Test
    void onlyOwnersAlreadyUsingTheAppSkipOnboarding() {
        migrate("5");
        Integer linked = insertOwner("linked", 111L);
        Integer withAccount = insertOwner("with-account", null);
        Integer fresh = insertOwner("fresh", null);
        jdbcTemplate.update("INSERT INTO " + schema + ".account (owner_id, description, balance) VALUES (?, 'Nubank', 10)", withAccount);

        migrate("6");

        Assertions.assertEquals("DONE", stepOf(linked));
        Assertions.assertEquals("DONE", stepOf(withAccount));
        Assertions.assertEquals("LANGUAGE", stepOf(fresh));
        Assertions.assertNull(jdbcTemplate.queryForObject("SELECT language FROM " + schema + ".owner WHERE owner_id = ?", String.class, withAccount));
    }

    @Test
    void accountNamesBecomeUniquePerOwnerIgnoringCase() {
        migrate("6");
        Integer owner = insertOwner("owner", null);
        jdbcTemplate.update("INSERT INTO " + schema + ".account (owner_id, description, balance) VALUES (?, 'Nubank', 10)", owner);

        Assertions.assertThrows(Exception.class, () -> jdbcTemplate.update(
                "INSERT INTO " + schema + ".account (owner_id, description, balance) VALUES (?, 'NUBANK', 10)", owner));
    }

    private void migrate(String target) {
        Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .createSchemas(true)
                .locations("classpath:db/migration")
                .target(target)
                .load()
                .migrate();
    }

    private Integer insertOwner(String name, Long chatId) {
        return jdbcTemplate.queryForObject("INSERT INTO " + schema + ".owner (name, email, password_hash, telegram_chat_id) " +
                "VALUES (?, ?, ?, ?) RETURNING owner_id", Integer.class, name, name + "@migration.test", "x".repeat(60), chatId);
    }

    private String stepOf(Integer ownerId) {
        return jdbcTemplate.queryForObject("SELECT onboarding_step::text FROM " + schema + ".owner WHERE owner_id = ?", String.class, ownerId);
    }
}
