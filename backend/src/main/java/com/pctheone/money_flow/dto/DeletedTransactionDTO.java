package com.pctheone.money_flow.dto;

import com.pctheone.money_flow.enums.CurrencyEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@AllArgsConstructor
@Getter
public class DeletedTransactionDTO {
    private String description;
    private BigDecimal amount;
    private CurrencyEnum currency;
}
