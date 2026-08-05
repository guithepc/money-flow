package com.pctheone.money_flow.dto;

import lombok.AllArgsConstructor;

import java.math.BigDecimal;

@AllArgsConstructor
public class AmountAndCategoryDTO {
    BigDecimal total;
    String categoryDescription;
}
