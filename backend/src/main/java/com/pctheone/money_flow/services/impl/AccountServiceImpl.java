package com.pctheone.money_flow.services.impl;

import com.pctheone.money_flow.dto.AccountDTO;
import com.pctheone.money_flow.entities.AccountEntity;
import com.pctheone.money_flow.enums.CurrencyEnum;
import com.pctheone.money_flow.exceptions.DuplicateAccountNameException;
import com.pctheone.money_flow.repositories.AccountRepository;
import com.pctheone.money_flow.repositories.OwnerRepository;
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
            return toDTO(accountRepository.save(accountEntity));
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
        return accountEntities.stream().map(this::toDTO).toList();
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

    private AccountDTO toDTO(AccountEntity accountEntity) {
        return new AccountDTO(accountEntity.getAccountId(), accountEntity.getDescription(), accountEntity.getBalance(), accountEntity.getCurrency());
    }
}
