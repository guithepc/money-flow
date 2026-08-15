package com.pctheone.money_flow.utils;

import com.pctheone.money_flow.exceptions.InvalidDateRangeException;

import java.time.LocalDate;

public class DateUtils {
    public static void validateTimeRange(LocalDate startDate, LocalDate endDate){
        if (startDate.isAfter(endDate))
            throw new InvalidDateRangeException("Start date can not be after end date");
    }
}
