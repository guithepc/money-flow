package com.pctheone.money_flow.services;

import com.pctheone.money_flow.entities.OwnerEntity;
import com.pctheone.money_flow.entities.PasswordResetTokenEntity;
import com.pctheone.money_flow.enums.LanguageEnum;
import com.pctheone.money_flow.exceptions.PasswordResetException;
import com.pctheone.money_flow.exceptions.PasswordResetRateLimitedException;
import com.pctheone.money_flow.repositories.OwnerRepository;
import com.pctheone.money_flow.repositories.PasswordResetTokenRepository;
import com.pctheone.money_flow.services.telegram.BotMessages;
import com.pctheone.money_flow.services.telegram.TelegramReplySender;
import com.pctheone.money_flow.utils.PasswordPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

//---- Password reset (#26): the link goes out through the Telegram bot, since there is no email delivery yet.
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    static final int TOKEN_TTL_MINUTES = 30;
    static final int TOKEN_BYTES = 32;

    private static final SecureRandom RANDOM = new SecureRandom();

    @Value("${app.frontend-url:http://localhost:5173}")
    String frontendUrl;

    @Autowired
    OwnerRepository ownerRepository;

    @Autowired
    PasswordResetTokenRepository tokenRepository;

    @Autowired
    PasswordResetRateLimiter rateLimiter;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    TelegramReplySender replySender;

    @Autowired
    BotMessages messages;

    @Autowired
    RefreshTokenService refreshTokenService;

    @Autowired
    Clock clock;

    TransactionTemplate transactionTemplate;

    //---- Lookup, token creation and the Telegram call run off the request thread, so the response time
    //---- does not reveal whether the email exists. Replaced by a direct executor in tests.
    Executor executor = Executors.newVirtualThreadPerTaskExecutor();

    @Autowired
    void setTransactionManager(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    //---- Same outcome whether or not the email exists or has Telegram linked; only the rate limit is visible.
    public void requestReset(String email) {
        if (!rateLimiter.tryAcquire(email))
            throw new PasswordResetRateLimitedException();
        String trimmedEmail = email.trim();
        executor.execute(() -> {
            try {
                sendLinkByEmail(trimmedEmail);
            } catch (Exception e) {
                log.error("Failed to process password reset request: {}", e.getMessage(), e);
            }
        });
    }

    //---- /redefinir_senha | /reset_password: the linked chat already identifies the owner.
    public void requestResetFromBot(OwnerEntity owner, Long chatId, LanguageEnum language) {
        if (!rateLimiter.tryAcquire(owner.getEmail())) {
            log.info("Password reset rate limited for owner {} (bot).", owner.getOwnerId());
            replySender.send(chatId, messages.get(language, "passwordReset.rateLimited"));
            return;
        }
        sendLink(owner.getOwnerId(), chatId, language);
    }

    //---- Validates the token, then the password (a weak password does not burn the token), and ends every session.
    @Transactional
    public void confirmReset(String token, String newPassword) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (token == null || token.isBlank())
            throw new PasswordResetException(PasswordResetException.Reason.TOKEN_INVALID);
        PasswordResetTokenEntity resetToken = tokenRepository.findByTokenHashForUpdate(hash(token.trim()))
                .orElseThrow(() -> new PasswordResetException(PasswordResetException.Reason.TOKEN_INVALID));
        if (resetToken.getUsedAt() != null)
            throw new PasswordResetException(PasswordResetException.Reason.TOKEN_USED);
        if (resetToken.getExpiresAt().isBefore(now))
            throw new PasswordResetException(PasswordResetException.Reason.TOKEN_EXPIRED);
        PasswordPolicy.check(newPassword);

        OwnerEntity owner = ownerRepository.findByIdForUpdate(resetToken.getOwnerId())
                .orElseThrow(() -> new PasswordResetException(PasswordResetException.Reason.TOKEN_INVALID));
        owner.setPasswordHash(passwordEncoder.encode(newPassword));
        //---- JWT iat has second precision: a login right after the reset (same second) must still be accepted.
        owner.setPasswordChangedAt(now.truncatedTo(ChronoUnit.SECONDS));
        ownerRepository.save(owner);
        //---- Same transaction: the refresh cookies (#24) die with the access tokens, so no browser silently logs back in.
        refreshTokenService.revokeAllSessions(owner.getOwnerId());

        resetToken.setUsedAt(now);
        tokenRepository.saveAndFlush(resetToken);
        tokenRepository.deleteUnusedByOwnerId(owner.getOwnerId());
        log.info("Password reset for owner {}.", owner.getOwnerId());

        //---- Sent after commit by TelegramReplySender.
        if (owner.getTelegramChatId() != null)
            replySender.send(owner.getTelegramChatId(), messages.get(languageOf(owner), "passwordReset.changed"));
    }

    private void sendLinkByEmail(String email) {
        Optional<OwnerEntity> owner = findOwnerByEmail(email);
        if (owner.isEmpty()) {
            log.info("Password reset requested for an unknown email.");
            return;
        }
        if (owner.get().getTelegramChatId() == null) {
            log.info("Password reset requested for owner {} without Telegram linked.", owner.get().getOwnerId());
            return;
        }
        sendLink(owner.get().getOwnerId(), owner.get().getTelegramChatId(), languageOf(owner.get()));
    }

    //---- Exact match first; otherwise a case-insensitive match, only when it is unambiguous.
    private Optional<OwnerEntity> findOwnerByEmail(String email) {
        if (email.isEmpty())
            return Optional.empty();
        List<OwnerEntity> owners = ownerRepository.findAllByEmailIgnoreCase(email);
        Optional<OwnerEntity> exact = owners.stream().filter(owner -> email.equals(owner.getEmail())).findFirst();
        if (exact.isPresent())
            return exact;
        return owners.size() == 1 ? Optional.of(owners.getFirst()) : Optional.empty();
    }

    private void sendLink(Integer ownerId, Long chatId, LanguageEnum language) {
        String token = transactionTemplate.execute(status -> issueToken(ownerId));
        String link = frontendUrl + "/reset-password?token=" + token;
        replySender.send(chatId, messages.get(language, "passwordReset.link", link));
        log.info("Password reset link sent to owner {}.", ownerId);
    }

    //---- A new request replaces the owner's previous unused tokens. Returns the raw token; only its hash is stored.
    String issueToken(Integer ownerId) {
        LocalDateTime now = LocalDateTime.now(clock);
        //---- Owner row lock: two concurrent requests can't both leave a valid token behind.
        ownerRepository.findByIdForUpdate(ownerId);
        tokenRepository.deleteUnusedByOwnerId(ownerId);

        String token = generateToken();
        PasswordResetTokenEntity entity = new PasswordResetTokenEntity();
        entity.setOwnerId(ownerId);
        entity.setTokenHash(hash(token));
        entity.setCreatedAt(now);
        entity.setExpiresAt(now.plusMinutes(TOKEN_TTL_MINUTES));
        tokenRepository.save(entity);
        return token;
    }

    static String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    //---- SHA-256, hex. The token already has 256 bits of entropy, so a plain hash (no salt/bcrypt) is enough.
    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    //---- No Telegram client language outside the bot: the saved language, else Portuguese.
    private static LanguageEnum languageOf(OwnerEntity owner) {
        LanguageEnum saved = LanguageEnum.fromCode(owner.getLanguage());
        return saved != null ? saved : LanguageEnum.PT;
    }
}
