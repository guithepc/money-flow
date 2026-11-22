package com.pctheone.money_flow.services;

import com.pctheone.money_flow.entities.OwnerEntity;
import com.pctheone.money_flow.enums.CategoryTypeEnum;
import com.pctheone.money_flow.enums.CurrencyEnum;
import com.pctheone.money_flow.enums.OnboardingStepEnum;
import com.pctheone.money_flow.repositories.OwnerRepository;
import com.pctheone.money_flow.repositories.TransactionsRepository;
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
import java.util.Optional;
import java.util.UUID;

//---- Account used when the user doesn't say one ("gastei 25 no supermercado"), against a real database.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({CategoryServiceImpl.class, AccountServiceImpl.class, TransactionsServiceImpl.class})
public class DefaultAccountTest {

    @Autowired
    OwnerRepository ownerRepository;

    @Autowired
    AccountService accountService;

    @Autowired
    CategoryService categoryService;

    @Autowired
    TransactionsService transactionsService;

    @Autowired
    TransactionsRepository transactionsRepository;

    @Test
    void ownerWithoutAccountsHasNoDefault() {
        Assertions.assertEquals(Optional.empty(), accountService.defaultAccountId(createOwner()));
    }

    @Test
    void onlyAccountIsTheDefault() {
        Integer owner = createOwner();
        Integer nubank = addAccount("Nubank", owner);

        Assertions.assertEquals(Optional.of(nubank), accountService.defaultAccountId(owner));
    }

    @Test
    void withoutRecentTransactionsTheFirstAccountWins() {
        Integer owner = createOwner();
        Integer nubank = addAccount("Nubank", owner);
        addAccount("Inter", owner);

        Assertions.assertEquals(Optional.of(nubank), accountService.defaultAccountId(owner));
    }

    @Test
    void mostUsedAccountInTheLast90DaysWins() {
        Integer owner = createOwner();
        Integer nubank = addAccount("Nubank", owner);
        Integer inter = addAccount("Inter", owner);
        Integer category = categoryService.addCategory("mercado", CategoryTypeEnum.EXPENSE, owner).getCategory().getCategoryId();
        //---- Nubank: 3 old transactions (outside the window). Inter: 2 recent ones.
        for (int i = 0; i < 3; i++)
            transactionsRepository.insertSingleExpense(owner, nubank, category, "antigo", LocalDate.now().minusDays(120), BigDecimal.ONE);
        transactionsService.registerExpense(owner, "mercado", inter, "1", "feira");
        transactionsService.registerExpense(owner, "mercado", inter, "1", "feira");

        Assertions.assertEquals(Optional.of(inter), accountService.defaultAccountId(owner));
    }

    @Test
    void anotherOwnersTransactionsDoNotCount() {
        Integer owner = createOwner();
        Integer other = createOwner();
        Integer nubank = addAccount("Nubank", owner);
        addAccount("Inter", owner);
        Integer otherAccount = addAccount("Nubank", other);
        categoryService.addCategory("mercado", CategoryTypeEnum.EXPENSE, other);
        transactionsService.registerExpense(other, "mercado", otherAccount, "1", "feira");

        Assertions.assertEquals(Optional.of(nubank), accountService.defaultAccountId(owner));
    }

    private Integer addAccount(String name, Integer owner) {
        return accountService.addAccount(name, new BigDecimal("100"), CurrencyEnum.BRL, owner).getAccountId();
    }

    private Integer createOwner() {
        String email = "default-" + UUID.randomUUID() + "@account.test";
        return ownerRepository.save(new OwnerEntity(null, "default", email, null, "x".repeat(60), null, null, null, OnboardingStepEnum.DONE, null, null, null, null)).getOwnerId();
    }
}
