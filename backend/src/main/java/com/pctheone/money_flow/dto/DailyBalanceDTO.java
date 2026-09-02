package com.pctheone.money_flow.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@AllArgsConstructor
@Getter
public class DailyBalanceDTO {
        private LocalDate date;
        private BigDecimal income;
        private BigDecimal expense;
        private BigDecimal net;


}
