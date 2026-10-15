package com.pctheone.money_flow.services;

import com.pctheone.money_flow.entities.AccountEntity;
import com.pctheone.money_flow.entities.OwnerEntity;
import com.pctheone.money_flow.entities.TransactionsEntity;
import com.pctheone.money_flow.enums.OperationTypeEnum;
import com.pctheone.money_flow.repositories.AccountRepository;
import com.pctheone.money_flow.repositories.TransactionsRepository;
import com.pctheone.money_flow.services.impl.TransactionsServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Unit tests proving TransactionsServiceImpl.deleteTransaction is scoped to the
 * authenticated owner: deleting another owner's transaction must NOT delete it
 * and must NOT touch any balance.
 */
class DeleteTransactionIsolationTest {

    private static final Integer OWNER_A = 42;
    private static final Integer OWNER_B = 7;
    private static final Integer TX_ID = 1001;
    private static final Integer ACCOUNT_B = 55;
    private static final BigDecimal AMOUNT = new BigDecimal("150.00");

    private TransactionsRepository transactionsRepository;
    private AccountRepository accountRepository;
    private TransactionsServiceImpl service;

    @BeforeEach
    void setUp() {
        transactionsRepository = mock(TransactionsRepository.class);
        accountRepository = mock(AccountRepository.class);
        service = new TransactionsServiceImpl();
        inject(service, "transactionsRepository", transactionsRepository);
        inject(service, "accountRepository", accountRepository);
    }

    private static void inject(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    // Entities are @Getter-only (no setters/builders), so populate private fields
    // reflectively to build real instances for the service under test.
    private TransactionsEntity txOwnedBy(Integer ownerId, OperationTypeEnum type) {
        OwnerEntity owner = new OwnerEntity();
        owner.setOwnerId(ownerId);

        AccountEntity account = new AccountEntity();
        setField(account, "accountId", ACCOUNT_B);

        TransactionsEntity tx = new TransactionsEntity();
        setField(tx, "owner", owner);
        setField(tx, "account", account);
        setField(tx, "operationType", type);
        setField(tx, "amount", AMOUNT);
        setField(tx, "description", "victim-tx");
        return tx;
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void wrongOwner_doesNotDeleteAndDoesNotTouchBalance() {
        // Transaction belongs to B, but A tries to delete it.
        when(transactionsRepository.findById(TX_ID))
                .thenReturn(Optional.of(txOwnedBy(OWNER_B, OperationTypeEnum.EXPENSE)));

        assertThatThrownBy(() -> service.deleteTransaction(TX_ID, OWNER_A))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        verify(transactionsRepository, never()).deleteById(any());
        verify(accountRepository, never()).incrementBalance(any(), any());
        verify(accountRepository, never()).decrementBalance(any(), any());
    }

    @Test
    void correctOwner_expense_deletesAndRestoresBalance() {
        when(transactionsRepository.findById(TX_ID))
                .thenReturn(Optional.of(txOwnedBy(OWNER_A, OperationTypeEnum.EXPENSE)));

        String result = service.deleteTransaction(TX_ID, OWNER_A);

        assertThat(result).contains("Transaction deleted");
        verify(transactionsRepository).deleteById(TX_ID);
        // Deleting an expense gives the money back.
        verify(accountRepository).incrementBalance(ACCOUNT_B, AMOUNT);
        verify(accountRepository, never()).decrementBalance(any(), any());
    }

    @Test
    void correctOwner_income_deletesAndRemovesBalance() {
        when(transactionsRepository.findById(TX_ID))
                .thenReturn(Optional.of(txOwnedBy(OWNER_A, OperationTypeEnum.INCOME)));

        service.deleteTransaction(TX_ID, OWNER_A);

        verify(transactionsRepository).deleteById(TX_ID);
        verify(accountRepository).decrementBalance(ACCOUNT_B, AMOUNT);
        verify(accountRepository, never()).incrementBalance(any(), any());
    }

    @Test
    void missingTransaction_returnsNotFoundMessageAndDeletesNothing() {
        when(transactionsRepository.findById(TX_ID)).thenReturn(Optional.empty());

        String result = service.deleteTransaction(TX_ID, OWNER_A);

        assertThat(result).contains("No transactions found");
        verify(transactionsRepository, never()).deleteById(any());
        verify(accountRepository, never()).incrementBalance(any(), any());
        verify(accountRepository, never()).decrementBalance(any(), any());
    }
}
