package com.pctheone.money_flow.dto;

import com.pctheone.money_flow.enums.CategoryTypeEnum;
import com.pctheone.money_flow.enums.CurrencyEnum;
import com.pctheone.money_flow.enums.OperationTypeEnum;

import java.math.BigDecimal;

//---- What the PWA gets back after sending a sentence or a voice note. The PWA owns the wording; this carries only facts.
//---- status: REGISTERED | NOT_UNDERSTOOD | CATEGORY_NOT_FOUND | CATEGORY_TYPE_MISMATCH | NO_ACCOUNT | LIMIT_REACHED
public record CaptureResponseDTO(
        String status,
        String transcription,
        Transaction transaction,
        //---- CATEGORY_NOT_FOUND: the name to offer creating. CATEGORY_TYPE_MISMATCH: the category of the other type.
        Category category,
        Integer remainingToday) {

    public record Transaction(Integer id, OperationTypeEnum operationType, BigDecimal amount, CurrencyEnum currency,
                              String account, String category, BigDecimal newBalance) {}

    public record Category(String name, CategoryTypeEnum type) {}
}
