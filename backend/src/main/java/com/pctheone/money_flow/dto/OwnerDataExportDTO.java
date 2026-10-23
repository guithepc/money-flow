package com.pctheone.money_flow.dto;

import com.pctheone.money_flow.enums.CategoryTypeEnum;
import com.pctheone.money_flow.enums.CurrencyEnum;
import com.pctheone.money_flow.enums.FrequencyEnum;
import com.pctheone.money_flow.enums.OperationTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

//---- GET /api/owner/export: everything stored about the owner (right of access / portability).
//---- The password hash and the Telegram invite token are left out on purpose (secrets, not information about the user).
@Getter
@AllArgsConstructor
public class OwnerDataExportDTO {
    private LocalDateTime exportedAt;
    private Profile profile;
    private List<Account> accounts;
    private List<Category> categories;
    private List<Transaction> transactions;
    private List<RecurringPayment> recurringPayments;

    public record Profile(String name, String email, Long telegramChatId, String language, String onboardingStep,
                          LocalDateTime termsAcceptedAt, String termsVersion) {
    }

    public record Account(Integer accountId, String name, CurrencyEnum currency, BigDecimal balance) {
    }

    public record Category(Integer categoryId, String name, CategoryTypeEnum type) {
    }

    public record Transaction(Integer transactionId, LocalDate date, OperationTypeEnum operationType, String description,
                              BigDecimal amount, CurrencyEnum currency, Integer accountId, String account,
                              Integer categoryId, String category, Integer recurringPaymentId) {
    }

    public record RecurringPayment(Integer recurringPaymentId, String description, BigDecimal amount, FrequencyEnum frequency,
                                   boolean active, LocalDate startDate, LocalDate endDate, Integer accountId, Integer categoryId) {
    }
}
