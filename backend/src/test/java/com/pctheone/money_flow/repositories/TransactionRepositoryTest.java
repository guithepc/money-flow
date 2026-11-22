package com.pctheone.money_flow.repositories;

import com.pctheone.money_flow.entities.OwnerEntity;
import com.pctheone.money_flow.enums.CategoryTypeEnum;
import com.pctheone.money_flow.enums.CurrencyEnum;
import com.pctheone.money_flow.enums.OnboardingStepEnum;
import com.pctheone.money_flow.services.AccountService;
import com.pctheone.money_flow.services.CategoryService;
import com.pctheone.money_flow.services.TransactionsService;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({CategoryServiceImpl.class, AccountServiceImpl.class, TransactionsServiceImpl.class})
public class TransactionRepositoryTest {

    @Autowired
    TransactionsRepository transactionsRepository;

    @Autowired
    OwnerRepository ownerRepository;

    @Autowired
    CategoryService categoryService;

    @Autowired
    AccountService accountService;

    @Autowired
    TransactionsService transactionsService;

    Integer owner;
    Integer account;
    Integer category;

    @BeforeEach
    void setUp() {
        String email = "repo-" + UUID.randomUUID() + "@repository.test";
        owner = ownerRepository.save(new OwnerEntity(null, "repo", email, null, "x".repeat(60), null, null, null, OnboardingStepEnum.DONE, null, null, null, null)).getOwnerId();
        account = accountService.addAccount("Nubank", new BigDecimal("100"), CurrencyEnum.BRL, owner).getAccountId();
        category = categoryService.addCategory("mercado", CategoryTypeEnum.EXPENSE, owner).getCategory().getCategoryId();
    }

    @Test
    public void testUnexistentCategory(){
        BigDecimal result = transactionsRepository.totalSpentByCategoryByTime(LocalDate.now(), LocalDate.now(), Integer.MAX_VALUE, owner);

        Assertions.assertEquals(0, result.compareTo(BigDecimal.ZERO));
    }

    @Test
    public void testExistentCategory(){
        transactionsService.registerExpense(owner, "mercado", account, "30", "carne");

        BigDecimal result = transactionsRepository.totalSpentByCategoryByTime(LocalDate.now(), LocalDate.now(), category, owner);

        Assertions.assertEquals(0, new BigDecimal("30").compareTo(result));
    }
}
