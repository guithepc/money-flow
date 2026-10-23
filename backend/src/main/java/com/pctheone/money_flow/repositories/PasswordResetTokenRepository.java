package com.pctheone.money_flow.repositories;

import com.pctheone.money_flow.entities.PasswordResetTokenEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetTokenEntity, Integer> {

    //---- Row lock so two confirms with the same token run one after the other (the second sees used_at).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM PasswordResetTokenEntity t WHERE t.tokenHash = :tokenHash")
    Optional<PasswordResetTokenEntity> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    //---- A replaced token is deleted, so it becomes unknown (TOKEN_INVALID). Used tokens are kept.
    @Modifying
    @Query("DELETE FROM PasswordResetTokenEntity t WHERE t.ownerId = :ownerId AND t.usedAt IS NULL")
    int deleteUnusedByOwnerId(@Param("ownerId") Integer ownerId);
}
