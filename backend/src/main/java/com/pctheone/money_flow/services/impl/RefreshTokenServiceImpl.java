package com.pctheone.money_flow.services.impl;

import com.pctheone.money_flow.dto.RefreshRotationDTO;
import com.pctheone.money_flow.entities.RefreshTokenEntity;
import com.pctheone.money_flow.repositories.RefreshTokenRepository;
import com.pctheone.money_flow.services.RefreshTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenServiceImpl.class);

    private static final SecureRandom RANDOM = new SecureRandom();

    //---- 256 bits of entropy: a plain SHA-256 is enough to store it, no need for a slow hash.
    private static final int TOKEN_BYTES = 32;

    private static final int USER_AGENT_MAX_LENGTH = 255;

    //---- Expired tokens are kept a few more days so a late reuse is still detected, then purged.
    static final int PURGE_GRACE_DAYS = 7;

    @Value("${jwt.refresh-expiration-days:30}")
    long refreshExpirationDays;

    @Autowired
    RefreshTokenRepository refreshTokenRepository;

    @Override
    @Transactional
    public String issue(Integer ownerId, String userAgent) {
        return createToken(ownerId, UUID.randomUUID(), userAgent);
    }

    @Override
    @Transactional
    public Optional<RefreshRotationDTO> rotate(String rawToken, String userAgent) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        Optional<RefreshTokenEntity> found = refreshTokenRepository.findByTokenHashForUpdate(hash(rawToken));
        if (found.isEmpty()) {
            return Optional.empty();
        }

        RefreshTokenEntity current = found.get();
        LocalDateTime now = LocalDateTime.now();

        if (current.getRevokedAt() != null) {
            //---- Someone presented a token that was already used: either it was stolen or replayed. Kill the session.
            int revoked = refreshTokenRepository.revokeFamily(current.getFamilyId(), now);
            if (revoked > 0) {
                log.warn("Refresh token reuse detected for owner {}; revoked {} token(s) of family {}.",
                        current.getOwnerId(), revoked, current.getFamilyId());
            } else {
                log.info("Refresh refused: session of owner {} was already revoked.", current.getOwnerId());
            }
            return Optional.empty();
        }
        if (!current.getExpiresAt().isAfter(now)) {
            return Optional.empty();
        }

        current.setRevokedAt(now);
        refreshTokenRepository.save(current);
        String next = createToken(current.getOwnerId(), current.getFamilyId(), userAgent);
        return Optional.of(new RefreshRotationDTO(current.getOwnerId(), next));
    }

    @Override
    @Transactional
    public void revokeSession(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(hash(rawToken))
                .ifPresent(token -> refreshTokenRepository.revokeFamily(token.getFamilyId(), LocalDateTime.now()));
    }

    @Override
    @Transactional
    public int revokeAllSessions(Integer ownerId) {
        int revoked = refreshTokenRepository.revokeAllByOwner(ownerId, LocalDateTime.now());
        log.info("Revoked {} refresh token(s) of owner {}.", revoked, ownerId);
        return revoked;
    }

    @Scheduled(cron = "0 30 4 * * *")
    @Transactional
    public void purgeExpiredTokens() {
        int deleted = refreshTokenRepository.deleteExpiredBefore(LocalDateTime.now().minusDays(PURGE_GRACE_DAYS));
        log.info("Purged {} refresh tokens expired more than {} days ago.", deleted, PURGE_GRACE_DAYS);
    }

    //---- Sliding expiration: every rotation gives the session another full window.
    private String createToken(Integer ownerId, UUID familyId, String userAgent) {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        LocalDateTime now = LocalDateTime.now();
        RefreshTokenEntity entity = new RefreshTokenEntity(null, ownerId, familyId, hash(raw), now,
                now.plusDays(refreshExpirationDays), null, truncate(userAgent));
        refreshTokenRepository.save(entity);
        return raw;
    }

    static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String truncate(String userAgent) {
        if (userAgent == null) {
            return null;
        }
        return userAgent.length() > USER_AGENT_MAX_LENGTH ? userAgent.substring(0, USER_AGENT_MAX_LENGTH) : userAgent;
    }
}
