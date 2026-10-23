package com.pctheone.money_flow.repositories;

import com.pctheone.money_flow.entities.OwnerEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OwnerRepository extends JpaRepository<OwnerEntity, Integer> {
    Optional<OwnerEntity> findByEmail(String email);

    //---- email is unique case-sensitively, so a case-insensitive lookup may match more than one owner.
    List<OwnerEntity> findAllByEmailIgnoreCase(String email);

    Optional<OwnerEntity> findByTelegramChatId(Long telegramChatId);

    Optional<OwnerEntity> findByInviteUuid(UUID inviteUuid);

    //---- Row lock so two updates of the same owner (e.g. a double click) run one after the other.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM OwnerEntity o WHERE o.ownerId = :ownerId")
    Optional<OwnerEntity> findByIdForUpdate(@Param("ownerId") Integer ownerId);

    //---- Last step of the account deletion. Tables added after #28 (refresh_token, voice_usage, password_reset_token)
    //---- reference owner with ON DELETE CASCADE, so they go with this row.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM owner WHERE owner_id = :ownerId", nativeQuery = true)
    int deleteByOwnerId(@Param("ownerId") Integer ownerId);

}
