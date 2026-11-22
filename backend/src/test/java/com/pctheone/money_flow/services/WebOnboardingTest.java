package com.pctheone.money_flow.services;

import com.pctheone.money_flow.entities.OwnerEntity;
import com.pctheone.money_flow.enums.CategoryTypeEnum;
import com.pctheone.money_flow.enums.CurrencyEnum;
import com.pctheone.money_flow.enums.LanguageEnum;
import com.pctheone.money_flow.enums.OnboardingStepEnum;
import com.pctheone.money_flow.repositories.OwnerRepository;
import com.pctheone.money_flow.services.WebOnboardingService.Status;
import com.pctheone.money_flow.services.WebOnboardingService.Step;
import com.pctheone.money_flow.services.telegram.TelegramApiClient;
import com.pctheone.money_flow.utils.LegalTerms;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

//---- PWA onboarding against a real database, on the same owner state the bot uses.
@SpringBootTest(properties = {
        "telegram.bot-token=test-token",
        "telegram.webhook-secret=test-secret",
        "jwt.secret=tmpGFRC21ovxZhKhbd11YOC3Kw8YDyg5KcBb1dlMY0c=",
        "jwt.expiration=3600000"
})
public class WebOnboardingTest {

    @MockitoBean
    TelegramApiClient telegramApiClient;

    @Autowired
    WebOnboardingService onboarding;

    @Autowired
    OwnerService ownerService;

    @Autowired
    OwnerRepository ownerRepository;

    @Autowired
    AccountService accountService;

    @Autowired
    CategoryService categoryService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    final List<Integer> owners = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (Integer owner : owners) {
            jdbcTemplate.update("DELETE FROM transactions WHERE owner_id = ?", owner);
            jdbcTemplate.update("DELETE FROM account WHERE owner_id = ?", owner);
            jdbcTemplate.update("DELETE FROM owner_sequence WHERE owner_id = ?", owner);
            jdbcTemplate.update("DELETE FROM category WHERE owner_id = ?", owner);
            jdbcTemplate.update("DELETE FROM refresh_token WHERE owner_id = ?", owner);
            jdbcTemplate.update("DELETE FROM owner WHERE owner_id = ?", owner);
        }
    }

    @Test
    void webSignUpWalksLanguageAccountsAndFirstTransaction() {
        Integer owner = signUp();
        Status start = onboarding.status(owner);
        Assertions.assertEquals(Step.LANGUAGE, start.step());
        Assertions.assertTrue(start.categories().isEmpty(), "categories wait for the language choice");

        Status chosen = onboarding.chooseLanguage(owner, "en");
        Assertions.assertEquals(Step.ACCOUNTS, chosen.step());
        Assertions.assertEquals("en", chosen.language());
        Assertions.assertEquals(List.of("Groceries", "Eating out", "Transport", "Home", "Salary", "Side income"),
                chosen.categories().stream().map(WebOnboardingService.Category::name).toList());

        ResponseStatusException noAccount = Assertions.assertThrows(ResponseStatusException.class, () -> onboarding.accountsDone(owner));
        Assertions.assertEquals(HttpStatus.CONFLICT, noAccount.getStatusCode());

        accountService.addAccount("Revolut", new BigDecimal("300"), CurrencyEnum.EUR, owner);
        Status accounts = onboarding.accountsDone(owner);
        Assertions.assertEquals(Step.FIRST_TRANSACTION, accounts.step());
        Assertions.assertEquals(1, accounts.accounts());

        onboarding.transactionRegistered(owner);
        Assertions.assertEquals(Step.DONE, onboarding.status(owner).step());
    }

    @Test
    void choosingAgainOnlyChangesTheLanguageWithoutASecondCategorySet() {
        Integer owner = signUp();
        onboarding.chooseLanguage(owner, "pt");

        Status again = onboarding.chooseLanguage(owner, "en");

        Assertions.assertEquals(Step.ACCOUNTS, again.step());
        Assertions.assertEquals("en", again.language());
        Assertions.assertEquals(6, again.categories().size());
        Assertions.assertEquals("Mercado", again.categories().getFirst().name());
    }

    @Test
    void skippingTheFirstTransactionStillNeedsAnAccount() {
        Integer owner = signUp();
        onboarding.chooseLanguage(owner, "pt");
        Assertions.assertThrows(ResponseStatusException.class, () -> onboarding.finish(owner));

        accountService.addAccount("Nubank", new BigDecimal("100"), CurrencyEnum.BRL, owner);

        Assertions.assertEquals(Step.DONE, onboarding.finish(owner).step());
    }

    @Test
    void ownerMidwayOnTheBotResumesOnTheAccountsStep() {
        Integer owner = existingOwner(OnboardingStepEnum.MORE_ACCOUNTS, "pt");
        Assertions.assertEquals(Step.ACCOUNTS, onboarding.status(owner).step());
    }

    @Test
    void ownerDoneOnTheBotIsDoneOnThePwaAndARegistrationChangesNothing() {
        Integer owner = existingOwner(OnboardingStepEnum.DONE, "pt");
        onboarding.transactionRegistered(owner);
        Assertions.assertEquals(Step.DONE, onboarding.status(owner).step());
    }

    @Test
    void settingsChangeTheLanguageButNotTheStep() {
        Integer owner = existingOwner(OnboardingStepEnum.DONE, "pt");

        Status changed = onboarding.changeLanguage(owner, "en");

        Assertions.assertEquals("en", changed.language());
        Assertions.assertEquals(Step.DONE, changed.step());
        Assertions.assertThrows(ResponseStatusException.class, () -> onboarding.changeLanguage(owner, "fr"));
    }

    @Test
    void botLanguageStepDoesNotAddASecondSet() {
        Integer owner = signUp();
        onboarding.chooseLanguage(owner, "pt");

        Assertions.assertFalse(categoryService.seedBaseCategoriesIfNone(owner, LanguageEnum.EN));
        Assertions.assertEquals(6, categoryService.listAllCategories(owner).size());
        Assertions.assertTrue(categoryService.listAllCategories(owner).stream().allMatch(c -> c.getType() == CategoryTypeEnum.EXPENSE || c.getType() == CategoryTypeEnum.INCOME));
    }

    private Integer signUp() {
        Integer owner = ownerService.registerOwner("Web", email(), "Senha-forte-123", LegalTerms.CURRENT_VERSION).orElseThrow();
        owners.add(owner);
        return owner;
    }

    private Integer existingOwner(OnboardingStepEnum step, String language) {
        OwnerEntity owner = new OwnerEntity(null, "Bot", email(), null, "x".repeat(60), null, null, language, step, null, null, null, null);
        Integer id = ownerRepository.save(owner).getOwnerId();
        owners.add(id);
        return id;
    }

    private static String email() {
        return "onboarding-" + UUID.randomUUID() + "@web.test";
    }
}
