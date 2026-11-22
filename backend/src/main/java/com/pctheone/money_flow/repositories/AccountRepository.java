package com.pctheone.money_flow.repositories;

import com.pctheone.money_flow.entities.AccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<AccountEntity, Integer> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE AccountEntity SET balance = balance - :amount WHERE accountId = :accountId")
    void decrementBalance(@Param("accountId") Integer accountId, @Param("amount") BigDecimal amount);


    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE AccountEntity SET balance = balance + :amount WHERE accountId = :accountId")
    void incrementBalance(@Param("accountId") Integer accountId, @Param("amount") BigDecimal amount);

    List<AccountEntity> findByOwnerOwnerIdOrderByAccountIdAsc(Integer ownerId);

    boolean existsByAccountIdAndOwnerOwnerId(Integer accountId, Integer ownerId);

    Optional<AccountEntity> findByAccountIdAndOwnerOwnerId(Integer accountId, Integer ownerId);

    Optional<AccountEntity> findByOwnerOwnerIdAndDisplayNumber(Integer ownerId, Integer displayNumber);

    boolean existsByOwnerOwnerIdAndDescriptionIgnoreCase(Integer ownerId, String description);

    //---- LEFT JOIN keeps accounts with no recent transactions (count 0); display_number breaks ties.
    @Query(value = """
            SELECT a.account_id FROM account a
            LEFT JOIN transactions t ON t.account_id = a.account_id AND t.timestamp >= CURRENT_DATE - 90
            WHERE a.owner_id = :ownerId
            GROUP BY a.account_id, a.display_number
            ORDER BY COUNT(t.transactions_id) DESC, a.display_number
            LIMIT 1""", nativeQuery = true)
    Optional<Integer> findMostUsedAccountId(@Param("ownerId") Integer ownerId);

    //---- Account deletion: runs after the owner's transactions and recurring payments are gone.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM account WHERE owner_id = :ownerId", nativeQuery = true)
    int deleteAllByOwnerId(@Param("ownerId") Integer ownerId);

    //---- Single account: runs after that account's transactions and recurring payments are gone.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM account WHERE account_id = :accountId AND owner_id = :ownerId", nativeQuery = true)
    int deleteByAccountIdAndOwnerId(@Param("accountId") Integer accountId, @Param("ownerId") Integer ownerId);
}
