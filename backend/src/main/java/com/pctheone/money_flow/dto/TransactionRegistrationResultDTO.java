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
    //---- Lets the PWA undo the registration (DELETE /api/transactions/{id}). Null where nobody needs it.
    private Integer transactionId;

    public TransactionRegistrationResultDTO(BigDecimal amount, BigDecimal newBalance, CurrencyEnum currency, String accountDescription, String categoryDescription) {
        this(amount, newBalance, currency, accountDescription, categoryDescription, null);
    }
}
