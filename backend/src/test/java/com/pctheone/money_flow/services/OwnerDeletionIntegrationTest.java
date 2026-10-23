package com.pctheone.money_flow.services;

import com.pctheone.money_flow.dto.AccountDTO;
import com.pctheone.money_flow.dto.OwnerDataExportDTO;
import com.pctheone.money_flow.entities.OwnerEntity;
import com.pctheone.money_flow.enums.CategoryTypeEnum;
import com.pctheone.money_flow.enums.CurrencyEnum;
import com.pctheone.money_flow.enums.LanguageEnum;
import com.pctheone.money_flow.enums.OnboardingStepEnum;
import com.pctheone.money_flow.exceptions.WrongPasswordException;
import com.pctheone.money_flow.repositories.OwnerRepository;
import com.pctheone.money_flow.services.telegram.BotMessages;
import com.pctheone.money_flow.services.telegram.TelegramApiClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

//---- Account deletion (#28) against a real database: owner A loses everything, owner B keeps everything.
@SpringBootTest(properties = {
        "telegram.bot-token=test-token",
        "telegram.webhook-secret=test-secret",
        "jwt.secret=tmpGFRC21ovxZhKhbd11YOC3Kw8YDyg5KcBb1dlMY0c=",
        "jwt.expiration=3600000"
})
public class OwnerDeletionIntegrationTest {

    private static final String PASSWORD = "coxinha123";

    //---- Every table holding owner data, in deletion order. The first three go by ON DELETE CASCADE from owner.
    private static final String[] OWNER_TABLES = {"refresh_token", "voice_usage", "password_reset_token",
            "transactions", "recurring_payment", "account", "category", "owner"};

    @MockitoBean
    TelegramApiClient telegramApiClient;

    @Autowired
    OwnerService ownerService;

    @Autowired
    AccountService accountService;

    @Autowired
    CategoryService categoryService;

    @Autowired
    TransactionsService transactionsService;

    @Autowired
    OwnerRepository ownerRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    BotMessages messages;

    Integer ownerA;
    Integer ownerB;
    long chatA = ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_000_000_000L);
    long chatB = chatA + 1;

    @BeforeEach
    void setUp() {
        ownerA = createOwnerWithData("a", chatA, "en");
        ownerB = createOwnerWithData("b", chatB, "pt");
    }

    @AfterEach
    void cleanUp() {
        for (Integer ownerId : new Integer[]{ownerA, ownerB})
            for (String table : OWNER_TABLES)
                jdbcTemplate.update("DELETE FROM " + table + " WHERE owner_id = ?", ownerId);
    }

    @Test
    void deletionRemovesEverythingOfTheOwnerAndNothingOfTheOthers() {
        Map<String, Integer> before = counts(ownerB);
        Assertions.assertTrue(counts(ownerA).values().stream().allMatch(count -> count > 0), "owner A starts with data in every table");

        ownerService.deleteOwner(ownerA, PASSWORD);

        counts(ownerA).forEach((table, count) -> Assertions.assertEquals(0, count, table + " of the deleted owner"));
        Assertions.assertTrue(ownerRepository.findById(ownerA).isEmpty());
        Assertions.assertEquals(before, counts(ownerB));
        Assertions.assertTrue(ownerRepository.findById(ownerB).isPresent());
    }

    @Test
    void deletionSendsFarewellToTheLinkedChatOnly() {
        ownerService.deleteOwner(ownerA, PASSWORD);

        Mockito.verify(telegramApiClient).sendMessage(chatA, messages.get(LanguageEnum.EN, "account.deletedFarewell"));
        Mockito.verify(telegramApiClient, Mockito.never()).sendMessage(ArgumentMatchers.eq(chatB), ArgumentMatchers.anyString());
    }

    @Test
    void wrongPasswordDeletesNothing() {
        Map<String, Integer> before = counts(ownerA);

        Assertions.assertThrows(WrongPasswordException.class, () -> ownerService.deleteOwner(ownerA, "wrong-password"));
        Assertions.assertThrows(WrongPasswordException.class, () -> ownerService.deleteOwner(ownerA, null));

        Assertions.assertEquals(before, counts(ownerA));
        Mockito.verifyNoInteractions(telegramApiClient);
    }

    @Test
    void exportContainsOnlyTheOwnersData() {
        OwnerDataExportDTO export = ownerService.exportData(ownerA);

        Assertions.assertEquals(chatA, export.getProfile().telegramChatId());
        Assertions.assertEquals(1, export.getAccounts().size());
        Assertions.assertEquals("Wallet a", export.getAccounts().getFirst().name());
        Assertions.assertEquals(1, export.getCategories().size());
        Assertions.assertEquals(2, export.getTransactions().size());
        Assertions.assertTrue(export.getTransactions().stream().allMatch(t -> "Wallet a".equals(t.account())));
        Assertions.assertEquals(1, export.getRecurringPayments().size());
    }

    //---- One account, one category, one recurring payment and two transactions (one of them recurring).
    private Integer createOwnerWithData(String prefix, long chatId, String language) {
        OwnerEntity owner = new OwnerEntity(null, prefix, prefix + "-" + UUID.randomUUID() + "@deletion.test", chatId,
                passwordEncoder.encode(PASSWORD), null, null, language, OnboardingStepEnum.DONE, null, null, null, null);
        Integer ownerId = ownerRepository.save(owner).getOwnerId();

        AccountDTO account = accountService.addAccount("Wallet " + prefix, new BigDecimal("100"), CurrencyEnum.BRL, ownerId);
        Integer categoryId = categoryService.addCategory("market", CategoryTypeEnum.EXPENSE, ownerId).getCategory().getCategoryId();
        transactionsService.registerExpense(ownerId, "market", account.getAccountId(), "10", "groceries");

        Integer recurringId = jdbcTemplate.queryForObject(
                "INSERT INTO recurring_payment (owner_id, account_id, category_id, description, is_active, recurring_start_date, amount, frequency) " +
                        "VALUES (?, ?, ?, 'rent', true, current_date, 50, 'MONTHLY') RETURNING recurring_payment_id",
                Integer.class, ownerId, account.getAccountId(), categoryId);
        jdbcTemplate.update(
                "INSERT INTO transactions (owner_id, account_id, category_id, recurring_payment_id, description, timestamp, amount, operation_type) " +
                        "VALUES (?, ?, ?, ?, 'rent', current_date, 50, 'EXPENSE')",
                ownerId, account.getAccountId(), categoryId, recurringId);

        //---- Session, voice quota and reset token rows (#24, #27, #26).
        jdbcTemplate.update("INSERT INTO refresh_token (owner_id, family_id, token_hash, expires_at, user_agent) " +
                "VALUES (?, gen_random_uuid(), ?, now() + interval '30 days', 'test')", ownerId, hash());
        jdbcTemplate.update("INSERT INTO voice_usage (owner_id, usage_date, count) VALUES (?, current_date, 3)", ownerId);
        jdbcTemplate.update("INSERT INTO password_reset_token (owner_id, token_hash, expires_at) " +
                "VALUES (?, ?, now() + interval '30 minutes')", ownerId, hash());
        return ownerId;
    }

    //---- 64 hex chars, unique per call (token_hash columns are CHAR(64) UNIQUE).
    private static String hash() {
        return (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "");
    }

    private Map<String, Integer> counts(Integer ownerId) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String table : OWNER_TABLES)
            counts.put(table, jdbcTemplate.queryForObject("SELECT count(*) FROM " + table + " WHERE owner_id = ?", Integer.class, ownerId));
        return counts;
    }
}
