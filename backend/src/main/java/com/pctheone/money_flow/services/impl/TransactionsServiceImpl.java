package com.pctheone.money_flow.services.impl;

import com.pctheone.money_flow.dto.*;
import com.pctheone.money_flow.entities.AccountEntity;
import com.pctheone.money_flow.entities.CategoryEntity;
import com.pctheone.money_flow.entities.TransactionsEntity;
import com.pctheone.money_flow.enums.CategoryTypeEnum;
import com.pctheone.money_flow.enums.CurrencyEnum;
import com.pctheone.money_flow.enums.OperationTypeEnum;
import com.pctheone.money_flow.exceptions.CategoryNotFoundException;
import com.pctheone.money_flow.repositories.AccountRepository;
import com.pctheone.money_flow.repositories.CategoryRepository;
import com.pctheone.money_flow.repositories.TransactionsRepository;
import com.pctheone.money_flow.services.AccountService;
import com.pctheone.money_flow.services.CategoryService;
import com.pctheone.money_flow.services.TransactionsService;
import com.pctheone.money_flow.utils.DateUtils;
import com.pctheone.money_flow.utils.MoneyFormat;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class TransactionsServiceImpl implements TransactionsService {

    @Autowired
    CategoryRepository categoryRepository;

    @Autowired
    CategoryService categoryService;

    @Autowired
    TransactionsRepository transactionsRepository;

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    AccountService accountService;

    @Override
    public BigDecimal totalSpentByCategoryAndTime(LocalDate startTime, LocalDate endTime, Integer categoryId, Integer ownerId){

        DateUtils.validateTimeRange(startTime, endTime);

        if (!categoryRepository.existsByCategoryIdAndOwnerOwnerId(categoryId, ownerId))
            throw new CategoryNotFoundException("Category doesn't exist");

        return transactionsRepository.totalSpentByCategoryByTime(startTime, endTime, categoryId, ownerId);

    }

    //---- Which transactions a report aggregates. Values in different currencies are never summed:
    //---- ACCOUNT = one account (optionally checked against the currency filter), CURRENCY = all accounts in
    //---- that currency, ALL = no filter (legacy behaviour), NONE = account doesn't match the currency filter.
    private enum Scope { ALL, ACCOUNT, CURRENCY, NONE }

    private Scope resolveScope(Integer accountId, CurrencyEnum currency, Integer ownerId) {
        if (accountId != null) {
            AccountEntity account = accountRepository.findByAccountIdAndOwnerOwnerId(accountId, ownerId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Account does not belong to the authenticated user"));
            if (currency == null) return Scope.ACCOUNT;
            return currency.equals(account.getCurrency()) ? Scope.ACCOUNT : Scope.NONE;
        }
        return currency == null ? Scope.ALL : Scope.CURRENCY;
    }

    @Override
    public BigDecimal totalRecurringByTime(LocalDate startTime, LocalDate endTime, Integer accountId, CurrencyEnum currency, Integer ownerId){
        DateUtils.validateTimeRange(startTime, endTime);
        return switch (resolveScope(accountId, currency, ownerId)) {
            case ALL -> transactionsRepository.totalRecurringByTime(startTime, endTime, ownerId);
            case ACCOUNT -> transactionsRepository.totalRecurringByTimeByAccount(startTime, endTime, ownerId, accountId);
            case CURRENCY -> transactionsRepository.totalRecurringByTimeByCurrency(startTime, endTime, ownerId, currency);
            case NONE -> BigDecimal.ZERO;
        };
    }

    @Override
    public BigDecimal totalIncomeByTime(LocalDate startTime, LocalDate endTime, Integer accountId, CurrencyEnum currency, Integer ownerId) {
        DateUtils.validateTimeRange(startTime, endTime);
        return switch (resolveScope(accountId, currency, ownerId)) {
            case ALL -> transactionsRepository.totalIncomeByTime(startTime, endTime, ownerId);
            case ACCOUNT -> transactionsRepository.totalIncomeByTimeByAccount(startTime, endTime, ownerId, accountId);
            case CURRENCY -> transactionsRepository.totalIncomeByTimeByCurrency(startTime, endTime, ownerId, currency);
            case NONE -> BigDecimal.ZERO;
        };
    }

    @Override
    public BigDecimal totalSpentByTime(LocalDate startTime, LocalDate endTime, Integer accountId, CurrencyEnum currency, Integer ownerId) {
        DateUtils.validateTimeRange(startTime, endTime);
        return switch (resolveScope(accountId, currency, ownerId)) {
            case ALL -> transactionsRepository.totalSpentByTime(startTime, endTime, ownerId);
            case ACCOUNT -> transactionsRepository.totalSpentByTimeAndAccountId(startTime, endTime, ownerId, accountId);
            case CURRENCY -> transactionsRepository.totalSpentByTimeByCurrency(startTime, endTime, ownerId, currency);
            case NONE -> BigDecimal.ZERO;
        };
    }

    @Override
    public List<AmountAndCategoryDTO> totalSpentInMonthByCategoryDesc(LocalDate startTime, LocalDate endTime, Integer accountId, CurrencyEnum currency, Integer ownerId) {
        DateUtils.validateTimeRange(startTime, endTime);
        return switch (resolveScope(accountId, currency, ownerId)) {
            case ALL -> transactionsRepository.totalSpentInMonthByCategoryDesc(startTime, endTime, ownerId);
            case ACCOUNT -> transactionsRepository.totalSpentInMonthByCategoryDescByAccount(startTime, endTime, ownerId, accountId);
            case CURRENCY -> transactionsRepository.totalSpentInMonthByCategoryDescByCurrency(startTime, endTime, ownerId, currency);
            case NONE -> List.of();
        };
    }

    @Transactional
    @Override
    public TransactionRegistrationResultDTO registerExpense(Integer ownerId, String categoryDescription, Integer accountId, String amount, String description) {
        accountService.validateAccountOwnership(accountId, ownerId);

        Optional<CategoryEntity> category = categoryService.findByDescription(categoryDescription, ownerId);

        if (category.isEmpty())
            throw new CategoryNotFoundException("No category found for this description");

        BigDecimal amountValue = new BigDecimal(MoneyFormat.format(amount));

        transactionsRepository.insertSingleExpense(ownerId, accountId, category.get().getCategoryId(), description, LocalDate.now(), amountValue);
        accountRepository.decrementBalance(accountId, amountValue);
        Optional<AccountEntity> account = accountRepository.findById(accountId);

        return new TransactionRegistrationResultDTO(amountValue, account.get().getBalance(), account.get().getCurrency(), account.get().getDescription(), category.get().getDescription());
    }

    @Transactional
    @Override
    public TransactionRegistrationResultDTO registerIncome(Integer ownerId, String categoryDescription, Integer accountId, String amount, String description) {
        accountService.validateAccountOwnership(accountId, ownerId);

        Optional<CategoryEntity> category = categoryService.findByDescription(categoryDescription, ownerId);

        if (category.isEmpty())
            throw new CategoryNotFoundException("No category found for this description");

        BigDecimal amountValue = new BigDecimal(MoneyFormat.format(amount));

        transactionsRepository.insertSingleIncome(ownerId, accountId, category.get().getCategoryId(), description, LocalDate.now(), amountValue);
        accountRepository.incrementBalance(accountId, amountValue);
        Optional<AccountEntity> account = accountRepository.findById(accountId);

        return new TransactionRegistrationResultDTO(amountValue, account.get().getBalance(), account.get().getCurrency(), account.get().getDescription(), category.get().getDescription());
    }

    @Override
    public List<TransactionDTO> listAllTransactions(LocalDate startTime, LocalDate endTime, Integer accountId, CurrencyEnum currency, Integer ownerId) {
        DateUtils.validateTimeRange(startTime, endTime);
        return switch (resolveScope(accountId, currency, ownerId)) {
            case ALL -> transactionsRepository.listAllTransaction(startTime, endTime, ownerId);
            case ACCOUNT -> transactionsRepository.listAllTransactionByAccount(startTime, endTime, ownerId, accountId);
            case CURRENCY -> transactionsRepository.listAllTransactionByCurrency(startTime, endTime, ownerId, currency);
            case NONE -> List.of();
        };
    }

    @Override
    public List<MonthlyIncomeExpenseDTO> monthlyIncomeExpense(LocalDate startTime, LocalDate endTime, Integer accountId, CurrencyEnum currency, Integer ownerId) {
        DateUtils.validateTimeRange(startTime, endTime);
        List<Object[]> rows = switch (resolveScope(accountId, currency, ownerId)) {
            case ALL -> transactionsRepository.incomeExpenseMonthly(startTime, endTime, ownerId);
            case ACCOUNT -> transactionsRepository.incomeExpenseMonthlyByAccount(startTime, endTime, ownerId, accountId);
            case CURRENCY -> transactionsRepository.incomeExpenseMonthlyByCurrency(startTime, endTime, ownerId, currency);
            case NONE -> List.of();
        };
        List<MonthlyIncomeExpenseDTO> result = new ArrayList<>();
        for (Object[] row : rows) {
            LocalDate month = (LocalDate) row[0];
            result.add(new MonthlyIncomeExpenseDTO(month, (BigDecimal) row[1], (BigDecimal) row[2]));

        }
        return result;
    }

    @Override
    public List<DailyBalanceDTO> dailyBalance(LocalDate startTime, LocalDate endTime, Integer accountId, CurrencyEnum currency, Integer ownerId) {
        DateUtils.validateTimeRange(startTime, endTime);
        List<Object[]> rows = switch (resolveScope(accountId, currency, ownerId)) {
            case ALL -> transactionsRepository.balanceHistory(startTime, endTime, ownerId);
            case ACCOUNT -> transactionsRepository.balanceHistoryByAccount(startTime, endTime, ownerId, accountId);
            case CURRENCY -> transactionsRepository.balanceHistoryByCurrency(startTime, endTime, ownerId, currency);
            case NONE -> List.of();
        };
        List<DailyBalanceDTO> result = new ArrayList<>();
        for (Object[] row : rows){
            LocalDate day = (LocalDate) row[0];
            BigDecimal income = (BigDecimal) row[1];
            BigDecimal expense = (BigDecimal) row[2];
            BigDecimal net = income.subtract(expense);
            result.add(new DailyBalanceDTO(day, income, expense, net));
        }
        return result;
    }

    @Override
    @Transactional
    public Optional<DeletedTransactionDTO> deleteTransaction(Integer id, Integer ownerId) {
        Optional<TransactionsEntity> transactionsEntity = transactionsRepository.findById(id);
        if (transactionsEntity.isEmpty()){
            return Optional.empty();
        }
        TransactionsEntity transactions = transactionsEntity.get();

        if (!transactions.getOwner().getOwnerId().equals(ownerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Transaction does not belong to the authenticated user");
        }

        if (transactions.getOperationType() == OperationTypeEnum.EXPENSE){
            accountRepository.incrementBalance(transactions.getAccount().getAccountId(), transactions.getAmount());
        } else if (transactions.getOperationType() == OperationTypeEnum.INCOME){
            accountRepository.decrementBalance(transactions.getAccount().getAccountId(), transactions.getAmount());
        }

        transactionsRepository.deleteById(id);

        return Optional.of(new DeletedTransactionDTO(transactions.getDescription(), transactions.getAmount(), transactions.getAccount().getCurrency()));
    }

    @Override
    @Transactional
    public TransactionDTO updateTransaction(Integer id, TransactionUpdateRequestDTO request, Integer ownerId) {
        TransactionsEntity transaction = transactionsRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));
        if (!transaction.getOwner().getOwnerId().equals(ownerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Transaction does not belong to the authenticated user");
        }

        CategoryTypeEnum expectedType = switch (transaction.getOperationType()) {
            case EXPENSE -> CategoryTypeEnum.EXPENSE;
            case INCOME -> CategoryTypeEnum.INCOME;
            case TRANSFER -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Transfers can't be edited");
        };

        String description = request.getDescription() == null ? "" : request.getDescription().trim();
        if (description.isEmpty() || description.length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Description must have between 1 and 255 characters");
        }

        BigDecimal amount = request.getAmount();
        if (amount == null || amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount must be positive with at most 2 decimals");
        }

        if (request.getCategoryId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category is required");
        }
        CategoryEntity category = categoryRepository.findByCategoryIdAndOwnerOwnerId(request.getCategoryId(), ownerId)
                .orElseThrow(() -> new CategoryNotFoundException("Category doesn't exist"));
        if (category.getType() != expectedType) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category type doesn't match the transaction");
        }

        //---- Balance moves by the difference: an expense raised from 30 to 50 takes 20 more from the account.
        BigDecimal delta = amount.subtract(transaction.getAmount());
        Integer accountId = transaction.getAccount().getAccountId();
        TransactionDTO result = new TransactionDTO(transaction.getTransactionsId(), description, amount, transaction.getTimestamp(),
                category.getDescription(), transaction.getOperationType(), transaction.getAccount().getCurrency(), category.getCategoryId());

        transaction.setDescription(description);
        transaction.setAmount(amount);
        transaction.setCategory(category);

        // The balance query flushes these changes first (flushAutomatically), then clears the persistence context.
        if (transaction.getOperationType() == OperationTypeEnum.EXPENSE) {
            accountRepository.decrementBalance(accountId, delta);
        } else {
            accountRepository.incrementBalance(accountId, delta);
        }

        return result;
    }

}
