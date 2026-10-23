package com.pctheone.money_flow.services;

import com.pctheone.money_flow.dto.AdminMetricsDTO;
import com.pctheone.money_flow.repositories.MetricsRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminMetricsService {

    private static final int WINDOW_DAYS = 30;

    private final MetricsRepository metrics;
    //---- Same zone as the rest of the app; the daily buckets must line up with the SQL current_date.
    private final Clock clock = Clock.systemDefaultZone();

    public AdminMetricsService(MetricsRepository metrics) {
        this.metrics = metrics;
    }

    public AdminMetricsDTO collect() {
        return new AdminMetricsDTO(
                metrics.totalUsers(),
                metrics.usersWithTelegram(),
                metrics.usersAcceptedTerms(),
                metrics.newUsers(7),
                metrics.newUsers(30),
                metrics.activeUsers(7),
                metrics.activeUsers(30),
                metrics.totalTransactions(),
                metrics.transactionsToday(),
                metrics.transactionsLastDays(7),
                metrics.transactionsLastDays(30),
                metrics.transactionsByType("EXPENSE"),
                metrics.transactionsByType("INCOME"),
                metrics.transactionsByType("TRANSFER"),
                metrics.totalAccounts(),
                toCurrencyCounts(metrics.accountsByCurrency()),
                last30Days()
        );
    }

    private List<AdminMetricsDTO.CurrencyCount> toCurrencyCounts(List<Object[]> rows) {
        List<AdminMetricsDTO.CurrencyCount> result = new ArrayList<>();
        for (Object[] row : rows) {
            result.add(new AdminMetricsDTO.CurrencyCount((String) row[0], asLong(row[1])));
        }
        return result;
    }

    //---- One point per day for the last 30 days (oldest first), zero where there was no activity.
    private List<AdminMetricsDTO.DailyPoint> last30Days() {
        Map<String, Long> transactionsByDay = toMap(metrics.transactionsPerDay());
        Map<String, Long> signupsByDay = toMap(metrics.signupsPerDay());

        List<AdminMetricsDTO.DailyPoint> points = new ArrayList<>(WINDOW_DAYS);
        LocalDate today = LocalDate.now(clock);
        for (int i = WINDOW_DAYS - 1; i >= 0; i--) {
            String date = today.minusDays(i).toString();
            points.add(new AdminMetricsDTO.DailyPoint(
                    date,
                    transactionsByDay.getOrDefault(date, 0L),
                    signupsByDay.getOrDefault(date, 0L)));
        }
        return points;
    }

    private Map<String, Long> toMap(List<Object[]> rows) {
        Map<String, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put((String) row[0], asLong(row[1]));
        }
        return map;
    }

    private long asLong(Object value) {
        return ((Number) value).longValue();
    }
}
