package com.pctheone.money_flow.services;

import com.pctheone.money_flow.dto.AccountDTO;
import com.pctheone.money_flow.dto.CategoryCreationResultDTO;
import com.pctheone.money_flow.dto.CategoryDTO;
import com.pctheone.money_flow.entities.CategoryEntity;
import com.pctheone.money_flow.entities.OwnerEntity;
import com.pctheone.money_flow.enums.OnboardingStepEnum;
import com.pctheone.money_flow.enums.CategoryTypeEnum;
import com.pctheone.money_flow.enums.CurrencyEnum;
import com.pctheone.money_flow.exceptions.CategoryNotFoundException;
import com.pctheone.money_flow.repositories.OwnerRepository;
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
public class CategoryIsolationTest {

    @Autowired
    OwnerRepository ownerRepository;

    @Autowired
    CategoryService categoryService;

    @Autowired
    AccountService accountService;

    @Autowired
    TransactionsService transactionsService;

    Integer ownerA;
    Integer ownerB;

    @BeforeEach
    void setUp(){
        ownerA = createOwner("a");
        ownerB = createOwner("b");
        categoryService.addCategory("secret", CategoryTypeEnum.EXPENSE, ownerB);
    }

    @Test
    void ownerCannotListAnotherOwnersCategories(){
        Assertions.assertTrue(categoryService.listAllCategories(ownerA).isEmpty());

        Assertions.assertEquals(1, categoryService.listAllCategories(ownerB).size());
        CategoryDTO category = categoryService.listAllCategories(ownerB).getFirst();
        Assertions.assertEquals("Secret", category.getCategoryDescription());
        Assertions.assertEquals(CategoryTypeEnum.EXPENSE, category.getType());
    }

    @Test
    void ownerCannotFindAnotherOwnersCategoryByDescription(){
        Assertions.assertTrue(categoryService.findByDescription("secret", ownerA).isEmpty());
        Assertions.assertTrue(categoryService.findByDescription("secret", ownerB).isPresent());
    }

    @Test
    void ownerCannotRegisterTransactionWithAnotherOwnersCategory(){
        AccountDTO account = accountService.addAccount("Wallet", new BigDecimal("100"), CurrencyEnum.BRL, ownerA);

        Assertions.assertThrows(CategoryNotFoundException.class,
                () -> transactionsService.registerExpense(ownerA, "secret", account.getAccountId(), "10", "test"));
        Assertions.assertThrows(CategoryNotFoundException.class,
                () -> transactionsService.registerIncome(ownerA, "secret", account.getAccountId(), "10", "test"));
    }

    @Test
    void ownerCannotQueryTotalsOfAnotherOwnersCategory(){
        Integer categoryOfB = categoryService.findByDescription("secret", ownerB).map(CategoryEntity::getCategoryId).orElseThrow();
        LocalDate today = LocalDate.now();

        Assertions.assertThrows(CategoryNotFoundException.class,
                () -> transactionsService.totalSpentByCategoryAndTime(today.minusDays(30), today, categoryOfB, ownerA));
    }

    @Test
    void sameDescriptionCanExistForDifferentOwners(){
        Assertions.assertTrue(categoryService.addCategory("secret", CategoryTypeEnum.INCOME, ownerA).isCreated());
        CategoryCreationResultDTO again = categoryService.addCategory("secret", CategoryTypeEnum.INCOME, ownerB);
        Assertions.assertFalse(again.isCreated());
        Assertions.assertEquals(CategoryTypeEnum.EXPENSE, again.getCategory().getType());

        CategoryEntity ofA = categoryService.findByDescription("secret", ownerA).orElseThrow();
        CategoryEntity ofB = categoryService.findByDescription("secret", ownerB).orElseThrow();
        Assertions.assertNotEquals(ofA.getCategoryId(), ofB.getCategoryId());
        Assertions.assertEquals(CategoryTypeEnum.INCOME, ofA.getType());
    }

    private Integer createOwner(String prefix){
        String email = prefix + "-" + UUID.randomUUID() + "@isolation.test";
        OwnerEntity owner = ownerRepository.save(new OwnerEntity(null, prefix, email, null, "x".repeat(60), null, null, null, OnboardingStepEnum.DONE, null));
        return owner.getOwnerId();
    }
}
