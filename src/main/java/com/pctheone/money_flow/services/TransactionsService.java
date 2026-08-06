package com.pctheone.money_flow.services;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface TransactionsService {
    BigDecimal totalSpentByCategoryAndTime(LocalDate startTime, LocalDate endTime, Integer categoryId);
}
