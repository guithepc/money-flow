package com.pctheone.money_flow.repositories;

import com.pctheone.money_flow.dto.AmountAndCategoryDTO;
import com.pctheone.money_flow.dto.MonthlyIncomeExpenseDTO;
import com.pctheone.money_flow.dto.TransactionDTO;
import com.pctheone.money_flow.entities.TransactionsEntity;
import org.springframework.cglib.core.Local;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface TransactionsRepository extends JpaRepository<TransactionsEntity, Integer> {


    @Query("SELECT new com.pctheone.money_flow.dto.AmountAndCategoryDTO(SUM(t.amount), t.category.description) from TransactionsEntity as t " +
            "WHERE t.timestamp BETWEEN :startDate AND :endDate AND t.operationType = 'EXPENSE' " +
            "GROUP BY t.category.description ORDER BY SUM(t.amount) DESC")
    List<AmountAndCategoryDTO> totalSpentInMonthByCategoryDesc(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("SELECT new com.pctheone.money_flow.dto.AmountAndCategoryDTO(SUM(t.amount), t.category.description) from TransactionsEntity as t " +
            "WHERE t.timestamp BETWEEN :startDate AND :endDate AND t.operationType = 'EXPENSE' AND t.account.accountId = :accountId " +
            "GROUP BY t.category.description ORDER BY SUM(t.amount) DESC")
    List<AmountAndCategoryDTO> totalSpentInMonthByCategoryDescByAccount(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate, @Param("accountId") Integer accountId);

    @Query("SELECT new com.pctheone.money_flow.dto.TransactionDTO(t.transactionsId, t.description, t.amount, t.timestamp, t.category.description, t.operationType) from TransactionsEntity as t " +
            "WHERE t.timestamp BETWEEN :startDate AND :endDate")
    List<TransactionDTO> listAllTransaction(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("SELECT new com.pctheone.money_flow.dto.TransactionDTO(t.transactionsId, t.description, t.amount, t.timestamp, t.category.description, t.operationType) from TransactionsEntity as t " +
            "WHERE t.timestamp BETWEEN :startDate AND :endDate AND t.account.accountId = :accountId")
    List<TransactionDTO> listAllTransactionByAccount(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate, @Param("accountId") Integer accountId);

    @Query("SELECT COALESCE(sum(t.amount), 0) from TransactionsEntity as t " +
            "WHERE t.timestamp BETWEEN :startDate AND :endDate AND t.category.categoryId = :categoryId AND t.operationType = 'EXPENSE'")
    BigDecimal totalSpentByCategoryByTime(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate, @Param("categoryId") Integer categoryId);


    @Query("SELECT COALESCE(sum(t.amount), 0) from TransactionsEntity as t " +
            "WHERE t.timestamp BETWEEN :startDate AND :endDate AND t.recurringPayment IS NOT NULL AND t.operationType = 'EXPENSE'")
    BigDecimal totalRecurringByTime(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("SELECT COALESCE(sum(t.amount), 0) from TransactionsEntity as t " +
            "WHERE t.timestamp BETWEEN :startDate AND :endDate AND t.recurringPayment IS NOT NULL AND t.operationType = 'EXPENSE' AND t.account.accountId = :accountId")
    BigDecimal totalRecurringByTimeByAccount(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate, @Param("accountId") Integer accountId);


    @Query("SELECT COALESCE(sum(t.amount), 0) from TransactionsEntity as t " +
            "WHERE t.timestamp BETWEEN :startDate AND :endDate AND t.operationType = 'INCOME'")
    BigDecimal totalIncomeByTime(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);


    @Query("SELECT COALESCE(sum(t.amount), 0) from TransactionsEntity as t " +
            "WHERE t.timestamp BETWEEN :startDate AND :endDate AND t.operationType = 'INCOME' AND t.account.accountId = :accountId")
    BigDecimal totalIncomeByTimeByAccount(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate, @Param("accountId") Integer accountId);


    @Query("SELECT COALESCE(sum(t.amount), 0) from TransactionsEntity as t " +
            "WHERE t.timestamp BETWEEN :startDate AND :endDate AND t.operationType = 'EXPENSE'")
    BigDecimal totalSpentByTime(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);


    @Query("SELECT COALESCE(sum(t.amount), 0) from TransactionsEntity as t " +
            "WHERE t.timestamp BETWEEN :startDate AND :endDate AND t.operationType = 'EXPENSE' AND t.account.accountId = :accountId")
    BigDecimal totalSpentByTimeAndAccountId(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate, @Param("accountId") Integer accountId);

    @Modifying
    @Query("INSERT into TransactionsEntity (owner, account, category, description, timestamp, operationType, amount) " +
            "VALUES (:ownerId, :accountId, :categoryId, :description, :timestamp, 'EXPENSE', :amount)")
    void insertSingleExpense(@Param("ownerId") Integer ownerId, @Param("accountId") Integer accountId, @Param("categoryId") Integer categoryId, @Param("description") String description,
                             @Param("timestamp") LocalDate timestamp, @Param ("amount") BigDecimal amount);

    @Modifying
    @Query("INSERT into TransactionsEntity (owner, account, category, description, timestamp, operationType, amount) " +
            "VALUES (:ownerId, :accountId, :categoryId, :description, :timestamp, 'INCOME', :amount)")
    void insertSingleIncome(@Param("ownerId") Integer ownerId, @Param("accountId") Integer accountId, @Param("categoryId") Integer categoryId, @Param("description") String description,
                            @Param("timestamp") LocalDate timestamp, @Param ("amount") BigDecimal amount);

    @Query("SELECT FUNCTION('date_trunc', 'month', t.timestamp), " +
    "SUM (CASE WHEN t.operationType = 'INCOME' THEN t.amount ELSE 0 END), " +
    "SUM (CASE WHEN t.operationType = 'EXPENSE' THEN t.amount ELSE 0 END) " +
    "FROM TransactionsEntity t " +
    "WHERE t.timestamp BETWEEN :startDate AND :endDate " +
    "GROUP BY FUNCTION('date_trunc', 'month', t.timestamp) " +
    "ORDER BY FUNCTION('date_trunc', 'month', t.timestamp)")
    List<Object[]> incomeExpenseMonthly(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
}
