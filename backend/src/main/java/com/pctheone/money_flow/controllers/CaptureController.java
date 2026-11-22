package com.pctheone.money_flow.controllers;

import com.pctheone.money_flow.dto.CaptureResponseDTO;
import com.pctheone.money_flow.dto.TransactionRegistrationResultDTO;
import com.pctheone.money_flow.services.TransactionCaptureService;
import com.pctheone.money_flow.services.WebOnboardingService;
import com.pctheone.money_flow.services.TransactionCaptureService.CategoryNotFound;
import com.pctheone.money_flow.services.TransactionCaptureService.CategoryTypeMismatch;
import com.pctheone.money_flow.services.TransactionCaptureService.NoAccount;
import com.pctheone.money_flow.services.TransactionCaptureService.NotUnderstood;
import com.pctheone.money_flow.services.TransactionCaptureService.Outcome;
import com.pctheone.money_flow.services.TransactionCaptureService.Registered;
import com.pctheone.money_flow.services.telegram.VoiceTranscriptionService;
import com.pctheone.money_flow.services.telegram.VoiceUsageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

//---- The PWA's way in: a typed sentence or a recorded voice note becomes a transaction, same parser and daily
//---- limits as the Telegram bot.
@RestController
@RequestMapping("/api/capture")
public class CaptureController {

    private static final Logger log = LoggerFactory.getLogger(CaptureController.class);

    static final int MAX_TEXT_LENGTH = 300;

    //---- MediaRecorder output: Safari on iPhone records audio/mp4, Chrome and Firefox audio/webm.
    static final Map<String, String> AUDIO_EXTENSIONS = Map.of(
            "audio/mp4", ".mp4", "audio/x-m4a", ".m4a", "audio/m4a", ".m4a", "audio/aac", ".m4a",
            "audio/webm", ".webm", "audio/ogg", ".ogg", "audio/mpeg", ".mp3", "audio/wav", ".wav", "audio/x-wav", ".wav");

    public record TextCaptureRequest(String text) {}

    @Autowired
    TransactionCaptureService transactionCaptureService;

    @Autowired
    VoiceTranscriptionService voiceTranscriptionService;

    @Autowired
    VoiceUsageService voiceUsageService;

    @Autowired
    WebOnboardingService webOnboardingService;

    @PostMapping("/text")
    public ResponseEntity<CaptureResponseDTO> captureText(@RequestBody TextCaptureRequest request) {
        Integer ownerId = ownerId();
        String text = request == null || request.text() == null ? "" : request.text().trim();
        if (text.isEmpty() || text.length() > MAX_TEXT_LENGTH)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Text must have between 1 and " + MAX_TEXT_LENGTH + " characters");
        log.info("Text capture received from owner {} ({} chars).", ownerId, text.length());

        VoiceUsageService.Quota quota = voiceUsageService.consumeText(ownerId);
        if (!quota.allowed())
            return limitReached();
        try {
            return ResponseEntity.ok(toResponse(registered(transactionCaptureService.capture(text, ownerId), ownerId), null, quota.remaining()));
        } catch (IOException e) {
            throw parserUnavailable(e);
        }
    }

    @PostMapping("/voice")
    public ResponseEntity<CaptureResponseDTO> captureVoice(@RequestParam("audio") MultipartFile audio) throws IOException {
        Integer ownerId = ownerId();
        if (audio.isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Audio is empty");
        String extension = extensionOf(audio.getContentType())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported audio format"));
        log.info("Voice capture received from owner {} ({} bytes, {}).", ownerId, audio.getSize(), extension);

        //---- Counted before transcribing: over the daily limit, OpenAI is never called.
        VoiceUsageService.Quota quota = voiceUsageService.consume(ownerId);
        if (!quota.allowed())
            return limitReached();
        try {
            String transcription = voiceTranscriptionService.transcribe(audio.getBytes(), extension);
            if (transcription == null || transcription.isBlank())
                return ResponseEntity.ok(new CaptureResponseDTO("NOT_UNDERSTOOD", "", null, null, quota.remaining()));
            return ResponseEntity.ok(toResponse(registered(transactionCaptureService.capture(transcription, ownerId), ownerId), transcription, quota.remaining()));
        } catch (IOException e) {
            throw parserUnavailable(e);
        }
    }

    //---- The first transaction registered during onboarding finishes it.
    private Outcome registered(Outcome outcome, Integer ownerId) {
        if (outcome instanceof Registered)
            webOnboardingService.transactionRegistered(ownerId);
        return outcome;
    }

    //---- "audio/webm;codecs=opus" -> ".webm"
    static Optional<String> extensionOf(String contentType) {
        if (contentType == null)
            return Optional.empty();
        return Optional.ofNullable(AUDIO_EXTENSIONS.get(contentType.split(";")[0].trim().toLowerCase()));
    }

    static CaptureResponseDTO toResponse(Outcome outcome, String transcription, int remainingToday) {
        return switch (outcome) {
            case Registered registered -> {
                TransactionRegistrationResultDTO result = registered.result();
                yield new CaptureResponseDTO("REGISTERED", transcription, new CaptureResponseDTO.Transaction(result.getTransactionId(),
                        registered.operationType(), result.getAmount(), result.getCurrency(), result.getAccountDescription(),
                        result.getCategoryDescription(), result.getNewBalance()), null, remainingToday);
            }
            case CategoryNotFound notFound -> new CaptureResponseDTO("CATEGORY_NOT_FOUND", transcription, null,
                    new CaptureResponseDTO.Category(notFound.suggestedName(), notFound.type()), remainingToday);
            case CategoryTypeMismatch mismatch -> new CaptureResponseDTO("CATEGORY_TYPE_MISMATCH", transcription, null,
                    new CaptureResponseDTO.Category(mismatch.category(), mismatch.categoryType()), remainingToday);
            case NoAccount ignored -> new CaptureResponseDTO("NO_ACCOUNT", transcription, null, null, remainingToday);
            case NotUnderstood ignored -> new CaptureResponseDTO("NOT_UNDERSTOOD", transcription, null, null, remainingToday);
        };
    }

    private static ResponseEntity<CaptureResponseDTO> limitReached() {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(new CaptureResponseDTO("LIMIT_REACHED", null, null, null, 0));
    }

    private static ResponseStatusException parserUnavailable(IOException e) {
        log.error("Capture failed while calling OpenAI: {}", e.getMessage());
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not process the message right now");
    }

    private static Integer ownerId() {
        return (Integer) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
