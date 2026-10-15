package com.pctheone.money_flow.services;

import com.pctheone.money_flow.dto.*;
import com.pctheone.money_flow.enums.CurrencyEnum;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TransactionsService {
    BigDecimal totalSpentByCategoryAndTime(LocalDate startTime, LocalDate endTime, Integer categoryId, Integer ownerId);
    BigDecimal totalRecurringByTime(LocalDate startTime, LocalDate endTime, Integer accountId, CurrencyEnum currency, Integer ownerId);
    BigDecimal totalIncomeByTime(LocalDate startTime, LocalDate endTime, Integer accountId, CurrencyEnum currency, Integer ownerId);
    BigDecimal totalSpentByTime(LocalDate startTime, LocalDate endTime, Integer accountId, CurrencyEnum currency, Integer ownerId);
    List<AmountAndCategoryDTO> totalSpentInMonthByCategoryDesc(LocalDate startTime, LocalDate endTime, Integer accountId, CurrencyEnum currency, Integer ownerId);
    TransactionRegistrationResultDTO registerExpense(Integer ownerId, String categoryDescription, Integer accountId, String amount, String description);
    TransactionRegistrationResultDTO registerIncome(Integer ownerId, String categoryDescription, Integer accountId, String amount, String description);
    List<TransactionDTO> listAllTransactions(LocalDate startTime, LocalDate endTime, Integer accountId, CurrencyEnum currency, Integer ownerId);
    List<MonthlyIncomeExpenseDTO> monthlyIncomeExpense(LocalDate startTime, LocalDate endTime, Integer accountId, CurrencyEnum currency, Integer ownerId);
    List<DailyBalanceDTO> dailyBalance(LocalDate startTime, LocalDate endTime, Integer accountId, CurrencyEnum currency, Integer ownerId);
    //---- Empty when the transaction does not exist.
    Optional<DeletedTransactionDTO> deleteTransaction(Integer id, Integer ownerId);
    TransactionDTO updateTransaction(Integer id, TransactionUpdateRequestDTO request, Integer ownerId);

}
