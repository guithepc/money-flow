package com.pctheone.money_flow.dto;

import com.pctheone.money_flow.enums.CurrencyEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class TransactionRegistrationResultDTO {
    private BigDecimal amount;
    private BigDecimal newBalance;
    private CurrencyEnum currency;
    private String accountDescription;
    private String categoryDescription;
}
