package com.pctheone.money_flow.services;

import com.pctheone.money_flow.dto.AdminMetricsDTO;
import com.pctheone.money_flow.entities.OwnerEntity;
import com.pctheone.money_flow.enums.CategoryTypeEnum;
import com.pctheone.money_flow.enums.CurrencyEnum;
import com.pctheone.money_flow.enums.OnboardingStepEnum;
import com.pctheone.money_flow.repositories.OwnerRepository;
import com.pctheone.money_flow.services.impl.AccountServiceImpl;
import com.pctheone.money_flow.services.impl.CategoryServiceImpl;
import com.pctheone.money_flow.services.impl.TransactionsServiceImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

//---- Runs against the local DB which may already hold rows, so every assertion is a delta
//---- (before vs after) rather than an absolute count.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({CategoryServiceImpl.class, AccountServiceImpl.class, TransactionsServiceImpl.class, AdminMetricsService.class})
public class AdminMetricsTest {

    @Autowired
    OwnerRepository ownerRepository;

    @Autowired
    CategoryService categoryService;

    @Autowired
    AccountService accountService;

    @Autowired
    TransactionsService transactionsService;

    @Autowired
    AdminMetricsService service;

    @Test
    void countsReflectNewData() {
        AdminMetricsDTO before = service.collect();

        Integer owner = createOwner();
        Integer account = accountService.addAccount("Nubank", BigDecimal.TEN, CurrencyEnum.BRL, owner).getAccountId();
        categoryService.addCategory("mercado", CategoryTypeEnum.EXPENSE, owner);
        categoryService.addCategory("salario", CategoryTypeEnum.INCOME, owner);
        transactionsService.registerExpense(owner, "mercado", account, "30", "carne");
        transactionsService.registerExpense(owner, "mercado", account, "10", "pao");
        transactionsService.registerIncome(owner, "salario", account, "100", "sal");
        accountService.addAccount("Revolut", BigDecimal.TEN, CurrencyEnum.EUR, owner);

        AdminMetricsDTO after = service.collect();

        Assertions.assertEquals(before.totalUsers() + 1, after.totalUsers());
        Assertions.assertEquals(before.newUsers7d() + 1, after.newUsers7d());
        Assertions.assertEquals(before.activeUsers7d() + 1, after.activeUsers7d());
        Assertions.assertEquals(before.totalTransactions() + 3, after.totalTransactions());
        Assertions.assertEquals(before.transactionsToday() + 3, after.transactionsToday());
        Assertions.assertEquals(before.transactions7d() + 3, after.transactions7d());
        Assertions.assertEquals(before.expenseCount() + 2, after.expenseCount());
        Assertions.assertEquals(before.incomeCount() + 1, after.incomeCount());
        Assertions.assertEquals(before.totalAccounts() + 2, after.totalAccounts());
        Assertions.assertEquals(eurAccounts(before) + 1, eurAccounts(after));
        Assertions.assertEquals(todayTransactions(before) + 3, todayTransactions(after));
    }

    @Test
    void thirtyDayWindowIsAlwaysComplete() {
        AdminMetricsDTO metrics = service.collect();
        Assertions.assertEquals(30, metrics.last30Days().size());
        Assertions.assertEquals(LocalDate.now().toString(), metrics.last30Days().getLast().date());
    }

    private long todayTransactions(AdminMetricsDTO metrics) {
        String today = LocalDate.now().toString();
        return metrics.last30Days().stream()
                .filter(point -> point.date().equals(today))
                .mapToLong(AdminMetricsDTO.DailyPoint::transactions)
                .findFirst().orElse(0);
    }

    private long eurAccounts(AdminMetricsDTO metrics) {
        return metrics.accountsByCurrency().stream()
                .filter(count -> count.currency().equals("EUR"))
                .mapToLong(AdminMetricsDTO.CurrencyCount::count)
                .findFirst().orElse(0);
    }

    private Integer createOwner() {
        String email = "metrics-" + UUID.randomUUID() + "@metrics.test";
        OwnerEntity entity = ownerRepository.save(new OwnerEntity(null, "m", email, null, "x".repeat(60), null, null, null, OnboardingStepEnum.DONE, null, null, null, null));
        return entity.getOwnerId();
    }
}
