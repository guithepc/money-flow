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
}
