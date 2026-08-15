package com.pctheone.money_flow.services.impl;

import com.pctheone.money_flow.dto.AccountDTO;
import com.pctheone.money_flow.entities.AccountEntity;
import com.pctheone.money_flow.repositories.AccountRepository;
import com.pctheone.money_flow.services.AccountService;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

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
    public List<AccountDTO> listAllAccounts() {

        List<AccountEntity> accountEntities = accountRepository.findAll(Sort.by(Sort.Direction.ASC, "accountId"));

        return accountEntities.stream().map(accountEntity -> new AccountDTO(accountEntity.getAccountId(), accountEntity.getDescription(), accountEntity.getBalance())).toList();
    }
}
