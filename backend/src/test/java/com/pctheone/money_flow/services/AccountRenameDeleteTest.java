package com.pctheone.money_flow.services;

import com.pctheone.money_flow.dto.AccountDTO;
import com.pctheone.money_flow.dto.AccountDeletionSummaryDTO;
import com.pctheone.money_flow.entities.OwnerEntity;
import com.pctheone.money_flow.enums.CategoryTypeEnum;
import com.pctheone.money_flow.enums.CurrencyEnum;
import com.pctheone.money_flow.enums.OnboardingStepEnum;
import com.pctheone.money_flow.exceptions.DuplicateAccountNameException;
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
import java.util.List;
import java.util.UUID;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({CategoryServiceImpl.class, AccountServiceImpl.class, TransactionsServiceImpl.class})
public class AccountRenameDeleteTest {

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
        owner = createOwner("acc");
        account = accountService.addAccount("Nubank", new BigDecimal("100"), CurrencyEnum.BRL, owner).getAccountId();
        categoryService.addCategory("mercado", CategoryTypeEnum.EXPENSE, owner);
    }

    @Test
    void renameChangesTheName() {
        AccountDTO renamed = accountService.renameAccount(account, "  Inter  ", owner);
        Assertions.assertEquals("Inter", renamed.getDescription());
        Assertions.assertEquals("Inter", accountService.listAllAccounts(owner).getFirst().getDescription());
    }

    @Test
    void renameToAnExistingNameIsRejected() {
        accountService.addAccount("Inter", BigDecimal.ZERO, CurrencyEnum.BRL, owner);
        Assertions.assertThrows(DuplicateAccountNameException.class,
                () -> accountService.renameAccount(account, "inter", owner));
    }

    @Test
    void renameToTheSameNameIsAllowed() {
        AccountDTO renamed = accountService.renameAccount(account, "nubank", owner);
        Assertions.assertEquals("nubank", renamed.getDescription());
    }

    @Test
    void renameRejectsBlankName() {
        ResponseStatusException e = Assertions.assertThrows(ResponseStatusException.class,
                () -> accountService.renameAccount(account, "   ", owner));
        Assertions.assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
    }

    @Test
    void anotherOwnerCannotRename() {
        Integer intruder = createOwner("intruder");
        ResponseStatusException e = Assertions.assertThrows(ResponseStatusException.class,
                () -> accountService.renameAccount(account, "Hacked", intruder));
        Assertions.assertEquals(HttpStatus.FORBIDDEN, e.getStatusCode());
    }

    @Test
    void missingAccountIsNotFound() {
        ResponseStatusException e = Assertions.assertThrows(ResponseStatusException.class,
                () -> accountService.renameAccount(Integer.MAX_VALUE, "x", owner));
        Assertions.assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
    }

    @Test
    void listReportsTheTransactionCount() {
        transactionsService.registerExpense(owner, "mercado", account, "30", "carne");
        transactionsService.registerExpense(owner, "mercado", account, "10", "pao");
        Assertions.assertEquals(2, accountService.listAllAccounts(owner).getFirst().getTransactionCount());
    }

    @Test
    void deleteRemovesTheAccountAndItsTransactionsOnly() {
        transactionsService.registerExpense(owner, "mercado", account, "30", "carne");
        transactionsService.registerExpense(owner, "mercado", account, "10", "pao");
        Integer other = accountService.addAccount("Inter", new BigDecimal("50"), CurrencyEnum.BRL, owner).getAccountId();
        transactionsService.registerExpense(owner, "mercado", other, "5", "cafe");

        AccountDeletionSummaryDTO summary = accountService.deleteAccount(account, owner);

        Assertions.assertEquals(2, summary.getDeletedTransactions());
        List<AccountDTO> remaining = accountService.listAllAccounts(owner);
        Assertions.assertEquals(1, remaining.size());
        Assertions.assertEquals(other, remaining.getFirst().getAccountId());
        Assertions.assertEquals(1, remaining.getFirst().getTransactionCount());
    }

    @Test
    void anotherOwnerCannotDelete() {
        transactionsService.registerExpense(owner, "mercado", account, "30", "carne");
        Integer intruder = createOwner("intruder");

        ResponseStatusException e = Assertions.assertThrows(ResponseStatusException.class,
                () -> accountService.deleteAccount(account, intruder));
        Assertions.assertEquals(HttpStatus.FORBIDDEN, e.getStatusCode());
        Assertions.assertEquals(1, accountService.listAllAccounts(owner).size());
    }

    private Integer createOwner(String prefix) {
        String email = prefix + "-" + UUID.randomUUID() + "@account.test";
        OwnerEntity entity = ownerRepository.save(new OwnerEntity(null, prefix, email, null, "x".repeat(60), null, null, null, OnboardingStepEnum.DONE, null, null, null, null));
        return entity.getOwnerId();
    }
}
