package com.pctheone.money_flow.services;

import com.pctheone.money_flow.dto.DeletedTransactionDTO;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({CategoryServiceImpl.class, AccountServiceImpl.class, TransactionsServiceImpl.class})
public class TransactionDeleteTest {

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
    void setUp() {
        owner = createOwner("delete");
        account = accountService.addAccount("Nubank", new BigDecimal("100"), CurrencyEnum.BRL, owner).getAccountId();
        categoryService.addCategory("mercado", CategoryTypeEnum.EXPENSE, owner);
        categoryService.addCategory("salario", CategoryTypeEnum.INCOME, owner);
    }

    //---- The PWA's undo: the id returned at registration (INSERT ... RETURNING) deletes exactly that transaction.
    @Test
    void registrationReturnsTheIdThatUndoesIt() {
        transactionsService.registerExpense(owner, "mercado", account, "10", "pão");
        Integer expenseId = transactionsService.registerExpense(owner, "mercado", account, "30", "carne").getTransactionId();
        Integer incomeId = transactionsService.registerIncome(owner, "salario", account, "50", "bônus").getTransactionId();

        Assertions.assertNotNull(incomeId);
        Assertions.assertEquals("carne", transactionsService.deleteTransaction(expenseId, owner).orElseThrow().getDescription());
        Assertions.assertEquals("bônus", transactionsService.deleteTransaction(incomeId, owner).orElseThrow().getDescription());
        Assertions.assertEquals(0, new BigDecimal("90").compareTo(balance()));
        Assertions.assertEquals(java.util.List.of("pão"), allTransactions().stream().map(t -> t.getDescription()).toList());
    }

    @Test
    void deletingExpenseGivesTheMoneyBack() {
        transactionsService.registerExpense(owner, "mercado", account, "30", "carne");
        Assertions.assertEquals(0, new BigDecimal("70").compareTo(balance()));
        Integer id = onlyTransaction().getId();

        Optional<DeletedTransactionDTO> deleted = transactionsService.deleteTransaction(id, owner);

        Assertions.assertTrue(deleted.isPresent());
        Assertions.assertEquals("carne", deleted.get().getDescription());
        Assertions.assertEquals(0, new BigDecimal("100").compareTo(balance()));
        Assertions.assertTrue(allTransactions().isEmpty());
    }

    @Test
    void deletingIncomeTakesTheMoneyAway() {
        transactionsService.registerIncome(owner, "salario", account, "200", "outubro");
        Assertions.assertEquals(0, new BigDecimal("300").compareTo(balance()));
        Integer id = onlyTransaction().getId();

        transactionsService.deleteTransaction(id, owner);

        Assertions.assertEquals(0, new BigDecimal("100").compareTo(balance()));
    }

    @Test
    void anotherOwnerCannotDeleteAndSeesItAsMissing() {
        transactionsService.registerExpense(owner, "mercado", account, "30", "carne");
        Integer intruder = createOwner("intruder");
        Integer id = onlyTransaction().getId();

        Assertions.assertTrue(transactionsService.deleteTransaction(id, intruder).isEmpty());
        Assertions.assertEquals(0, new BigDecimal("70").compareTo(balance()));
        Assertions.assertFalse(allTransactions().isEmpty());
    }

    @Test
    void missingTransactionReturnsEmpty() {
        Assertions.assertTrue(transactionsService.deleteTransaction(Integer.MAX_VALUE, owner).isEmpty());
    }

    private TransactionDTO onlyTransaction() {
        return allTransactions().getFirst();
    }

    private java.util.List<TransactionDTO> allTransactions() {
        return transactionsService.listAllTransactions(LocalDate.now(), LocalDate.now(), account, null, owner);
    }

    private BigDecimal balance() {
        return accountService.listAllAccounts(owner).getFirst().getAmount();
    }

    private Integer createOwner(String prefix) {
        String email = prefix + "-" + UUID.randomUUID() + "@delete.test";
        OwnerEntity entity = ownerRepository.save(new OwnerEntity(null, prefix, email, null, "x".repeat(60), null, null, null, OnboardingStepEnum.DONE, null, null, null, null));
        return entity.getOwnerId();
    }
}
