package com.pctheone.money_flow.services;

import com.pctheone.money_flow.dto.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface TransactionsService {
    BigDecimal totalSpentByCategoryAndTime(LocalDate startTime, LocalDate endTime, Integer categoryId);
    BigDecimal totalRecurringByTime(LocalDate startTime, LocalDate endTime, Integer accountId);
    BigDecimal totalIncomeByTime(LocalDate startTime, LocalDate endTime, Integer accountId);
    BigDecimal totalSpentByTime(LocalDate startTime, LocalDate endTime, Integer accountId);
    List<AmountAndCategoryDTO> totalSpentInMonthByCategoryDesc(LocalDate startTime, LocalDate endTime, Integer accountId);
    TransactionRegistrationResultDTO registerExpense(Integer ownerId, String categoryDescription, Integer accountId, String amount, String description);
    TransactionRegistrationResultDTO registerIncome(Integer ownerId, String categoryDescription, Integer accountId, String amount, String description);
    List<TransactionDTO> listAllTransactions (LocalDate startTime, LocalDate endTime, Integer accountId);
    List<MonthlyIncomeExpenseDTO> monthlyIncomeExpense(LocalDate startTime, LocalDate endTime, Integer accountId);
    List<DailyBalanceDTO> dailyBalance(LocalDate startTime, LocalDate endTime, Integer accountId);
    String deleteTransaction(Integer id);

}
