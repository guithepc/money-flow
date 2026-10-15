package com.pctheone.money_flow.services.telegram;

import com.pctheone.money_flow.services.OnboardingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

//---- Onboarding buttons ("onb:<action>[:<argument>]"), handled by OnboardingService.
@Component
public class OnboardingCallbackHandler implements TelegramCallbackHandler {

    @Autowired
    OnboardingService onboardingService;

    @Override
    public String prefix() {
        return OnboardingService.CALLBACK_PREFIX;
    }

    @Override
    public String handle(Context context) {
        String languageCode = context.callbackQuery().getFrom() != null ? context.callbackQuery().getFrom().getLanguageCode() : null;
        onboardingService.handleCallback(context.ownerId(), context.chatId(), context.payload(), languageCode);
        return null;
    }
}
