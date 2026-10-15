package com.pctheone.money_flow.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.pctheone.money_flow.enums.CurrencyEnum;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

//---- Account being created during onboarding, stored as JSON in owner.onboarding_draft until confirmed.
@Getter
@Setter
@NoArgsConstructor
public class OnboardingAccountDraftDTO {

    //---- The single field the bot asked for last; null while waiting for the full "name, currency, balance" message.
    public enum Field {
        NAME,
        BALANCE,
        CURRENCY
    }

    private String name;
    private CurrencyEnum currency;
    private BigDecimal balance;
    private Field awaiting;

    @JsonIgnore
    public boolean isComplete() {
        return name != null && currency != null && balance != null;
    }
}
