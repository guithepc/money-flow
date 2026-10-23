package com.pctheone.money_flow.services;

import com.pctheone.money_flow.dto.RefreshRotationDTO;
import com.pctheone.money_flow.entities.OwnerEntity;
import com.pctheone.money_flow.repositories.OwnerRepository;
import com.pctheone.money_flow.services.telegram.TelegramApiClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Optional;
import java.util.UUID;

//---- Refresh token rotation, reuse detection and revocation against a real database (Flyway V7).
@SpringBootTest(properties = {
        "telegram.bot-token=test-token",
        "telegram.webhook-secret=test-secret",
        "jwt.secret=tmpGFRC21ovxZhKhbd11YOC3Kw8YDyg5KcBb1dlMY0c=",
        "jwt.expiration=3600000"
})
public class RefreshTokenServiceTest {

    @MockitoBean
    TelegramApiClient telegramApiClient;

    @Autowired
    RefreshTokenService refreshTokenService;

    @Autowired
    OwnerRepository ownerRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    Integer ownerId;

    @BeforeEach
    void setUp() {
        OwnerEntity owner = new OwnerEntity();
        owner.setName("refresh");
        owner.setEmail("refresh-" + UUID.randomUUID() + "@test.local");
        owner.setPasswordHash("x".repeat(60));
        ownerId = ownerRepository.save(owner).getOwnerId();
    }

    @AfterEach
    void cleanUp() {
        //---- refresh_token rows go with the owner (ON DELETE CASCADE).
        jdbcTemplate.update("DELETE FROM owner WHERE owner_id = ?", ownerId);
    }

    @Test
    void onlyTheHashIsStored() {
        String raw = refreshTokenService.issue(ownerId, "JUnit");

        Integer plain = jdbcTemplate.queryForObject("SELECT count(*) FROM refresh_token WHERE token_hash = ?", Integer.class, raw);
        Integer forOwner = jdbcTemplate.queryForObject("SELECT count(*) FROM refresh_token WHERE owner_id = ?", Integer.class, ownerId);
        Assertions.assertEquals(0, plain);
        Assertions.assertEquals(1, forOwner);
    }

    @Test
    void rotationReturnsNewTokenAndRetiresTheOldOne() {
        String first = refreshTokenService.issue(ownerId, "JUnit");

        RefreshRotationDTO rotation = refreshTokenService.rotate(first, "JUnit").orElseThrow();

        Assertions.assertEquals(ownerId, rotation.getOwnerId());
        Assertions.assertNotEquals(first, rotation.getRefreshToken());
        //---- The rotated token keeps working, again and again.
        RefreshRotationDTO second = refreshTokenService.rotate(rotation.getRefreshToken(), "JUnit").orElseThrow();
        Assertions.assertTrue(refreshTokenService.rotate(second.getRefreshToken(), "JUnit").isPresent());
    }

    @Test
    void reusingARotatedTokenRevokesTheWholeFamily() {
        String first = refreshTokenService.issue(ownerId, "JUnit");
        String second = refreshTokenService.rotate(first, "JUnit").orElseThrow().getRefreshToken();

        Assertions.assertTrue(refreshTokenService.rotate(first, "attacker").isEmpty());

        //---- The legitimate (latest) token of the family is dead too.
        Assertions.assertTrue(refreshTokenService.rotate(second, "JUnit").isEmpty());
        Integer active = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM refresh_token WHERE owner_id = ? AND revoked_at IS NULL", Integer.class, ownerId);
        Assertions.assertEquals(0, active);
    }

    @Test
    void reuseOnlyRevokesItsOwnFamily() {
        String laptop = refreshTokenService.issue(ownerId, "laptop");
        String phone = refreshTokenService.issue(ownerId, "phone");
        refreshTokenService.rotate(laptop, "laptop").orElseThrow();

        Assertions.assertTrue(refreshTokenService.rotate(laptop, "laptop").isEmpty());

        Assertions.assertTrue(refreshTokenService.rotate(phone, "phone").isPresent());
    }

    @Test
    void expiredTokenIsRejected() {
        String raw = refreshTokenService.issue(ownerId, "JUnit");
        jdbcTemplate.update("UPDATE refresh_token SET expires_at = now() - interval '1 minute' WHERE owner_id = ?", ownerId);

        Assertions.assertTrue(refreshTokenService.rotate(raw, "JUnit").isEmpty());
    }

    @Test
    void unknownOrMissingTokenIsRejected() {
        Assertions.assertTrue(refreshTokenService.rotate("not-a-token", "JUnit").isEmpty());
        Assertions.assertTrue(refreshTokenService.rotate(null, "JUnit").isEmpty());
        Assertions.assertTrue(refreshTokenService.rotate("", "JUnit").isEmpty());
    }

    @Test
    void revokedSessionCannotRefresh() {
        String raw = refreshTokenService.issue(ownerId, "JUnit");
        String rotated = refreshTokenService.rotate(raw, "JUnit").orElseThrow().getRefreshToken();

        refreshTokenService.revokeSession(rotated);

        Assertions.assertTrue(refreshTokenService.rotate(rotated, "JUnit").isEmpty());
    }

    @Test
    void revokeAllSessionsEndsEveryDevice() {
        String laptop = refreshTokenService.issue(ownerId, "laptop");
        String phone = refreshTokenService.issue(ownerId, "phone");

        int revoked = refreshTokenService.revokeAllSessions(ownerId);

        Assertions.assertEquals(2, revoked);
        Assertions.assertEquals(Optional.empty(), refreshTokenService.rotate(laptop, "laptop"));
        Assertions.assertEquals(Optional.empty(), refreshTokenService.rotate(phone, "phone"));
    }
}
