package com.pctheone.money_flow.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class TransactionUpdateRequestDTO {
    private String description;
    private Integer categoryId;
    private BigDecimal amount;
}
