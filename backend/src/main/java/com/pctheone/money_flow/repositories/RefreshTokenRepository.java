package com.pctheone.money_flow.repositories;

import com.pctheone.money_flow.entities.RefreshTokenEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, Long> {

    //---- Row lock so two refreshes with the same token run one after the other: the second one sees it rotated.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RefreshTokenEntity r WHERE r.tokenHash = :tokenHash")
    Optional<RefreshTokenEntity> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE RefreshTokenEntity SET revokedAt = :now WHERE familyId = :familyId AND revokedAt IS NULL")
    int revokeFamily(@Param("familyId") UUID familyId, @Param("now") LocalDateTime now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE RefreshTokenEntity SET revokedAt = :now WHERE ownerId = :ownerId AND revokedAt IS NULL")
    int revokeAllByOwner(@Param("ownerId") Integer ownerId, @Param("now") LocalDateTime now);

    @Modifying
    @Query("DELETE FROM RefreshTokenEntity WHERE expiresAt < :before")
    int deleteExpiredBefore(@Param("before") LocalDateTime before);
}
