package com.pctheone.money_flow.services;

import com.pctheone.money_flow.entities.OwnerEntity;
import com.pctheone.money_flow.entities.PasswordResetTokenEntity;
import com.pctheone.money_flow.enums.LanguageEnum;
import com.pctheone.money_flow.exceptions.PasswordResetException;
import com.pctheone.money_flow.exceptions.PasswordResetRateLimitedException;
import com.pctheone.money_flow.exceptions.WeakPasswordException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

public class PasswordResetServiceTest {

    private static final long CHAT_ID = 42L;
    private static final String EMAIL = "g@example.com";
    private static final String NEW_PASSWORD = "new-password-123";

    PasswordResetTestFixture fixture;
    PasswordResetService service;
    OwnerEntity owner;

    @BeforeEach
    void setUp() {
        fixture = new PasswordResetTestFixture();
        service = fixture.service;
        owner = fixture.addOwner(7, EMAIL, CHAT_ID, "en");
    }

    //---- Request

    @Test
    void requestSendsLinkInTheOwnerLanguage() {
        service.requestReset("  G@Example.com ");

        String token = fixture.lastTokenSentTo(CHAT_ID);
        Mockito.verify(fixture.replySender).send(CHAT_ID,
                fixture.messages.get(LanguageEnum.EN, "passwordReset.link", PasswordResetTestFixture.FRONTEND_URL + "/reset-password?token=" + token));
    }

    @Test
    void exactEmailWinsWhenOnlyTheCaseDiffers() {
        fixture.addOwner(8, "G@example.com", 43L, "pt");

        service.requestReset("G@example.com");

        fixture.lastTokenSentTo(43L);
        Mockito.verify(fixture.replySender, Mockito.never()).send(ArgumentMatchers.eq(CHAT_ID), ArgumentMatchers.anyString());
        Assertions.assertEquals(8, fixture.tokens.values().iterator().next().getOwnerId());
    }

    @Test
    void ownerWithoutLanguageGetsPortuguese() {
        owner.setLanguage(null);

        service.requestReset(EMAIL);

        String token = fixture.lastTokenSentTo(CHAT_ID);
        Mockito.verify(fixture.replySender).send(CHAT_ID,
                fixture.messages.get(LanguageEnum.PT, "passwordReset.link", PasswordResetTestFixture.FRONTEND_URL + "/reset-password?token=" + token));
    }

    @Test
    void onlyTheTokenHashIsStored() throws Exception {
        service.requestReset(EMAIL);

        String token = fixture.lastTokenSentTo(CHAT_ID);
        Assertions.assertEquals(32, Base64.getUrlDecoder().decode(token).length);
        Assertions.assertFalse(token.contains("="), "no padding");

        Assertions.assertEquals(1, fixture.tokens.size());
        PasswordResetTokenEntity stored = fixture.tokens.values().iterator().next();
        String expectedHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        Assertions.assertNotEquals(token, stored.getTokenHash());
        Assertions.assertEquals(expectedHash, stored.getTokenHash());
        Assertions.assertEquals(64, stored.getTokenHash().length());
        Assertions.assertEquals(owner.getOwnerId(), stored.getOwnerId());
        Assertions.assertNull(stored.getUsedAt());
        Assertions.assertEquals(stored.getCreatedAt().plusMinutes(30), stored.getExpiresAt());
    }

    @Test
    void unknownEmailOrOwnerWithoutTelegramSendNothing() {
        fixture.addOwner(8, "no-telegram@example.com", null, "pt");

        Assertions.assertDoesNotThrow(() -> service.requestReset("nobody@example.com"));
        Assertions.assertDoesNotThrow(() -> service.requestReset("no-telegram@example.com"));

        Mockito.verifyNoInteractions(fixture.replySender);
        Assertions.assertTrue(fixture.tokens.isEmpty());
    }

    @Test
    void failureWhileSendingNeverReachesTheCaller() {
        Mockito.when(fixture.ownerRepository.findAllByEmailIgnoreCase(ArgumentMatchers.anyString())).thenThrow(new RuntimeException("db down"));

        Assertions.assertDoesNotThrow(() -> service.requestReset(EMAIL));
    }

    @Test
    void fourthRequestWithinAnHourIsRateLimitedEvenForUnknownEmails() {
        for (String email : new String[]{EMAIL, "nobody@example.com"}) {
            for (int i = 0; i < 3; i++)
                service.requestReset(email);
            Assertions.assertThrows(PasswordResetRateLimitedException.class, () -> service.requestReset(email.toUpperCase()));
        }
    }

    @Test
    void botShortcutSharesTheRateLimitWithTheEmail() {
        service.requestReset(EMAIL);
        service.requestReset(EMAIL);
        service.requestResetFromBot(owner, CHAT_ID, LanguageEnum.EN);
        Mockito.clearInvocations(fixture.replySender);

        service.requestResetFromBot(owner, CHAT_ID, LanguageEnum.EN);

        Mockito.verify(fixture.replySender).send(CHAT_ID, fixture.messages.get(LanguageEnum.EN, "passwordReset.rateLimited"));
        Mockito.verifyNoMoreInteractions(fixture.replySender);
        Assertions.assertThrows(PasswordResetRateLimitedException.class, () -> service.requestReset(EMAIL));
    }

    @Test
    void botShortcutSendsTheLinkInTheChatLanguage() {
        service.requestResetFromBot(owner, CHAT_ID, LanguageEnum.PT);

        String token = fixture.lastTokenSentTo(CHAT_ID);
        Mockito.verify(fixture.replySender).send(CHAT_ID,
                fixture.messages.get(LanguageEnum.PT, "passwordReset.link", PasswordResetTestFixture.FRONTEND_URL + "/reset-password?token=" + token));
        Assertions.assertEquals(1, fixture.tokens.size());
    }

    //---- Confirm

    @Test
    void confirmChangesPasswordEndsSessionsAndNotifies() {
        service.requestReset(EMAIL);
        String token = fixture.lastTokenSentTo(CHAT_ID);
        fixture.clock.advance(Duration.ofMinutes(5).plusMillis(400));

        service.confirmReset(token, NEW_PASSWORD);

        LocalDateTime now = LocalDateTime.now(fixture.clock);
        Assertions.assertTrue(fixture.passwordEncoder.matches(NEW_PASSWORD, owner.getPasswordHash()));
        Assertions.assertEquals(now.truncatedTo(ChronoUnit.SECONDS), owner.getPasswordChangedAt());
        Assertions.assertEquals(now, fixture.tokens.values().iterator().next().getUsedAt());
        Mockito.verify(fixture.replySender).send(CHAT_ID, fixture.messages.get(LanguageEnum.EN, "passwordReset.changed"));
        Mockito.verify(fixture.refreshTokenService).revokeAllSessions(owner.getOwnerId());
    }

    @Test
    void alreadyUsedTokenIsRejected() {
        service.requestReset(EMAIL);
        String token = fixture.lastTokenSentTo(CHAT_ID);
        service.confirmReset(token, NEW_PASSWORD);

        assertRejected(PasswordResetException.Reason.TOKEN_USED, token, "another-password-1");
        Assertions.assertTrue(fixture.passwordEncoder.matches(NEW_PASSWORD, owner.getPasswordHash()));
    }

    @Test
    void expiredTokenIsRejected() {
        service.requestReset(EMAIL);
        String token = fixture.lastTokenSentTo(CHAT_ID);
        fixture.clock.advance(Duration.ofMinutes(30).plusSeconds(1));

        assertRejected(PasswordResetException.Reason.TOKEN_EXPIRED, token, NEW_PASSWORD);
        Assertions.assertTrue(fixture.passwordEncoder.matches("old-password", owner.getPasswordHash()));
        Assertions.assertNull(owner.getPasswordChangedAt());
    }

    @Test
    void tokenStillValidAtThirtyMinutes() {
        service.requestReset(EMAIL);
        String token = fixture.lastTokenSentTo(CHAT_ID);
        fixture.clock.advance(Duration.ofMinutes(30));

        Assertions.assertDoesNotThrow(() -> service.confirmReset(token, NEW_PASSWORD));
    }

    @Test
    void replacedTokenIsRejectedAndTheNewOneWorks() {
        service.requestReset(EMAIL);
        String first = fixture.lastTokenSentTo(CHAT_ID);
        service.requestReset(EMAIL);
        String second = fixture.lastTokenSentTo(CHAT_ID);
        Assertions.assertNotEquals(first, second);
        Assertions.assertEquals(1, fixture.tokens.size());

        assertRejected(PasswordResetException.Reason.TOKEN_INVALID, first, NEW_PASSWORD);
        Assertions.assertDoesNotThrow(() -> service.confirmReset(second, NEW_PASSWORD));
    }

    @Test
    void unknownOrMissingTokenIsInvalid() {
        assertRejected(PasswordResetException.Reason.TOKEN_INVALID, "not-a-token", NEW_PASSWORD);
        assertRejected(PasswordResetException.Reason.TOKEN_INVALID, null, NEW_PASSWORD);
        assertRejected(PasswordResetException.Reason.TOKEN_INVALID, " ", NEW_PASSWORD);
    }

    @Test
    void weakPasswordIsRejectedWithoutConsumingTheToken() {
        service.requestReset(EMAIL);
        String token = fixture.lastTokenSentTo(CHAT_ID);

        for (String weak : new String[]{null, "", "        ", "short1", "ç".repeat(37)})
            Assertions.assertThrows(WeakPasswordException.class, () -> service.confirmReset(token, weak), String.valueOf(weak));

        Assertions.assertNull(fixture.tokens.values().iterator().next().getUsedAt());
        Assertions.assertNull(owner.getPasswordChangedAt());
        Mockito.verify(fixture.ownerRepository, Mockito.never()).save(ArgumentMatchers.any());
        Mockito.verifyNoInteractions(fixture.refreshTokenService);
        Assertions.assertDoesNotThrow(() -> service.confirmReset(token, NEW_PASSWORD));
    }

    @Test
    void ownerWithoutTelegramIsNotNotified() {
        service.requestReset(EMAIL);
        String token = fixture.lastTokenSentTo(CHAT_ID);
        owner.setTelegramChatId(null);
        Mockito.clearInvocations(fixture.replySender);

        service.confirmReset(token, NEW_PASSWORD);

        Mockito.verifyNoInteractions(fixture.replySender);
    }

    //---- Sessions

    @Test
    void sessionsIssuedBeforeTheResetAreRevoked() {
        AuthService authService = new AuthService(fixture.passwordEncoder, fixture.ownerRepository);
        authService.jwtSecret = "tmpGFRC21ovxZhKhbd11YOC3Kw8YDyg5KcBb1dlMY0c=";
        authService.jwtExpiration = 3600000L;
        authService.clock = fixture.clock;
        String oldSession = authService.generateToken(owner.getOwnerId());
        Assertions.assertEquals(Optional.of(owner.getOwnerId()), authService.validateToken(oldSession));

        service.requestReset(EMAIL);
        String token = fixture.lastTokenSentTo(CHAT_ID);
        fixture.clock.advance(Duration.ofSeconds(2));
        service.confirmReset(token, NEW_PASSWORD);
        //---- Login right after the reset, in the same second.
        String newSession = authService.generateToken(owner.getOwnerId());

        Assertions.assertEquals(Optional.empty(), authService.validateToken(oldSession));
        Assertions.assertEquals(Optional.of(owner.getOwnerId()), authService.validateToken(newSession));
        Assertions.assertEquals(Optional.of(owner.getOwnerId()), authService.authenticate(EMAIL, NEW_PASSWORD));
    }

    private void assertRejected(PasswordResetException.Reason reason, String token, String password) {
        PasswordResetException e = Assertions.assertThrows(PasswordResetException.class, () -> service.confirmReset(token, password));
        Assertions.assertEquals(reason, e.getReason());
    }
}
