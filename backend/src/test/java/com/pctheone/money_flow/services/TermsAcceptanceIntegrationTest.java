package com.pctheone.money_flow.services;

import com.pctheone.money_flow.entities.OwnerEntity;
import com.pctheone.money_flow.exceptions.TermsNotAcceptedException;
import com.pctheone.money_flow.repositories.OwnerRepository;
import com.pctheone.money_flow.services.telegram.TelegramApiClient;
import com.pctheone.money_flow.utils.LegalTerms;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

//---- Consent on sign-up and acceptance by existing owners (#28).
@SpringBootTest(properties = {
        "telegram.bot-token=test-token",
        "telegram.webhook-secret=test-secret",
        "jwt.secret=tmpGFRC21ovxZhKhbd11YOC3Kw8YDyg5KcBb1dlMY0c=",
        "jwt.expiration=3600000"
})
public class TermsAcceptanceIntegrationTest {

    @MockitoBean
    TelegramApiClient telegramApiClient;

    @Autowired
    OwnerService ownerService;

    @Autowired
    OwnerRepository ownerRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    String email = "terms-" + UUID.randomUUID() + "@terms.test";

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM owner WHERE email = ?", email);
    }

    @Test
    void registerWithoutAcceptanceIsRejected() {
        Assertions.assertThrows(TermsNotAcceptedException.class, () -> ownerService.registerOwner("T", email, "secret123", null));
        Assertions.assertThrows(TermsNotAcceptedException.class, () -> ownerService.registerOwner("T", email, "secret123", " "));
        Assertions.assertTrue(ownerRepository.findByEmail(email).isEmpty());
    }

    @Test
    void registerWithOutdatedVersionIsRejected() {
        Assertions.assertThrows(TermsNotAcceptedException.class, () -> ownerService.registerOwner("T", email, "secret123", "0.1"));
        Assertions.assertTrue(ownerRepository.findByEmail(email).isEmpty());
    }

    @Test
    void registerStoresAcceptanceDateAndVersion() {
        Integer ownerId = ownerService.registerOwner("T", email, "secret123", LegalTerms.CURRENT_VERSION).orElseThrow();

        OwnerEntity owner = ownerRepository.findById(ownerId).orElseThrow();
        Assertions.assertEquals(LegalTerms.CURRENT_VERSION, owner.getTermsVersion());
        Assertions.assertNotNull(owner.getTermsAcceptedAt());
        Assertions.assertTrue(ownerService.getProfile(ownerId).isTermsAccepted());
    }

    @Test
    void existingOwnerWithoutAcceptanceIsAskedAndCanAccept() {
        OwnerEntity legacy = new OwnerEntity();
        legacy.setName("legacy");
        legacy.setEmail(email);
        legacy.setPasswordHash("x".repeat(60));
        Integer ownerId = ownerRepository.save(legacy).getOwnerId();
        Assertions.assertFalse(ownerService.getProfile(ownerId).isTermsAccepted());

        Assertions.assertThrows(TermsNotAcceptedException.class, () -> ownerService.acceptTerms(ownerId, "0.1"));
        Assertions.assertFalse(ownerService.getProfile(ownerId).isTermsAccepted());

        ownerService.acceptTerms(ownerId, LegalTerms.CURRENT_VERSION);
        Assertions.assertTrue(ownerService.getProfile(ownerId).isTermsAccepted());
        Assertions.assertNotNull(ownerRepository.findById(ownerId).orElseThrow().getTermsAcceptedAt());
    }
}
