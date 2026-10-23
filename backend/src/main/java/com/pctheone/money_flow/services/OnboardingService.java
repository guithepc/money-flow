package com.pctheone.money_flow.services;

import com.pctheone.money_flow.dto.OnboardingAccountDraftDTO;
import com.pctheone.money_flow.entities.OwnerEntity;
import com.pctheone.money_flow.enums.LanguageEnum;
import com.pctheone.money_flow.enums.OnboardingStepEnum;
import com.pctheone.money_flow.exceptions.DuplicateAccountNameException;
import com.pctheone.money_flow.repositories.OwnerRepository;
import com.pctheone.money_flow.services.telegram.AccountMessageParser;
import com.pctheone.money_flow.services.telegram.BotMessages;
import com.pctheone.money_flow.services.telegram.TelegramReplySender;
import com.pctheone.money_flow.services.telegram.VoiceTranscriptionService;
import com.pctheone.money_flow.services.telegram.VoiceUsageService;
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
    VoiceUsageService voiceUsageService;

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

        if (snapshot.getOnboardingStep() == OnboardingStepEnum.ACCOUNT && !isCommand(input.text()) && input.voiceFileId() != null) {
            //---- Same daily voice quota as transactions, counted before the download.
            LanguageEnum language = OnboardingStateMachine.language(snapshot, input.telegramLanguageCode());
            VoiceUsageService.Quota quota = voiceUsageService.consume(ownerId);
            if (!quota.allowed()) {
                replySender.send(chatId, voiceUsageService.limitReachedMessage(language, true));
                return true;
            }
            try {
                String text = transcribe(input.voiceFileId());
                if (text == null) {
                    replySender.send(chatId, messages.get(language, "error.voice"));
                    return true;
                }
                return apply(ownerId, chatId, input, prepare(text, OnboardingDrafts.read(snapshot).getAwaiting()));
            } finally {
                voiceUsageService.lowQuotaNotice(quota, language).ifPresent(notice -> replySender.send(chatId, notice));
            }
        }

        OnboardingStateMachine.PreparedAccountInput prepared = null;
        if (snapshot.getOnboardingStep() == OnboardingStepEnum.ACCOUNT && !isCommand(input.text()))
            prepared = prepare(input.text(), OnboardingDrafts.read(snapshot).getAwaiting());
        return apply(ownerId, chatId, input, prepared);
    }

    private boolean apply(Integer ownerId, Long chatId, Input input, OnboardingStateMachine.PreparedAccountInput prepared) {
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
