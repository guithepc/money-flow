package com.pctheone.money_flow.repositories;

import com.pctheone.money_flow.entities.OwnerEntity;
import com.pctheone.money_flow.entities.PasswordResetTokenEntity;
import com.pctheone.money_flow.enums.OnboardingStepEnum;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

//---- password_reset_token and owner.password_changed_at (V7).
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class PasswordResetTokenRepositoryTest {

    @Autowired
    PasswordResetTokenRepository tokenRepository;

    @Autowired
    OwnerRepository ownerRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    OwnerEntity ownerA;
    OwnerEntity ownerB;
    LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

    @BeforeEach
    void setUp() {
        ownerA = createOwner("a");
        ownerB = createOwner("b");
    }

    @Test
    void tokenIsFoundByItsHash() {
        PasswordResetTokenEntity saved = token(ownerA, null);
        tokenRepository.flush();

        PasswordResetTokenEntity found = tokenRepository.findByTokenHashForUpdate(saved.getTokenHash()).orElseThrow();
        Assertions.assertEquals(ownerA.getOwnerId(), found.getOwnerId());
        Assertions.assertEquals(now.plusMinutes(30), found.getExpiresAt());
        Assertions.assertTrue(tokenRepository.findByTokenHashForUpdate("f".repeat(64)).isEmpty());
    }

    @Test
    void deleteUnusedKeepsUsedTokensAndOtherOwners() {
        PasswordResetTokenEntity unused = token(ownerA, null);
        PasswordResetTokenEntity used = token(ownerA, now);
        PasswordResetTokenEntity otherOwner = token(ownerB, null);
        tokenRepository.flush();

        Assertions.assertEquals(1, tokenRepository.deleteUnusedByOwnerId(ownerA.getOwnerId()));

        Assertions.assertEquals(0, count(unused));
        Assertions.assertEquals(1, count(used));
        Assertions.assertEquals(1, count(otherOwner));
    }

    @Test
    void tokenHashIsUnique() {
        String hash = token(ownerA, null).getTokenHash();
        tokenRepository.flush();

        Assertions.assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "INSERT INTO password_reset_token (owner_id, token_hash, expires_at) VALUES (?, ?, now())", ownerB.getOwnerId(), hash));
    }

    @Test
    void tokensAreDeletedWithTheOwner() {
        PasswordResetTokenEntity saved = token(ownerA, null);
        tokenRepository.flush();

        jdbcTemplate.update("DELETE FROM owner WHERE owner_id = ?", ownerA.getOwnerId());

        Assertions.assertEquals(0, count(saved));
    }

    @Test
    void passwordChangedAtIsPersisted() {
        ownerA.setPasswordChangedAt(now);
        ownerRepository.saveAndFlush(ownerA);

        LocalDateTime stored = jdbcTemplate.queryForObject("SELECT password_changed_at FROM owner WHERE owner_id = ?",
                LocalDateTime.class, ownerA.getOwnerId());
        Assertions.assertEquals(now, stored);
    }

    @Test
    void emailLookupIgnoresCase() {
        Assertions.assertEquals(1, ownerRepository.findAllByEmailIgnoreCase(ownerA.getEmail().toUpperCase()).size());
    }

    private OwnerEntity createOwner(String prefix) {
        String email = prefix + "-" + UUID.randomUUID() + "@reset.test";
        return ownerRepository.save(new OwnerEntity(null, prefix, email, null, "x".repeat(60), null, null, null, OnboardingStepEnum.DONE, null, null));
    }

    private PasswordResetTokenEntity token(OwnerEntity owner, LocalDateTime usedAt) {
        PasswordResetTokenEntity token = new PasswordResetTokenEntity();
        token.setOwnerId(owner.getOwnerId());
        token.setTokenHash(UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", ""));
        token.setCreatedAt(now);
        token.setExpiresAt(now.plusMinutes(30));
        token.setUsedAt(usedAt);
        return tokenRepository.save(token);
    }

    private int count(PasswordResetTokenEntity token) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM password_reset_token WHERE id = ?", Integer.class, token.getId());
    }
}
