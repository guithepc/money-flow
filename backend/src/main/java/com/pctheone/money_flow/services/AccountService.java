package com.pctheone.money_flow.services;

import com.pctheone.money_flow.dto.AccountDTO;
import com.pctheone.money_flow.enums.CurrencyEnum;

import java.math.BigDecimal;
import java.util.List;

public interface AccountService {
    AccountDTO addAccount(String description, BigDecimal balance, CurrencyEnum currency, int ownerId);
    List<AccountDTO> listAllAccounts(Integer ownerId);
    void validateAccountOwnership(Integer accountId, Integer ownerId);
    boolean existsByDescription(String description, Integer ownerId);
}
