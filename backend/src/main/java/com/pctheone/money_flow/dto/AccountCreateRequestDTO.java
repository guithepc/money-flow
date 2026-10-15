package com.pctheone.money_flow.dto;

import com.pctheone.money_flow.enums.CurrencyEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class AccountCreateRequestDTO {
    private String description;
    private BigDecimal balance;
    private CurrencyEnum currency;
}
