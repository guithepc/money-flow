package com.pctheone.money_flow.services;

import com.pctheone.money_flow.entities.OwnerEntity;
import com.pctheone.money_flow.enums.CategoryTypeEnum;
import com.pctheone.money_flow.enums.LanguageEnum;
import com.pctheone.money_flow.enums.OnboardingStepEnum;
import com.pctheone.money_flow.repositories.OwnerRepository;
import com.pctheone.money_flow.services.telegram.TelegramApiClient;
import com.pctheone.money_flow.utils.LegalTerms;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

//---- Someone who signs up on the web (PWA) gets the same base categories as the bot onboarding, without Telegram.
@SpringBootTest(properties = {
        "telegram.bot-token=test-token",
        "telegram.webhook-secret=test-secret",
        "jwt.secret=tmpGFRC21ovxZhKhbd11YOC3Kw8YDyg5KcBb1dlMY0c=",
        "jwt.expiration=3600000"
})
public class WebSignUpCategoriesTest {

    private static final List<String> BASE_PT = List.of("Mercado:EXPENSE", "Restaurantes:EXPENSE", "Transporte:EXPENSE",
            "Casa:EXPENSE", "Salário:INCOME", "Renda extra:INCOME");

    @MockitoBean
    TelegramApiClient telegramApiClient;

    @Autowired
    OwnerService ownerService;

    @Autowired
    OwnerRepository ownerRepository;

    @Autowired
    CategoryService categoryService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    final List<Integer> owners = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (Integer owner : owners) {
            jdbcTemplate.update("DELETE FROM refresh_token WHERE owner_id = ?", owner);
            jdbcTemplate.update("DELETE FROM category WHERE owner_id = ?", owner);
            jdbcTemplate.update("DELETE FROM owner WHERE owner_id = ?", owner);
        }
    }

    @Test
    void webSignUpGetsTheBaseCategoriesInPortuguese() {
        Integer owner = ownerService.registerOwner("Web", email(), "Senha-forte-123", LegalTerms.CURRENT_VERSION).orElseThrow();
        owners.add(owner);

        Assertions.assertEquals(BASE_PT, categories(owner));
    }

    @Test
    void firstLoginOfAnOlderWebAccountSeedsOnceAndOnlyOnce() {
        Integer owner = existingOwner(null);

        ownerService.ensureBaseCategories(owner);
        ownerService.ensureBaseCategories(owner);

        Assertions.assertEquals(BASE_PT, categories(owner));
    }

    @Test
    void loginSeedsInTheLanguageChosenOnTheBot() {
        Integer owner = existingOwner("en");

        ownerService.ensureBaseCategories(owner);

        Assertions.assertEquals(List.of("Groceries:EXPENSE", "Eating out:EXPENSE", "Transport:EXPENSE", "Home:EXPENSE",
                "Salary:INCOME", "Side income:INCOME"), categories(owner));
    }

    @Test
    void ownerWhoAlreadyHasCategoriesGetsNothingNew() {
        Integer owner = existingOwner("pt");
        categoryService.addCategory("Pets", CategoryTypeEnum.EXPENSE, owner);

        ownerService.ensureBaseCategories(owner);

        Assertions.assertEquals(List.of("Pets:EXPENSE"), categories(owner));
    }

    //---- Web sign-up seeded Portuguese; picking English later on the bot must not add a second set.
    @Test
    void botLanguageStepDoesNotDuplicateWebCategories() {
        Integer owner = ownerService.registerOwner("Web", email(), "Senha-forte-123", LegalTerms.CURRENT_VERSION).orElseThrow();
        owners.add(owner);

        Assertions.assertFalse(categoryService.seedBaseCategoriesIfNone(owner, LanguageEnum.EN));
        Assertions.assertEquals(BASE_PT, categories(owner));
    }

    private Integer existingOwner(String language) {
        OwnerEntity owner = new OwnerEntity(null, "Old", email(), null, "x".repeat(60), null, null, language, OnboardingStepEnum.LANGUAGE, null, null, null, null);
        Integer id = ownerRepository.save(owner).getOwnerId();
        owners.add(id);
        return id;
    }

    private List<String> categories(Integer owner) {
        return jdbcTemplate.queryForList("SELECT description || ':' || type FROM category WHERE owner_id = ? ORDER BY category_id", String.class, owner);
    }

    private static String email() {
        return "web-" + UUID.randomUUID() + "@signup.test";
    }
}
