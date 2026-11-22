package com.pctheone.money_flow.dto;

import com.pctheone.money_flow.enums.OperationTypeEnum;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Getter
public class OpenAITransactionDTO {
    private OperationTypeEnum operationType;
    private String category;
    private String description;
    private String amount;
    //---- Null when the user didn't say the account: the default account is used.
    private Integer accountId;
}
