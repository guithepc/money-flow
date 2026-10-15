package com.pctheone.money_flow.services.telegram;

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
        CategoryEntity created = new CategoryEntity();
        created.setDescription("Pharmacy");
        Mockito.when(categoryService.findByDescription("Pharmacy", OWNER_ID)).thenReturn(Optional.of(created));

        handler.handle(context("new:E:Pharmacy"));

        Mockito.verify(categoryService).addCategory("Pharmacy", CategoryTypeEnum.EXPENSE, OWNER_ID);
        Mockito.verify(telegramApiClient).sendMessage(CHAT_ID, messages.get(LanguageEnum.EN, "voice.categoryCreated", "Pharmacy"));
    }

    @Test
    void incomeTypeIsKept() {
        handler.handle(context("new:I:Freela"));

        Mockito.verify(categoryService).addCategory("Freela", CategoryTypeEnum.INCOME, OWNER_ID);
    }

    @Test
    void noCreatesNothing() {
        handler.handle(context("no"));

        Mockito.verify(categoryService, Mockito.never()).addCategory(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any());
        Mockito.verify(telegramApiClient).sendMessage(CHAT_ID, messages.get(LanguageEnum.EN, "voice.categoryNotCreated"));
    }

    private TelegramCallbackHandler.Context context(String payload) {
        CallbackQuery query = new CallbackQuery();
        User from = new User();
        from.setLanguageCode("pt-br");
        query.setFrom(from);
        return new TelegramCallbackHandler.Context(query, CHAT_ID, OWNER_ID, payload);
    }
}
