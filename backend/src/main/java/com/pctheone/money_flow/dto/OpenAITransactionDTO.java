package com.pctheone.money_flow.dto;

import com.pctheone.money_flow.enums.OperationTypeEnum;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@NoArgsConstructor
@Getter
public class OpenAITransactionDTO {
    private OperationTypeEnum operationType;
    private String category;
    private String description;
    private String amount;
    private int accountId;
}
