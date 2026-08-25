package com.pctheone.money_flow.repositories;

import com.pctheone.money_flow.entities.AccountEntity;
import com.pctheone.money_flow.entities.OwnerEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OwnerRepository extends JpaRepository<OwnerEntity, Integer> {
    Optional<OwnerEntity> findByEmail(String email);

    Optional<OwnerEntity> findByTelegramChatId(Long telegramChatId);

}
