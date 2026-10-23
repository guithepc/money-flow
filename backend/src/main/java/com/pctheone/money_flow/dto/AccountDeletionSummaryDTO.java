package com.pctheone.money_flow.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

//---- What a cascade account deletion removed, so the client can confirm and log.
@Getter
@AllArgsConstructor
public class AccountDeletionSummaryDTO {
    private int deletedTransactions;
    private int deletedRecurringPayments;
}
