package com.pctheone.money_flow.services;

import com.pctheone.money_flow.dto.AccountDTO;
import com.pctheone.money_flow.dto.AccountDeletionSummaryDTO;
import com.pctheone.money_flow.enums.CurrencyEnum;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface AccountService {
    AccountDTO addAccount(String description, BigDecimal balance, CurrencyEnum currency, int ownerId);
    List<AccountDTO> listAllAccounts(Integer ownerId);
    AccountDTO renameAccount(Integer accountId, String description, Integer ownerId);
    //---- Cascade: removes the account and all of its transactions and recurring payments.
    AccountDeletionSummaryDTO deleteAccount(Integer accountId, Integer ownerId);
    void validateAccountOwnership(Integer accountId, Integer ownerId);

    //---- Account id for the per-owner number the user typed in the bot. NOT_FOUND when the owner has no such account.
    Integer accountIdByDisplayNumber(Integer displayNumber, Integer ownerId);
    boolean existsByDescription(String description, Integer ownerId);

    //---- Account used when the user doesn't say one: the most used in the last 90 days, ties to the lowest number.
    Optional<Integer> defaultAccountId(Integer ownerId);
}
