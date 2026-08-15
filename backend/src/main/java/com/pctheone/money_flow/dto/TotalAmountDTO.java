package com.pctheone.money_flow.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class TotalAmountDTO {
    private BigDecimal total;
}
