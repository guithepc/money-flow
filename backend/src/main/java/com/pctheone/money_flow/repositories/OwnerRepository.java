package com.pctheone.money_flow.repositories;

import com.pctheone.money_flow.entities.OwnerEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface OwnerRepository extends JpaRepository<OwnerEntity, Integer> {
    Optional<OwnerEntity> findByEmail(String email);

    Optional<OwnerEntity> findByTelegramChatId(Long telegramChatId);

    Optional<OwnerEntity> findByInviteUuid(UUID inviteUuid);

    //---- Row lock so two updates of the same owner (e.g. a double click) run one after the other.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM OwnerEntity o WHERE o.ownerId = :ownerId")
    Optional<OwnerEntity> findByIdForUpdate(@Param("ownerId") Integer ownerId);

}
