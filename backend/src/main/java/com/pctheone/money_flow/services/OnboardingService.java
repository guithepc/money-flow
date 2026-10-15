package com.pctheone.money_flow.services;

import com.pctheone.money_flow.dto.OnboardingAccountDraftDTO;
import com.pctheone.money_flow.entities.OwnerEntity;
import com.pctheone.money_flow.enums.OnboardingStepEnum;
import com.pctheone.money_flow.exceptions.DuplicateAccountNameException;
import com.pctheone.money_flow.repositories.OwnerRepository;
import com.pctheone.money_flow.services.telegram.AccountMessageParser;
import com.pctheone.money_flow.services.telegram.BotMessages;
import com.pctheone.money_flow.services.telegram.TelegramReplySender;
import com.pctheone.money_flow.services.telegram.VoiceTranscriptionService;
import com.pctheone.money_flow.utils.AmountParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

//---- Entry point of the guided onboarding (router + inline buttons). Not transactional on purpose:
//---- the slow part (Telegram download, Whisper, GPT parse) runs here without any lock, then
//---- OnboardingStateMachine applies the result in a short transaction under the owner row lock.
@Service
public class OnboardingService {

    private static final Logger log = LoggerFactory.getLogger(OnboardingService.class);

    public static final String CALLBACK_PREFIX = OnboardingStateMachine.CALLBACK_PREFIX;

    //---- What the user sent. Exactly one of text / voiceFileId is usually set.
    public record Input(String text, String voiceFileId, String telegramLanguageCode) {
    }

    @Autowired
    OnboardingStateMachine stateMachine;

    @Autowired
    OwnerRepository ownerRepository;

    @Autowired
    AccountMessageParser accountMessageParser;

    @Autowired
    VoiceTranscriptionService voiceTranscriptionService;

    @Autowired
    TelegramReplySender replySender;

    @Autowired
    BotMessages messages;

    public void resume(Integer ownerId, Long chatId, String telegramLanguageCode) {
        stateMachine.resume(ownerId, chatId, telegramLanguageCode);
    }

    //---- Returns false when the message is not for onboarding and must go through the normal router.
    public boolean handleMessage(Integer ownerId, Long chatId, Input input) {
        //---- Unlocked snapshot, only to know what to prepare. The state machine re-checks it under the lock.
        OwnerEntity snapshot = ownerRepository.findById(ownerId)
                .orElseThrow(() -> new IllegalStateException("Owner not found: " + ownerId));
        if (snapshot.getOnboardingStep() == OnboardingStepEnum.DONE)
            return false;

        OnboardingStateMachine.PreparedAccountInput prepared = null;
        if (snapshot.getOnboardingStep() == OnboardingStepEnum.ACCOUNT && !isCommand(input.text())) {
            String text = input.text();
            if (input.voiceFileId() != null) {
                text = transcribe(input.voiceFileId());
                if (text == null) {
                    replySender.send(chatId, messages.get(OnboardingStateMachine.language(snapshot, input.telegramLanguageCode()), "error.voice"));
                    return true;
                }
            }
            prepared = prepare(text, OnboardingDrafts.read(snapshot).getAwaiting());
        }

        try {
            return stateMachine.handleMessage(ownerId, chatId, input, prepared);
        } catch (DuplicateAccountNameException e) {
            stateMachine.rejectDuplicateName(ownerId, chatId, input.telegramLanguageCode(), e.getName());
            return true;
        }
    }

    public void handleCallback(Integer ownerId, Long chatId, String payload, String telegramLanguageCode) {
        try {
            stateMachine.handleCallback(ownerId, chatId, payload, telegramLanguageCode);
        } catch (DuplicateAccountNameException e) {
            stateMachine.rejectDuplicateName(ownerId, chatId, telegramLanguageCode, e.getName());
        }
    }

    public void completeTutorial(Integer ownerId, Long chatId, String telegramLanguageCode) {
        stateMachine.completeTutorial(ownerId, chatId, telegramLanguageCode);
    }

    //---- GPT is only called when the field being asked needs it: the full message, or a name answer that may carry
    //---- balance/currency too. A balance answer is parsed deterministically first; the currency never needs GPT.
    private OnboardingStateMachine.PreparedAccountInput prepare(String text, OnboardingAccountDraftDTO.Field awaiting) {
        AccountMessageParser.ParsedAccount parsed = null;
        if (text != null && !text.isBlank()) {
            if (awaiting == null || awaiting == OnboardingAccountDraftDTO.Field.NAME)
                parsed = accountMessageParser.parse(text);
            else if (awaiting == OnboardingAccountDraftDTO.Field.BALANCE && AmountParser.parse(text).isEmpty())
                parsed = accountMessageParser.parse(text);
        }
        return new OnboardingStateMachine.PreparedAccountInput(awaiting, text, parsed);
    }

    private String transcribe(String fileId) {
        try {
            return voiceTranscriptionService.transcribe(fileId);
        } catch (Exception e) {
            if (e instanceof InterruptedException)
                Thread.currentThread().interrupt();
            log.error("Failed to transcribe onboarding voice message: {}", e.getMessage(), e);
            return null;
        }
    }

    static boolean isCommand(String text) {
        return text != null && text.trim().startsWith("/");
    }
}
