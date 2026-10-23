package com.pctheone.money_flow.dto;

import com.pctheone.money_flow.enums.CurrencyEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@AllArgsConstructor
@Getter
public class AccountDTO {
    private Integer accountId;
    private String description;
    private BigDecimal amount;
    private CurrencyEnum currency;
    //---- How many transactions reference this account. Drives the "delete account" confirmation.
    private long transactionCount;
}
