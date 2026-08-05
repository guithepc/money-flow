package com.pctheone.money_flow.repositories;

import com.pctheone.money_flow.entities.AccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<AccountEntity, Integer> {
}
