package com.pctheone.money_flow.services;

import com.pctheone.money_flow.dto.AmountAndCategoryDTO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface TransactionsService {
    BigDecimal totalSpentByCategoryAndTime(LocalDate startTime, LocalDate endTime, Integer categoryId);
    BigDecimal totalRecurringByTime(LocalDate startTime, LocalDate endTime);
    BigDecimal totalIncomeByTime(LocalDate startTime, LocalDate endTime);
    BigDecimal totalSpentByTime(LocalDate startTime, LocalDate endTime);
    List<AmountAndCategoryDTO> totalSpentInMonthByCategoryDesc(LocalDate startTime, LocalDate endTime);
    BigDecimal registerExpense(Integer ownerId, String categoryDescription, Integer accountId, String amount, String description);
}
