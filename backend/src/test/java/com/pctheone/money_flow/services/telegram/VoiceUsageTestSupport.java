package com.pctheone.money_flow.services.telegram;

import org.mockito.Mockito;

//---- Real VoiceUsageService (messages, notice rule) without a database: tests stub consume() on the spy.
public final class VoiceUsageTestSupport {

    private VoiceUsageTestSupport() {
    }

    public static VoiceUsageService spyWithLimit(BotMessages messages, int dailyLimit) {
        VoiceUsageService service = new VoiceUsageService();
        service.messages = messages;
        service.dailyLimit = dailyLimit;
        return Mockito.spy(service);
    }
}
