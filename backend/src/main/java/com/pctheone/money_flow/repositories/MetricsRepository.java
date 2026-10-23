package com.pctheone.money_flow.repositories;

import com.pctheone.money_flow.entities.OwnerEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

//---- Read-only volumetry queries. Native SQL so it can read the unmapped owner.created_at column
//---- and cast the Postgres ENUMs (operation_type, currency) to text.
public interface MetricsRepository extends Repository<OwnerEntity, Integer> {

    @Query(value = "SELECT COUNT(*) FROM owner", nativeQuery = true)
    long totalUsers();

    @Query(value = "SELECT COUNT(*) FROM owner WHERE telegram_chat_id IS NOT NULL", nativeQuery = true)
    long usersWithTelegram();

    @Query(value = "SELECT COUNT(*) FROM owner WHERE terms_accepted_at IS NOT NULL", nativeQuery = true)
    long usersAcceptedTerms();

    @Query(value = "SELECT COUNT(*) FROM owner WHERE created_at >= current_date - :days", nativeQuery = true)
    long newUsers(@Param("days") int days);

    @Query(value = "SELECT COUNT(DISTINCT owner_id) FROM transactions WHERE timestamp >= current_date - :days", nativeQuery = true)
    long activeUsers(@Param("days") int days);

    @Query(value = "SELECT COUNT(*) FROM transactions", nativeQuery = true)
    long totalTransactions();

    @Query(value = "SELECT COUNT(*) FROM transactions WHERE timestamp = current_date", nativeQuery = true)
    long transactionsToday();

    @Query(value = "SELECT COUNT(*) FROM transactions WHERE timestamp >= current_date - :days", nativeQuery = true)
    long transactionsLastDays(@Param("days") int days);

    @Query(value = "SELECT COUNT(*) FROM transactions WHERE operation_type::text = :type", nativeQuery = true)
    long transactionsByType(@Param("type") String type);

    @Query(value = "SELECT COUNT(*) FROM account", nativeQuery = true)
    long totalAccounts();

    @Query(value = "SELECT currency::text, COUNT(*) FROM account GROUP BY currency ORDER BY currency", nativeQuery = true)
    List<Object[]> accountsByCurrency();

    //---- date (text) -> count, for the last 30 days. Missing days just don't appear (filled in Java).
    @Query(value = "SELECT timestamp::text, COUNT(*) FROM transactions WHERE timestamp >= current_date - 29 GROUP BY timestamp", nativeQuery = true)
    List<Object[]> transactionsPerDay();

    @Query(value = "SELECT created_at::date::text, COUNT(*) FROM owner WHERE created_at >= current_date - 29 GROUP BY created_at::date", nativeQuery = true)
    List<Object[]> signupsPerDay();
}
