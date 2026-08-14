package com.pctheone.money_flow.services;

import com.pctheone.money_flow.dto.AccountDTO;

import java.math.BigDecimal;
import java.util.List;

public interface AccountService {
    String addAccount(String description, BigDecimal balance, int ownerId);
    List<AccountDTO> listAllAccounts();
}
