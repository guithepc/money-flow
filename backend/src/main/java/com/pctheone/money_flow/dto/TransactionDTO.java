package com.pctheone.money_flow.dto;

import com.pctheone.money_flow.enums.OperationTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@AllArgsConstructor
public class TransactionDTO {
    private Integer id;
    private String description;
    private BigDecimal amount;
    private LocalDate date;
    private String categoryDescription;
    private OperationTypeEnum operationType;
}
