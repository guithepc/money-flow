package com.pctheone.money_flow.services;

import com.pctheone.money_flow.dto.*;
import com.pctheone.money_flow.entities.OwnerEntity;
import com.pctheone.money_flow.enums.OnboardingStepEnum;
import com.pctheone.money_flow.enums.CategoryTypeEnum;
import com.pctheone.money_flow.enums.CurrencyEnum;
import com.pctheone.money_flow.repositories.OwnerRepository;
import com.pctheone.money_flow.repositories.TransactionsRepository;
import com.pctheone.money_flow.services.impl.AccountServiceImpl;
import com.pctheone.money_flow.services.impl.CategoryServiceImpl;
import com.pctheone.money_flow.services.impl.TransactionsServiceImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({CategoryServiceImpl.class, AccountServiceImpl.class, TransactionsServiceImpl.class})
public class CurrencyFilterTest {

    @Autowired
    OwnerRepository ownerRepository;

    @Autowired
    CategoryService categoryService;

    @Autowired
    AccountService accountService;

    @Autowired
    TransactionsService transactionsService;

    @Autowired
    TransactionsRepository transactionsRepository;

    Integer ownerId;
    Integer otherOwnerId;
    Integer brlA;
    Integer brlB;
    Integer eur;
    LocalDate start;
    LocalDate end;

    @BeforeEach
    void setUp(){
        ownerId = createOwner("cur");
        otherOwnerId = createOwner("other");
        categoryService.addCategory("mercado", CategoryTypeEnum.EXPENSE, ownerId);
        categoryService.addCategory("salario", CategoryTypeEnum.INCOME, ownerId);

        brlA = accountService.addAccount("Nubank", new BigDecimal("500"), CurrencyEnum.BRL, ownerId).getAccountId();
        brlB = accountService.addAccount("Itau", new BigDecimal("100"), CurrencyEnum.BRL, ownerId).getAccountId();
        eur = accountService.addAccount("Revolut", new BigDecimal("1000"), CurrencyEnum.EUR, ownerId).getAccountId();

        transactionsService.registerExpense(ownerId, "mercado", brlA, "30", "feira");
        transactionsService.registerExpense(ownerId, "mercado", brlB, "20", "padaria");
        transactionsService.registerExpense(ownerId, "mercado", eur, "7", "lidl");
        transactionsService.registerIncome(ownerId, "salario", brlA, "1000", "salario");
        transactionsService.registerIncome(ownerId, "salario", eur, "300", "freela");

        start = LocalDate.now().minusDays(1);
        end = LocalDate.now().plusDays(1);
    }

    @Test
    void currencyFilterNeverSumsAcrossCurrencies(){
        assertAmount("50", transactionsService.totalSpentByTime(start, end, null, CurrencyEnum.BRL, ownerId));
        assertAmount("7", transactionsService.totalSpentByTime(start, end, null, CurrencyEnum.EUR, ownerId));
        assertAmount("0", transactionsService.totalSpentByTime(start, end, null, CurrencyEnum.USD, ownerId));

        assertAmount("1000", transactionsService.totalIncomeByTime(start, end, null, CurrencyEnum.BRL, ownerId));
        assertAmount("300", transactionsService.totalIncomeByTime(start, end, null, CurrencyEnum.EUR, ownerId));
        assertAmount("0", transactionsService.totalRecurringByTime(start, end, null, CurrencyEnum.BRL, ownerId));
    }

    @Test
    void categoryRankingIsScopedToCurrency(){
        List<AmountAndCategoryDTO> brl = transactionsService.totalSpentInMonthByCategoryDesc(start, end, null, CurrencyEnum.BRL, ownerId);
        Assertions.assertEquals(1, brl.size());
        assertAmount("50", brl.getFirst().getTotal());

        List<AmountAndCategoryDTO> eurRanking = transactionsService.totalSpentInMonthByCategoryDesc(start, end, null, CurrencyEnum.EUR, ownerId);
        assertAmount("7", eurRanking.getFirst().getTotal());
    }

    @Test
    void monthlyAndDailySeriesAreScopedToCurrency(){
        List<MonthlyIncomeExpenseDTO> monthly = transactionsService.monthlyIncomeExpense(start, end, null, CurrencyEnum.EUR, ownerId);
        assertAmount("300", monthly.stream().map(MonthlyIncomeExpenseDTO::getIncome).reduce(BigDecimal.ZERO, BigDecimal::add));
        assertAmount("7", monthly.stream().map(MonthlyIncomeExpenseDTO::getExpense).reduce(BigDecimal.ZERO, BigDecimal::add));

        List<DailyBalanceDTO> daily = transactionsService.dailyBalance(start, end, null, CurrencyEnum.BRL, ownerId);
        assertAmount("950", daily.stream().map(DailyBalanceDTO::getNet).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    @Test
    void transactionListCarriesAccountCurrency(){
        List<TransactionDTO> brl = transactionsService.listAllTransactions(start, end, null, CurrencyEnum.BRL, ownerId);
        Assertions.assertEquals(3, brl.size());
        Assertions.assertTrue(brl.stream().allMatch(t -> t.getCurrency() == CurrencyEnum.BRL));

        List<TransactionDTO> all = transactionsService.listAllTransactions(start, end, null, null, ownerId);
        Assertions.assertEquals(5, all.size());
        Assertions.assertEquals(2, all.stream().filter(t -> t.getCurrency() == CurrencyEnum.EUR).count());
    }

    @Test
    void accountAndCurrencyFiltersCombine(){
        assertAmount("30", transactionsService.totalSpentByTime(start, end, brlA, CurrencyEnum.BRL, ownerId));
        assertAmount("30", transactionsService.totalSpentByTime(start, end, brlA, null, ownerId));
        //---- account in another currency than the filter -> nothing is aggregated
        assertAmount("0", transactionsService.totalSpentByTime(start, end, brlA, CurrencyEnum.EUR, ownerId));
        Assertions.assertTrue(transactionsService.listAllTransactions(start, end, eur, CurrencyEnum.BRL, ownerId).isEmpty());
        Assertions.assertTrue(transactionsService.totalSpentInMonthByCategoryDesc(start, end, eur, CurrencyEnum.BRL, ownerId).isEmpty());
    }

    @Test
    void currencyFilterDoesNotLeakOtherOwners(){
        assertAmount("0", transactionsService.totalSpentByTime(start, end, null, CurrencyEnum.BRL, otherOwnerId));
        Assertions.assertTrue(transactionsService.listAllTransactions(start, end, null, CurrencyEnum.BRL, otherOwnerId).isEmpty());
        Assertions.assertThrows(ResponseStatusException.class,
                () -> transactionsService.totalSpentByTime(start, end, brlA, CurrencyEnum.BRL, otherOwnerId));
        Assertions.assertThrows(ResponseStatusException.class,
                () -> transactionsService.listAllTransactions(start, end, brlA, null, otherOwnerId));
        Assertions.assertThrows(ResponseStatusException.class,
                () -> transactionsService.totalIncomeByTime(start, end, Integer.MAX_VALUE, CurrencyEnum.BRL, ownerId));
    }

    @Test
    void registrationResultCarriesAccountCurrencyAndNames(){
        TransactionRegistrationResultDTO result = transactionsService.registerExpense(ownerId, "mercado", eur, "10,50", "cafe");
        Assertions.assertEquals(CurrencyEnum.EUR, result.getCurrency());
        Assertions.assertEquals("Revolut", result.getAccountDescription());
        Assertions.assertEquals("Mercado", result.getCategoryDescription());
        assertAmount("1282.50", result.getNewBalance());
    }

    @Test
    void repositoryCurrencyQueriesIgnoreOtherCurrencies(){
        assertAmount("50", transactionsRepository.totalSpentByTimeByCurrency(start, end, ownerId, CurrencyEnum.BRL));
        assertAmount("300", transactionsRepository.totalIncomeByTimeByCurrency(start, end, ownerId, CurrencyEnum.EUR));
        Assertions.assertEquals(2, transactionsRepository.listAllTransactionByCurrency(start, end, ownerId, CurrencyEnum.EUR).size());
    }

    private void assertAmount(String expected, BigDecimal actual){
        Assertions.assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + " but was " + actual);
    }

    private Integer createOwner(String prefix){
        String email = prefix + "-" + UUID.randomUUID() + "@currency.test";
        OwnerEntity owner = ownerRepository.save(new OwnerEntity(null, prefix, email, null, "x".repeat(60), null, null, null, OnboardingStepEnum.DONE, null));
        return owner.getOwnerId();
    }
}
