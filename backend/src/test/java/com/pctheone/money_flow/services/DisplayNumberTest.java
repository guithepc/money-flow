package com.pctheone.money_flow.services;

import com.pctheone.money_flow.dto.AccountDTO;
import com.pctheone.money_flow.dto.TransactionDTO;
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
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

//---- Per-owner numbers (V13 trigger) against a real database: what the bot shows and accepts instead of global ids.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({CategoryServiceImpl.class, AccountServiceImpl.class, TransactionsServiceImpl.class})
public class DisplayNumberTest {

    @Autowired
    OwnerRepository ownerRepository;

    @Autowired
    AccountService accountService;

    @Autowired
    CategoryService categoryService;

    @Autowired
    TransactionsService transactionsService;

    @Test
    void accountsAreNumberedPerOwner() {
        Integer a = createOwner("a");
        Integer b = createOwner("b");

        Assertions.assertEquals(1, addAccount("Nubank", a).getDisplayNumber());
        Assertions.assertEquals(2, addAccount("Inter", a).getDisplayNumber());
        Assertions.assertEquals(3, addAccount("Revolut", a).getDisplayNumber());
        Assertions.assertEquals(1, addAccount("Nubank", b).getDisplayNumber());

        Assertions.assertEquals(List.of(1, 2, 3), accountService.listAllAccounts(a).stream().map(AccountDTO::getDisplayNumber).toList());
    }

    @Test
    void numberResolvesOnlyWithinTheOwner() {
        Integer a = createOwner("a");
        Integer b = createOwner("b");
        AccountDTO nubankA = addAccount("Nubank", a);
        addAccount("Inter", a);
        AccountDTO nubankB = addAccount("Nubank", b);

        Assertions.assertEquals(nubankA.getAccountId(), accountService.accountIdByDisplayNumber(1, a));
        Assertions.assertEquals(nubankB.getAccountId(), accountService.accountIdByDisplayNumber(1, b));
        ResponseStatusException e = Assertions.assertThrows(ResponseStatusException.class, () -> accountService.accountIdByDisplayNumber(2, b));
        Assertions.assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
    }

    @Test
    void deletedNumbersAreNeverReused() {
        Integer owner = createOwner("reuse");
        addAccount("Nubank", owner);
        AccountDTO inter = addAccount("Inter", owner);
        accountService.deleteAccount(inter.getAccountId(), owner);

        Assertions.assertEquals(3, addAccount("Revolut", owner).getDisplayNumber());
    }

    @Test
    void transactionsAreNumberedAndDeletedPerOwner() {
        Integer a = createOwner("a");
        Integer b = createOwner("b");
        Integer accountA = addAccount("Nubank", a).getAccountId();
        Integer accountB = addAccount("Nubank", b).getAccountId();
        categoryService.addCategory("mercado", CategoryTypeEnum.EXPENSE, a);
        categoryService.addCategory("mercado", CategoryTypeEnum.EXPENSE, b);
        transactionsService.registerExpense(a, "mercado", accountA, "10", "feira");
        transactionsService.registerExpense(a, "mercado", accountA, "20", "carne");
        transactionsService.registerExpense(b, "mercado", accountB, "5", "pão");

        Assertions.assertEquals(List.of(1, 2), numbers(a, accountA));
        Assertions.assertEquals(List.of(1), numbers(b, accountB));

        //---- b's "/deletar_transacao 2" finds nothing: number 2 only exists for a.
        Assertions.assertTrue(transactionsService.deleteTransactionByDisplayNumber(2, b).isEmpty());
        Assertions.assertEquals(List.of(1, 2), numbers(a, accountA));

        Assertions.assertEquals("carne", transactionsService.deleteTransactionByDisplayNumber(2, a).orElseThrow().getDescription());
        Assertions.assertEquals(List.of(1), numbers(a, accountA));
    }

    private List<Integer> numbers(Integer owner, Integer account) {
        return transactionsService.listAllTransactions(LocalDate.now(), LocalDate.now(), account, null, owner).stream()
                .map(TransactionDTO::getDisplayNumber).sorted().toList();
    }

    private AccountDTO addAccount(String name, Integer owner) {
        return accountService.addAccount(name, new BigDecimal("100"), CurrencyEnum.BRL, owner);
    }

    private Integer createOwner(String prefix) {
        String email = prefix + "-" + UUID.randomUUID() + "@display.test";
        return ownerRepository.save(new OwnerEntity(null, prefix, email, null, "x".repeat(60), null, null, null, OnboardingStepEnum.DONE, null, null, null, null)).getOwnerId();
    }
}
