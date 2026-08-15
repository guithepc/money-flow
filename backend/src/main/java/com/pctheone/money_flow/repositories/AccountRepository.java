package com.pctheone.money_flow.repositories;

import com.pctheone.money_flow.entities.AccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<AccountEntity, Integer> {

    @Modifying
    @Query("UPDATE AccountEntity SET balance = balance - :amount WHERE accountId = :accountId")
    void decrementBalance(@Param("accountId") Integer accountId, @Param("amount") BigDecimal amount);


    @Modifying
    @Query("UPDATE AccountEntity SET balance = balance + :amount WHERE accountId = :accountId")
    void incrementBalance(@Param("accountId") Integer accountId, @Param("amount") BigDecimal amount);

    @Modifying
    @Query("INSERT INTO AccountEntity (balance, description, owner) values (:balance, :description, :owner)")
    void addAccount(@Param("balance") BigDecimal balance, @Param("description") String description, @Param("owner") Integer ownerId);


}
