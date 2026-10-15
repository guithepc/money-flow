package com.pctheone.money_flow.services;

import com.pctheone.money_flow.dto.TransactionDTO;
import com.pctheone.money_flow.dto.TransactionUpdateRequestDTO;
import com.pctheone.money_flow.entities.OwnerEntity;
import com.pctheone.money_flow.enums.CategoryTypeEnum;
import com.pctheone.money_flow.enums.CurrencyEnum;
import com.pctheone.money_flow.enums.OnboardingStepEnum;
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
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({CategoryServiceImpl.class, AccountServiceImpl.class, TransactionsServiceImpl.class})
public class TransactionUpdateTest {

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

    @BeforeEach
    void setUp(){
        owner = createOwner("edit");
        account = accountService.addAccount("Nubank", new BigDecimal("100"), CurrencyEnum.BRL, owner).getAccountId();
        categoryService.addCategory("mercado", CategoryTypeEnum.EXPENSE, owner);
        categoryService.addCategory("casa", CategoryTypeEnum.EXPENSE, owner);
        categoryService.addCategory("salario", CategoryTypeEnum.INCOME, owner);
    }

    @Test
    void editingExpenseUpdatesFieldsAndBalanceByTheDifference(){
        transactionsService.registerExpense(owner, "mercado", account, "30", "carne");
        Assertions.assertEquals(0, new BigDecimal("70").compareTo(balance()));
        TransactionDTO original = onlyTransaction();

        TransactionDTO updated = transactionsService.updateTransaction(original.getId(),
                new TransactionUpdateRequestDTO("  aluguel  ", categoryId("Casa"), new BigDecimal("50.00")), owner);

        Assertions.assertEquals("aluguel", updated.getDescription());
        Assertions.assertEquals("Casa", updated.getCategoryDescription());
        Assertions.assertEquals(0, new BigDecimal("50").compareTo(updated.getAmount()));
        Assertions.assertEquals(CurrencyEnum.BRL, updated.getCurrency());

        TransactionDTO stored = onlyTransaction();
        Assertions.assertEquals("aluguel", stored.getDescription());
        Assertions.assertEquals("Casa", stored.getCategoryDescription());
        Assertions.assertEquals(0, new BigDecimal("50").compareTo(stored.getAmount()));
        Assertions.assertEquals(0, new BigDecimal("50").compareTo(balance()));
    }

    @Test
    void loweringAnExpenseGivesMoneyBack(){
        transactionsService.registerExpense(owner, "mercado", account, "30", "carne");
        transactionsService.updateTransaction(onlyTransaction().getId(),
                new TransactionUpdateRequestDTO("carne", categoryId("Mercado"), new BigDecimal("10")), owner);
        Assertions.assertEquals(0, new BigDecimal("90").compareTo(balance()));
    }

    @Test
    void editingIncomeMovesBalanceTheOtherWay(){
        transactionsService.registerIncome(owner, "salario", account, "200", "outubro");
        Assertions.assertEquals(0, new BigDecimal("300").compareTo(balance()));

        transactionsService.updateTransaction(onlyTransaction().getId(),
                new TransactionUpdateRequestDTO("outubro", categoryId("Salario"), new BigDecimal("150")), owner);
        Assertions.assertEquals(0, new BigDecimal("250").compareTo(balance()));
    }

    @Test
    void anotherOwnerCannotEdit(){
        transactionsService.registerExpense(owner, "mercado", account, "30", "carne");
        Integer intruder = createOwner("intruder");
        Integer id = onlyTransaction().getId();

        ResponseStatusException e = Assertions.assertThrows(ResponseStatusException.class, () -> transactionsService.updateTransaction(id,
                new TransactionUpdateRequestDTO("x", categoryId("Mercado"), BigDecimal.ONE), intruder));
        Assertions.assertEquals(HttpStatus.FORBIDDEN, e.getStatusCode());
        Assertions.assertEquals(0, new BigDecimal("70").compareTo(balance()));
    }

    @Test
    void anotherOwnersCategoryIsRejected(){
        transactionsService.registerExpense(owner, "mercado", account, "30", "carne");
        Integer other = createOwner("other");
        categoryService.addCategory("secret", CategoryTypeEnum.EXPENSE, other);
        Integer foreignCategory = categoryService.findByDescription("secret", other).orElseThrow().getCategoryId();
        Integer id = onlyTransaction().getId();

        Assertions.assertThrows(CategoryNotFoundException.class, () -> transactionsService.updateTransaction(id,
                new TransactionUpdateRequestDTO("carne", foreignCategory, BigDecimal.ONE), owner));
    }

    @Test
    void invalidInputIsRejectedWithoutTouchingTheBalance(){
        transactionsService.registerExpense(owner, "mercado", account, "30", "carne");
        Integer id = onlyTransaction().getId();
        Integer mercado = categoryId("Mercado");

        assertBadRequest(id, new TransactionUpdateRequestDTO("carne", categoryId("Salario"), BigDecimal.TEN));
        assertBadRequest(id, new TransactionUpdateRequestDTO("carne", mercado, BigDecimal.ZERO));
        assertBadRequest(id, new TransactionUpdateRequestDTO("carne", mercado, new BigDecimal("-5")));
        assertBadRequest(id, new TransactionUpdateRequestDTO("carne", mercado, new BigDecimal("1.999")));
        assertBadRequest(id, new TransactionUpdateRequestDTO("carne", mercado, null));
        assertBadRequest(id, new TransactionUpdateRequestDTO("   ", mercado, BigDecimal.TEN));
        assertBadRequest(id, new TransactionUpdateRequestDTO("x".repeat(256), mercado, BigDecimal.TEN));
        assertBadRequest(id, new TransactionUpdateRequestDTO("carne", null, BigDecimal.TEN));

        Assertions.assertEquals(0, new BigDecimal("70").compareTo(balance()));
        Assertions.assertEquals("carne", onlyTransaction().getDescription());
    }

    @Test
    void missingTransactionIsNotFound(){
        ResponseStatusException e = Assertions.assertThrows(ResponseStatusException.class, () -> transactionsService.updateTransaction(Integer.MAX_VALUE,
                new TransactionUpdateRequestDTO("x", categoryId("Mercado"), BigDecimal.ONE), owner));
        Assertions.assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
    }

    private void assertBadRequest(Integer id, TransactionUpdateRequestDTO request){
        ResponseStatusException e = Assertions.assertThrows(ResponseStatusException.class,
                () -> transactionsService.updateTransaction(id, request, owner));
        Assertions.assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
    }

    private TransactionDTO onlyTransaction(){
        return transactionsService.listAllTransactions(LocalDate.now(), LocalDate.now(), account, null, owner).getFirst();
    }

    private BigDecimal balance(){
        return accountService.listAllAccounts(owner).getFirst().getAmount();
    }

    private Integer categoryId(String description){
        return categoryService.findByDescription(description, owner).orElseThrow().getCategoryId();
    }

    private Integer createOwner(String prefix){
        String email = prefix + "-" + UUID.randomUUID() + "@update.test";
        OwnerEntity owner = ownerRepository.save(new OwnerEntity(null, prefix, email, null, "x".repeat(60), null, null, null, OnboardingStepEnum.DONE, null));
        return owner.getOwnerId();
    }
}
