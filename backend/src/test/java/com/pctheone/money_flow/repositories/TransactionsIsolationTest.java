package com.pctheone.money_flow.repositories;

import com.pctheone.money_flow.dto.AmountAndCategoryDTO;
import com.pctheone.money_flow.dto.TransactionDTO;
import com.pctheone.money_flow.entities.AccountEntity;
import com.pctheone.money_flow.entities.CategoryEntity;
import com.pctheone.money_flow.entities.OwnerEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cross-account data isolation (OWASP A01) at the repository layer.
 *
 * Seeds TWO independent owners (A and B), each with their own account(s),
 * category and transactions, then proves that EVERY read query:
 *  - returns ONLY the queried owner's rows / sums,
 *  - never leaks or sums in the other owner's data,
 *  - and is immune to IDOR: owner A passing owner B's accountId gets empty/zero.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class TransactionsIsolationTest {

    @Autowired
    TransactionsRepository transactionsRepository;
    @Autowired
    AccountRepository accountRepository;
    @Autowired
    CategoryRepository categoryRepository;
    @Autowired
    OwnerRepository ownerRepository;
    @Autowired
    TestEntityManager entityManager;

    // Fixed, isolated date window so seeded rows are found regardless of pre-existing DB data.
    private static final LocalDate START = LocalDate.of(2099, 1, 1);
    private static final LocalDate END = LocalDate.of(2099, 12, 31);
    private static final LocalDate TS = LocalDate.of(2099, 6, 15);
    private static final LocalDate TS2 = LocalDate.of(2099, 7, 15);

    private Integer ownerA;
    private Integer ownerB;
    private Integer accountA;
    private Integer accountB;
    private Integer categoryId;
    private String categoryDesc;

    // Amounts chosen so A and B differ and sums are unambiguous.
    private static final BigDecimal A_EXPENSE = new BigDecimal("100.00");
    private static final BigDecimal A_INCOME = new BigDecimal("300.00");
    private static final BigDecimal B_EXPENSE = new BigDecimal("777.00");
    private static final BigDecimal B_INCOME = new BigDecimal("999.00");

    @BeforeEach
    void seed() {
        ownerA = newOwner().getOwnerId();
        ownerB = newOwner().getOwnerId();

        categoryDesc = "Cat-" + UUID.randomUUID();
        categoryRepository.createCategory(categoryDesc);
        entityManager.flush();
        CategoryEntity category = categoryRepository.findByDescription(categoryDesc);
        categoryId = category.getCategoryId();

        String accDescA = "AccA-" + UUID.randomUUID();
        String accDescB = "AccB-" + UUID.randomUUID();
        accountRepository.addAccount(new BigDecimal("1000.00"), accDescA, ownerA);
        accountRepository.addAccount(new BigDecimal("1000.00"), accDescB, ownerB);
        entityManager.flush();
        accountA = accountRepository.findByOwnerOwnerIdOrderByAccountIdAsc(ownerA).getFirst().getAccountId();
        accountB = accountRepository.findByOwnerOwnerIdOrderByAccountIdAsc(ownerB).getFirst().getAccountId();

        // Owner A data
        transactionsRepository.insertSingleExpense(ownerA, accountA, categoryId, "A-expense", TS, A_EXPENSE);
        transactionsRepository.insertSingleIncome(ownerA, accountA, categoryId, "A-income", TS2, A_INCOME);
        // Owner B data
        transactionsRepository.insertSingleExpense(ownerB, accountB, categoryId, "B-expense", TS, B_EXPENSE);
        transactionsRepository.insertSingleIncome(ownerB, accountB, categoryId, "B-income", TS2, B_INCOME);

        entityManager.flush();
        entityManager.clear();
    }

    private OwnerEntity newOwner() {
        OwnerEntity o = new OwnerEntity();
        o.setName("owner-" + UUID.randomUUID());
        o.setEmail(UUID.randomUUID() + "@test.local");
        o.setPasswordHash("$2a$10$abcdefghijklmnopqrstuv0123456789012345678901234567890"); // 60 chars
        return ownerRepository.save(o);
    }

    // ---------- listAllTransaction ----------
    @Test
    void listAllTransaction_returnsOnlyOwnersRows() {
        List<TransactionDTO> a = transactionsRepository.listAllTransaction(START, END, ownerA);
        assertThat(a).extracting(TransactionDTO::getDescription)
                .containsExactlyInAnyOrder("A-expense", "A-income")
                .doesNotContain("B-expense", "B-income");

        List<TransactionDTO> b = transactionsRepository.listAllTransaction(START, END, ownerB);
        assertThat(b).extracting(TransactionDTO::getDescription)
                .containsExactlyInAnyOrder("B-expense", "B-income")
                .doesNotContain("A-expense", "A-income");
    }

    @Test
    void listAllTransactionByAccount_ownersAccountOnly() {
        List<TransactionDTO> a = transactionsRepository.listAllTransactionByAccount(START, END, ownerA, accountA);
        assertThat(a).extracting(TransactionDTO::getDescription)
                .containsExactlyInAnyOrder("A-expense", "A-income");
    }

    @Test
    void listAllTransactionByAccount_idorReturnsEmpty() {
        // IDOR: A authenticated, but passing B's accountId must return nothing.
        List<TransactionDTO> leak = transactionsRepository.listAllTransactionByAccount(START, END, ownerA, accountB);
        assertThat(leak).isEmpty();
    }

    // ---------- totalSpentByTime ----------
    @Test
    void totalSpentByTime_onlyOwnersExpenses() {
        assertThat(transactionsRepository.totalSpentByTime(START, END, ownerA)).isEqualByComparingTo(A_EXPENSE);
        assertThat(transactionsRepository.totalSpentByTime(START, END, ownerB)).isEqualByComparingTo(B_EXPENSE);
    }

    @Test
    void totalSpentByTimeAndAccountId_idorReturnsZero() {
        assertThat(transactionsRepository.totalSpentByTimeAndAccountId(START, END, ownerA, accountA))
                .isEqualByComparingTo(A_EXPENSE);
        // IDOR: A + B's account -> zero (never B's 777).
        assertThat(transactionsRepository.totalSpentByTimeAndAccountId(START, END, ownerA, accountB))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ---------- totalIncomeByTime ----------
    @Test
    void totalIncomeByTime_onlyOwnersIncome() {
        assertThat(transactionsRepository.totalIncomeByTime(START, END, ownerA)).isEqualByComparingTo(A_INCOME);
        assertThat(transactionsRepository.totalIncomeByTime(START, END, ownerB)).isEqualByComparingTo(B_INCOME);
    }

    @Test
    void totalIncomeByTimeByAccount_idorReturnsZero() {
        assertThat(transactionsRepository.totalIncomeByTimeByAccount(START, END, ownerA, accountA))
                .isEqualByComparingTo(A_INCOME);
        assertThat(transactionsRepository.totalIncomeByTimeByAccount(START, END, ownerA, accountB))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ---------- totalRecurringByTime (no recurring seeded -> both zero, but still owner-scoped) ----------
    @Test
    void totalRecurringByTime_ownerScopedZero() {
        assertThat(transactionsRepository.totalRecurringByTime(START, END, ownerA)).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(transactionsRepository.totalRecurringByTime(START, END, ownerB)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void totalRecurringByTimeByAccount_idorReturnsZero() {
        assertThat(transactionsRepository.totalRecurringByTimeByAccount(START, END, ownerA, accountB))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ---------- totalSpentByCategoryByTime ----------
    @Test
    void totalSpentByCategoryByTime_onlyOwnersSpend() {
        assertThat(transactionsRepository.totalSpentByCategoryByTime(START, END, categoryId, ownerA))
                .isEqualByComparingTo(A_EXPENSE);
        assertThat(transactionsRepository.totalSpentByCategoryByTime(START, END, categoryId, ownerB))
                .isEqualByComparingTo(B_EXPENSE);
    }

    // ---------- totalSpentInMonthByCategoryDesc ----------
    @Test
    void totalSpentInMonthByCategoryDesc_onlyOwnersRows() {
        List<AmountAndCategoryDTO> a = transactionsRepository.totalSpentInMonthByCategoryDesc(START, END, ownerA);
        assertThat(a).hasSize(1);
        assertThat(a.getFirst().getCategoryDescription()).isEqualTo(categoryDesc);
        assertThat(a.getFirst().getTotal()).isEqualByComparingTo(A_EXPENSE);

        List<AmountAndCategoryDTO> b = transactionsRepository.totalSpentInMonthByCategoryDesc(START, END, ownerB);
        assertThat(b).hasSize(1);
        assertThat(b.getFirst().getTotal()).isEqualByComparingTo(B_EXPENSE);
    }

    @Test
    void totalSpentInMonthByCategoryDescByAccount_idorReturnsEmpty() {
        List<AmountAndCategoryDTO> own = transactionsRepository
                .totalSpentInMonthByCategoryDescByAccount(START, END, ownerA, accountA);
        assertThat(own).hasSize(1);
        assertThat(own.getFirst().getTotal()).isEqualByComparingTo(A_EXPENSE);

        List<AmountAndCategoryDTO> leak = transactionsRepository
                .totalSpentInMonthByCategoryDescByAccount(START, END, ownerA, accountB);
        assertThat(leak).isEmpty();
    }

    // ---------- incomeExpenseMonthly ----------
    @Test
    void incomeExpenseMonthly_onlyOwnersAmounts() {
        List<Object[]> a = transactionsRepository.incomeExpenseMonthly(START, END, ownerA);
        BigDecimal incomeA = sum(a, 1);
        BigDecimal expenseA = sum(a, 2);
        assertThat(incomeA).isEqualByComparingTo(A_INCOME);
        assertThat(expenseA).isEqualByComparingTo(A_EXPENSE);

        List<Object[]> b = transactionsRepository.incomeExpenseMonthly(START, END, ownerB);
        assertThat(sum(b, 1)).isEqualByComparingTo(B_INCOME);
        assertThat(sum(b, 2)).isEqualByComparingTo(B_EXPENSE);
    }

    @Test
    void incomeExpenseMonthlyByAccount_idorReturnsEmpty() {
        List<Object[]> own = transactionsRepository.incomeExpenseMonthlyByAccount(START, END, ownerA, accountA);
        assertThat(sum(own, 2)).isEqualByComparingTo(A_EXPENSE);

        List<Object[]> leak = transactionsRepository.incomeExpenseMonthlyByAccount(START, END, ownerA, accountB);
        assertThat(leak).isEmpty();
    }

    // ---------- balanceHistory ----------
    @Test
    void balanceHistory_onlyOwnersAmounts() {
        List<Object[]> a = transactionsRepository.balanceHistory(START, END, ownerA);
        assertThat(sum(a, 1)).isEqualByComparingTo(A_INCOME);
        assertThat(sum(a, 2)).isEqualByComparingTo(A_EXPENSE);

        List<Object[]> b = transactionsRepository.balanceHistory(START, END, ownerB);
        assertThat(sum(b, 1)).isEqualByComparingTo(B_INCOME);
        assertThat(sum(b, 2)).isEqualByComparingTo(B_EXPENSE);
    }

    @Test
    void balanceHistoryByAccount_idorReturnsEmpty() {
        List<Object[]> own = transactionsRepository.balanceHistoryByAccount(START, END, ownerA, accountA);
        assertThat(sum(own, 1)).isEqualByComparingTo(A_INCOME);

        List<Object[]> leak = transactionsRepository.balanceHistoryByAccount(START, END, ownerA, accountB);
        assertThat(leak).isEmpty();
    }

    // ---------- account ownership (used by services to block IDOR) ----------
    @Test
    void existsByAccountIdAndOwnerOwnerId_blocksCrossOwner() {
        assertThat(accountRepository.existsByAccountIdAndOwnerOwnerId(accountA, ownerA)).isTrue();
        assertThat(accountRepository.existsByAccountIdAndOwnerOwnerId(accountB, ownerB)).isTrue();
        // A does not own B's account and vice-versa.
        assertThat(accountRepository.existsByAccountIdAndOwnerOwnerId(accountB, ownerA)).isFalse();
        assertThat(accountRepository.existsByAccountIdAndOwnerOwnerId(accountA, ownerB)).isFalse();
    }

    @Test
    void findByOwner_returnsOnlyOwnersAccounts() {
        List<AccountEntity> a = accountRepository.findByOwnerOwnerIdOrderByAccountIdAsc(ownerA);
        assertThat(a).extracting(AccountEntity::getAccountId).containsExactly(accountA).doesNotContain(accountB);
        List<AccountEntity> b = accountRepository.findByOwnerOwnerIdOrderByAccountIdAsc(ownerB);
        assertThat(b).extracting(AccountEntity::getAccountId).containsExactly(accountB).doesNotContain(accountA);
    }

    private BigDecimal sum(List<Object[]> rows, int idx) {
        BigDecimal total = BigDecimal.ZERO;
        for (Object[] row : rows) {
            total = total.add((BigDecimal) row[idx]);
        }
        return total;
    }
}
