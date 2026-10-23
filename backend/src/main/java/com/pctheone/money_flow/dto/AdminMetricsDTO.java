package com.pctheone.money_flow.dto;

import java.util.List;

//---- Global volumetry for the admin dashboard. All counts are across every owner.
public record AdminMetricsDTO(
        long totalUsers,
        long usersWithTelegram,
        long usersAcceptedTerms,
        long newUsers7d,
        long newUsers30d,
        long activeUsers7d,
        long activeUsers30d,
        long totalTransactions,
        long transactionsToday,
        long transactions7d,
        long transactions30d,
        long expenseCount,
        long incomeCount,
        long transferCount,
        long totalAccounts,
        List<CurrencyCount> accountsByCurrency,
        List<DailyPoint> last30Days
) {
    public record CurrencyCount(String currency, long count) {}

    //---- One day in the 30-day window; transactions and signups on that date. Gaps filled with 0.
    public record DailyPoint(String date, long transactions, long signups) {}
}
