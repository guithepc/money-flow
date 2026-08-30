package com.pctheone.money_flow.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@AllArgsConstructor
public class MonthlyIncomeExpenseDTO {
    private LocalDate month;
    private BigDecimal income;
    private BigDecimal expense;
}
