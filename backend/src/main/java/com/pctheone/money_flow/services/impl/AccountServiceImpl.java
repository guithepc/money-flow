package com.pctheone.money_flow.services.impl;

import com.pctheone.money_flow.dto.AccountDTO;
import com.pctheone.money_flow.dto.AccountDeletionSummaryDTO;
import com.pctheone.money_flow.entities.AccountEntity;
import com.pctheone.money_flow.enums.CurrencyEnum;
import com.pctheone.money_flow.exceptions.DuplicateAccountNameException;
import com.pctheone.money_flow.repositories.AccountRepository;
import com.pctheone.money_flow.repositories.OwnerRepository;
import com.pctheone.money_flow.repositories.RecurringPaymentRepository;
import com.pctheone.money_flow.repositories.TransactionsRepository;
import com.pctheone.money_flow.services.AccountService;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

@Service
public class AccountServiceImpl implements AccountService {

    static final String DUPLICATE_NAME_INDEX = "account_owner_lower_description_key";

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    OwnerRepository ownerRepository;

    @Autowired
    TransactionsRepository transactionsRepository;

    @Autowired
    RecurringPaymentRepository recurringPaymentRepository;

    @Override
    @Transactional
    //---- Names are unique per owner, case-insensitive (unique index account_owner_lower_description_key).
    public AccountDTO addAccount(String description, BigDecimal balance, CurrencyEnum currency, int ownerId) {
        if (existsByDescription(description, ownerId))
            throw new DuplicateAccountNameException(description);
        AccountEntity accountEntity = new AccountEntity();
        accountEntity.setDescription(description);
        accountEntity.setBalance(balance);
        accountEntity.setCurrency(currency);
        accountEntity.setOwner(ownerRepository.getReferenceById(ownerId));
        try {
            //---- A brand-new account has no transactions yet.
            return toDTO(accountRepository.save(accountEntity), 0);
        } catch (DataIntegrityViolationException e) {
            //---- Lost a race with a concurrent insert of the same name.
            if (isDuplicateName(e))
                throw new DuplicateAccountNameException(description);
            throw e;
        }
    }

    static boolean isDuplicateName(DataIntegrityViolationException e) {
        String message = e.getMostSpecificCause().getMessage();
        return message != null && message.contains(DUPLICATE_NAME_INDEX);
    }

    @Override
    public List<AccountDTO> listAllAccounts(Integer ownerId) {
        List<AccountEntity> accountEntities = accountRepository.findByOwnerOwnerIdOrderByAccountIdAsc(ownerId);
        return accountEntities.stream()
                .map(account -> toDTO(account, transactionsRepository.countByAccount(account.getAccountId(), ownerId)))
                .toList();
    }

    @Override
    @Transactional
    //---- Names are unique per owner, case-insensitive. Keeping the same name (or changing only its case) is allowed.
    public AccountDTO renameAccount(Integer accountId, String description, Integer ownerId) {
        AccountEntity account = ownedAccount(accountId, ownerId);
        String name = description == null ? "" : description.trim();
        if (name.isEmpty() || name.length() > 255)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Account name must have between 1 and 255 characters");
        if (!name.equalsIgnoreCase(account.getDescription()) && existsByDescription(name, ownerId))
            throw new DuplicateAccountNameException(name);
        account.setDescription(name);
        try {
            AccountEntity saved = accountRepository.save(account);
            return toDTO(saved, transactionsRepository.countByAccount(accountId, ownerId));
        } catch (DataIntegrityViolationException e) {
            if (isDuplicateName(e))
                throw new DuplicateAccountNameException(name);
            throw e;
        }
    }

    @Override
    @Transactional
    //---- Cascade, in foreign-key order: transactions reference recurring_payment, both reference account.
    public AccountDeletionSummaryDTO deleteAccount(Integer accountId, Integer ownerId) {
        ownedAccount(accountId, ownerId);
        int transactions = transactionsRepository.deleteByAccountIdAndOwnerId(accountId, ownerId);
        int recurring = recurringPaymentRepository.deleteByAccountIdAndOwnerId(accountId, ownerId);
        accountRepository.deleteByAccountIdAndOwnerId(accountId, ownerId);
        return new AccountDeletionSummaryDTO(transactions, recurring);
    }

    //---- 404 when the account doesn't exist, 403 when it isn't the caller's (same shape as the transaction endpoints).
    private AccountEntity ownedAccount(Integer accountId, Integer ownerId) {
        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        if (!account.getOwner().getOwnerId().equals(ownerId))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account does not belong to the authenticated user");
        return account;
    }

    @Override
    public void validateAccountOwnership(Integer accountId, Integer ownerId) {
        if (!accountRepository.existsByAccountIdAndOwnerOwnerId(accountId, ownerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account does not belong to the authenticated user");
        }
    }

    //---- Case-insensitive: "nubank" and "Nubank" are the same account for the user.
    @Override
    public boolean existsByDescription(String description, Integer ownerId) {
        return accountRepository.existsByOwnerOwnerIdAndDescriptionIgnoreCase(ownerId, description.trim());
    }

    private AccountDTO toDTO(AccountEntity accountEntity, long transactionCount) {
        return new AccountDTO(accountEntity.getAccountId(), accountEntity.getDescription(), accountEntity.getBalance(), accountEntity.getCurrency(), transactionCount);
    }
}
