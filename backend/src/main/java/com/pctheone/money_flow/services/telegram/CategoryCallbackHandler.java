package com.pctheone.money_flow.services.telegram;

import com.pctheone.money_flow.dto.CategoryCreationResultDTO;
import com.pctheone.money_flow.dto.TelegramInlineButtonDTO;
import com.pctheone.money_flow.entities.CategoryEntity;
import com.pctheone.money_flow.enums.CategoryTypeEnum;
import com.pctheone.money_flow.enums.LanguageEnum;
import com.pctheone.money_flow.repositories.OwnerRepository;
import com.pctheone.money_flow.services.CategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

//---- "Couldn't find 'Pharmacy'. Create it?" buttons sent by the voice parser.
//---- callback_data: "cat:new:E:<name>" / "cat:new:I:<name>" (expense / income) and "cat:no".
@Component
public class CategoryCallbackHandler implements TelegramCallbackHandler {

    public static final String PREFIX = "cat";

    @Autowired
    CategoryService categoryService;

    @Autowired
    OwnerRepository ownerRepository;

    @Autowired
    TelegramApiClient telegramApiClient;

    @Autowired
    BotMessages messages;

    @Override
    public String prefix() {
        return PREFIX;
    }

    @Override
    public String handle(Context context) {
        String languageCode = context.callbackQuery().getFrom() != null ? context.callbackQuery().getFrom().getLanguageCode() : null;
        LanguageEnum language = ownerRepository.findById(context.ownerId())
                .map(owner -> LanguageEnum.resolve(owner.getLanguage(), languageCode))
                .orElse(LanguageEnum.fromTelegram(languageCode));

        String[] parts = context.payload().split(":", 3);
        if (parts.length == 3 && parts[0].equals("new")) {
            CategoryTypeEnum type = parts[1].equals("I") ? CategoryTypeEnum.INCOME : CategoryTypeEnum.EXPENSE;
            String name = parts[2];
            CategoryCreationResultDTO result = categoryService.addCategory(name, type, context.ownerId());
            telegramApiClient.sendMessage(context.chatId(), reply(result, type, language));
        } else {
            telegramApiClient.sendMessage(context.chatId(), messages.get(language, "voice.categoryNotCreated"));
        }
        return null;
    }

    //---- Uses the real outcome: a double click (or a category created meanwhile) never says "created" twice.
    private String reply(CategoryCreationResultDTO result, CategoryTypeEnum requestedType, LanguageEnum language) {
        CategoryEntity category = result.getCategory();
        if (result.isCreated())
            return messages.get(language, "voice.categoryCreated", category.getDescription());
        if (category.getType() != requestedType)
            return messages.get(language, "category.nameTakenByOtherType", category.getDescription(),
                    messages.get(language, "category.type." + category.getType().name()));
        return messages.get(language, "voice.categoryAlreadyExists", category.getDescription());
    }

    //---- Empty when the name does not fit in Telegram's 64-byte callback_data.
    public static Optional<List<List<TelegramInlineButtonDTO>>> createButtons(String name, CategoryTypeEnum type, String yesText, String noText) {
        String data = PREFIX + ":new:" + (type == CategoryTypeEnum.INCOME ? "I" : "E") + ":" + name;
        if (data.getBytes(StandardCharsets.UTF_8).length > TelegramInlineButtonDTO.MAX_CALLBACK_DATA_BYTES)
            return Optional.empty();
        return Optional.of(List.of(List.of(
                TelegramInlineButtonDTO.callback(yesText, data),
                TelegramInlineButtonDTO.callback(noText, PREFIX + ":no"))));
    }
}
