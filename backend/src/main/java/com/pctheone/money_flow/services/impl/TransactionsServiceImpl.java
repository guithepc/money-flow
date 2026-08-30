package com.pctheone.money_flow.services.impl;

import com.pctheone.money_flow.dto.AmountAndCategoryDTO;
import com.pctheone.money_flow.dto.MonthlyIncomeExpenseDTO;
import com.pctheone.money_flow.dto.TransactionDTO;
import com.pctheone.money_flow.dto.TransactionRegistrationResultDTO;
import com.pctheone.money_flow.entities.AccountEntity;
import com.pctheone.money_flow.entities.CategoryEntity;
import com.pctheone.money_flow.exceptions.CategoryNotFoundException;
import com.pctheone.money_flow.repositories.AccountRepository;
import com.pctheone.money_flow.repositories.CategoryRepository;
import com.pctheone.money_flow.repositories.TransactionsRepository;
import com.pctheone.money_flow.services.TransactionsService;
import com.pctheone.money_flow.utils.DateUtils;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class TransactionsServiceImpl implements TransactionsService {

    @Autowired
    CategoryRepository categoryRepository;

    @Autowired
    TransactionsRepository transactionsRepository;

    @Autowired
    AccountRepository accountRepository;

    @Override
    public BigDecimal totalSpentByCategoryAndTime(LocalDate startTime, LocalDate endTime, Integer categoryId){

        DateUtils.validateTimeRange(startTime, endTime);

        Optional<CategoryEntity> optionalCategory = categoryRepository.findById(categoryId);
        if (optionalCategory.isEmpty())
            throw new CategoryNotFoundException("Category doesn't exist");

        return transactionsRepository.totalSpentByCategoryByTime(startTime, endTime, categoryId);

    }

    @Override
    public BigDecimal totalRecurringByTime(LocalDate startTime, LocalDate endTime, Integer accountId){
        DateUtils.validateTimeRange(startTime, endTime);
        if (accountId == null) {
            return transactionsRepository.totalRecurringByTime(startTime, endTime);
        } else {
            return transactionsRepository.totalRecurringByTimeByAccount(startTime, endTime, accountId);
        }
    }

    @Override
    public BigDecimal totalIncomeByTime(LocalDate startTime, LocalDate endTime, Integer accountId) {
        DateUtils.validateTimeRange(startTime, endTime);
        if (accountId == null){
            return transactionsRepository.totalIncomeByTime(startTime, endTime);
        } else {
            return transactionsRepository.totalIncomeByTimeByAccount(startTime, endTime, accountId);
        }
    }

    @Override
    public BigDecimal totalSpentByTime(LocalDate startTime, LocalDate endTime, Integer accountId) {
        DateUtils.validateTimeRange(startTime, endTime);
        if (accountId == null){
            return transactionsRepository.totalSpentByTime(startTime,endTime);
        } else {
            return transactionsRepository.totalSpentByTimeAndAccountId(startTime, endTime, accountId);
        }
    }

    @Override
    public List<AmountAndCategoryDTO> totalSpentInMonthByCategoryDesc(LocalDate startTime, LocalDate endTime, Integer accountId) {

        DateUtils.validateTimeRange(startTime, endTime);
        if (accountId == null) {
            return transactionsRepository.totalSpentInMonthByCategoryDesc(startTime, endTime);

        } else {
            return transactionsRepository.totalSpentInMonthByCategoryDescByAccount(startTime, endTime, accountId);
        }

    }

    @Transactional
    @Override
    public TransactionRegistrationResultDTO registerExpense(Integer ownerId, String categoryDescription, Integer accountId, String amount, String description) {
        char firstChar = categoryDescription.charAt(0);
        String categoryDescriptionFormatted = String.valueOf(firstChar).toUpperCase() + categoryDescription.substring(1);

        Optional<CategoryEntity> category = Optional.ofNullable(categoryRepository.findByDescription(categoryDescriptionFormatted));

        if (category.isEmpty())
            throw new CategoryNotFoundException("No category found for this description");

        BigDecimal amountValue = new BigDecimal(amount);

        transactionsRepository.insertSingleExpense(ownerId, accountId, category.get().getCategoryId(), description, LocalDate.now(), amountValue);
        accountRepository.decrementBalance(accountId, amountValue);
        Optional<AccountEntity> account = accountRepository.findById(accountId);

        return new TransactionRegistrationResultDTO(amountValue, account.get().getBalance());
    }

    @Transactional
    @Override
    public TransactionRegistrationResultDTO registerIncome(Integer ownerId, String categoryDescription, Integer accountId, String amount, String description) {
        char firstChar = categoryDescription.charAt(0);
        String categoryDescriptionFormatted = String.valueOf(firstChar).toUpperCase() + categoryDescription.substring(1);

        Optional<CategoryEntity> category = Optional.ofNullable(categoryRepository.findByDescription(categoryDescriptionFormatted));

        if (category.isEmpty())
            throw new CategoryNotFoundException("No category found for this description");

        BigDecimal amountValue = new BigDecimal(amount);

        transactionsRepository.insertSingleIncome(ownerId, accountId, category.get().getCategoryId(), description, LocalDate.now(), amountValue);
        accountRepository.incrementBalance(accountId, amountValue);
        Optional<AccountEntity> account = accountRepository.findById(accountId);

        return new TransactionRegistrationResultDTO(amountValue, account.get().getBalance());
    }

    @Override
    public List<TransactionDTO> listAllTransactions(LocalDate startTime, LocalDate endTime, Integer accountId) {
        DateUtils.validateTimeRange(startTime, endTime);
        if (accountId == null){
            return transactionsRepository.listAllTransaction(startTime, endTime);
        } else{
            return transactionsRepository.listAllTransactionByAccount(startTime, endTime, accountId);
        }
    }

    @Override
    public List<MonthlyIncomeExpenseDTO> monthlyIncomeExpense(LocalDate startTime, LocalDate endTime) {
        DateUtils.validateTimeRange(startTime, endTime);
        List<Object[]> rows = transactionsRepository.incomeExpenseMonthly(startTime, endTime);
        List<MonthlyIncomeExpenseDTO> result = new ArrayList<>();
        for (Object[] row : rows) {
            LocalDate month = (LocalDate) row[0];
            result.add(new MonthlyIncomeExpenseDTO(month, (BigDecimal) row[1], (BigDecimal) row[2]));

        }
        return result;
    }

}
