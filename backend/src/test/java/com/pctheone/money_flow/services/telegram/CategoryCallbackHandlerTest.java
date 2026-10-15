package com.pctheone.money_flow.services.telegram;

import com.pctheone.money_flow.dto.CategoryCreationResultDTO;
import com.pctheone.money_flow.entities.CategoryEntity;
import com.pctheone.money_flow.entities.OwnerEntity;
import com.pctheone.money_flow.enums.CategoryTypeEnum;
import com.pctheone.money_flow.enums.LanguageEnum;
import com.pctheone.money_flow.repositories.OwnerRepository;
import com.pctheone.money_flow.services.CategoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.User;

import java.util.Optional;

public class CategoryCallbackHandlerTest {

    private static final long CHAT_ID = 42L;
    private static final int OWNER_ID = 7;

    CategoryCallbackHandler handler;
    CategoryService categoryService;
    TelegramApiClient telegramApiClient;
    BotMessages messages = new BotMessages();

    @BeforeEach
    void setUp() {
        categoryService = Mockito.mock(CategoryService.class);
        telegramApiClient = Mockito.mock(TelegramApiClient.class);
        OwnerRepository ownerRepository = Mockito.mock(OwnerRepository.class);
        OwnerEntity owner = new OwnerEntity();
        owner.setOwnerId(OWNER_ID);
        owner.setLanguage("en");
        Mockito.when(ownerRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));

        handler = new CategoryCallbackHandler();
        handler.categoryService = categoryService;
        handler.ownerRepository = ownerRepository;
        handler.telegramApiClient = telegramApiClient;
        handler.messages = messages;
    }

    @Test
    void yesCreatesTheCategoryWithItsType() {
        Mockito.when(categoryService.addCategory("Pharmacy", CategoryTypeEnum.EXPENSE, OWNER_ID))
                .thenReturn(result("Pharmacy", CategoryTypeEnum.EXPENSE, true));

        handler.handle(context("new:E:Pharmacy"));

        Mockito.verify(telegramApiClient).sendMessage(CHAT_ID, messages.get(LanguageEnum.EN, "voice.categoryCreated", "Pharmacy"));
    }

    @Test
    void doubleClickSaysCreatedOnlyOnce() {
        Mockito.when(categoryService.addCategory("Pharmacy", CategoryTypeEnum.EXPENSE, OWNER_ID))
                .thenReturn(result("Pharmacy", CategoryTypeEnum.EXPENSE, true), result("Pharmacy", CategoryTypeEnum.EXPENSE, false));

        handler.handle(context("new:E:Pharmacy"));
        handler.handle(context("new:E:Pharmacy"));

        Mockito.verify(telegramApiClient, Mockito.times(1)).sendMessage(CHAT_ID, messages.get(LanguageEnum.EN, "voice.categoryCreated", "Pharmacy"));
        Mockito.verify(telegramApiClient).sendMessage(CHAT_ID, messages.get(LanguageEnum.EN, "voice.categoryAlreadyExists", "Pharmacy"));
    }

    @Test
    void nameTakenByTheOtherTypeIsExplained() {
        Mockito.when(categoryService.addCategory("Freela", CategoryTypeEnum.INCOME, OWNER_ID))
                .thenReturn(result("Freela", CategoryTypeEnum.EXPENSE, false));

        handler.handle(context("new:I:Freela"));

        Mockito.verify(telegramApiClient).sendMessage(CHAT_ID, "🚨 The name Freela is already used by an expense category. Pick another name.");
    }

    @Test
    void noCreatesNothing() {
        handler.handle(context("no"));

        Mockito.verify(categoryService, Mockito.never()).addCategory(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any());
        Mockito.verify(telegramApiClient).sendMessage(CHAT_ID, messages.get(LanguageEnum.EN, "voice.categoryNotCreated"));
    }

    private static CategoryCreationResultDTO result(String description, CategoryTypeEnum type, boolean created) {
        CategoryEntity category = new CategoryEntity();
        category.setDescription(description);
        category.setType(type);
        return new CategoryCreationResultDTO(category, created);
    }

    private TelegramCallbackHandler.Context context(String payload) {
        CallbackQuery query = new CallbackQuery();
        User from = new User();
        from.setLanguageCode("pt-br");
        query.setFrom(from);
        return new TelegramCallbackHandler.Context(query, CHAT_ID, OWNER_ID, payload);
    }
}
