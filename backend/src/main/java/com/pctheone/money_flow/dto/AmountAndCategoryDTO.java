package com.pctheone.money_flow.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

import java.math.BigDecimal;

@ToString
@Getter
@AllArgsConstructor
public class AmountAndCategoryDTO {
    BigDecimal total;
    String categoryDescription;
}
