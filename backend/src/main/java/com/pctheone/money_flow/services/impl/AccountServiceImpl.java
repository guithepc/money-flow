package com.pctheone.money_flow.services.impl;

import com.pctheone.money_flow.dto.AccountDTO;
import com.pctheone.money_flow.entities.AccountEntity;
import com.pctheone.money_flow.repositories.AccountRepository;
import com.pctheone.money_flow.services.AccountService;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

@Service
public class AccountServiceImpl implements AccountService {

    @Autowired
    AccountRepository accountRepository;

    @Override
    @Transactional
    public String addAccount(String description, BigDecimal balance, int ownerId) {
        accountRepository.addAccount(balance, description, ownerId);
        return "Account added: " + description;
    }

    @Override
    public List<AccountDTO> listAllAccounts(Integer ownerId) {
        List<AccountEntity> accountEntities = accountRepository.findByOwnerOwnerIdOrderByAccountIdAsc(ownerId);
        return accountEntities.stream().map(accountEntity -> new AccountDTO(accountEntity.getAccountId(), accountEntity.getDescription(), accountEntity.getBalance())).toList();
    }

    @Override
    public void validateAccountOwnership(Integer accountId, Integer ownerId) {
        if (!accountRepository.existsByAccountIdAndOwnerOwnerId(accountId, ownerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account does not belong to the authenticated user");
        }
    }
}
