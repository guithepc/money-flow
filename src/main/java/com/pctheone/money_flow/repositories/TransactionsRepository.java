package com.pctheone.money_flow.repositories;

import com.pctheone.money_flow.dto.AmountAndCategoryDTO;
import com.pctheone.money_flow.entities.CategoryEntity;
import com.pctheone.money_flow.entities.TransactionsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface TransactionsRepository extends JpaRepository<TransactionsEntity, Integer> {


    @Query("SELECT new com.pctheone.money_flow.dto.AmountAndCategoryDTO(SUM(t.amount), t.category.description) from TransactionsEntity as t " +
            "WHERE t.timestamp BETWEEN :startDate AND :endDate AND t.operationType = 'EXPENSE' " +
            "GROUP BY t.category.description ORDER BY SUM(t.amount) DESC")
    List<AmountAndCategoryDTO> totalSpentInMonthDesc(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);


    @Query("SELECT COALESCE(sum(t.amount), 0) from TransactionsEntity as t " +
            "WHERE t.timestamp BETWEEN :startDate AND :endDate AND t.category.categoryId = :categoryId AND t.operationType = 'EXPENSE'")
    BigDecimal totalSpentByCategoryByTime(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate, @Param("categoryId") Integer categoryId);


    @Query("SELECT COALESCE(sum(t.amount), 0) from TransactionsEntity as t " +
            "WHERE t.timestamp BETWEEN :startDate AND :endDate AND t.recurringPayment IS NOT NULL AND t.operationType = 'EXPENSE'")
    BigDecimal totalRecurringByTime(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);


    @Query("SELECT COALESCE(sum(t.amount), 0) from TransactionsEntity as t " +
            "WHERE t.timestamp BETWEEN :startDate AND :endDate AND t.operationType = 'INCOME'")
    BigDecimal totalIncomeByTime(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);


    @Query("SELECT COALESCE(sum(t.amount), 0) from TransactionsEntity as t " +
            "WHERE t.timestamp BETWEEN :startDate AND :endDate AND t.operationType = 'EXPENSE'")
    BigDecimal totalSpentByTime(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);


}
