package com.pctheone.money_flow.controllers;

import com.pctheone.money_flow.dto.TransactionRegistrationResultDTO;
import com.pctheone.money_flow.enums.CategoryTypeEnum;
import com.pctheone.money_flow.enums.CurrencyEnum;
import com.pctheone.money_flow.enums.OperationTypeEnum;
import com.pctheone.money_flow.services.TransactionCaptureService;
import com.pctheone.money_flow.services.telegram.VoiceTranscriptionService;
import com.pctheone.money_flow.services.telegram.VoiceUsageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class CaptureControllerTest {

    private static final int OWNER_ID = 7;
    private static final byte[] AUDIO = {1, 2, 3};

    TransactionCaptureService captureService;
    VoiceTranscriptionService transcriptionService;
    VoiceUsageService usageService;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        captureService = Mockito.mock(TransactionCaptureService.class);
        transcriptionService = Mockito.mock(VoiceTranscriptionService.class);
        usageService = Mockito.mock(VoiceUsageService.class);
        Mockito.when(usageService.consumeText(OWNER_ID)).thenReturn(new VoiceUsageService.Quota(1, 100));
        Mockito.when(usageService.consume(OWNER_ID)).thenReturn(new VoiceUsageService.Quota(3, 20));

        CaptureController controller = new CaptureController();
        controller.transactionCaptureService = captureService;
        controller.voiceTranscriptionService = transcriptionService;
        controller.voiceUsageService = usageService;
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(OWNER_ID, null, List.of()));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void typedSentenceIsRegisteredWithIdForUndo() throws Exception {
        Mockito.when(captureService.capture("gastei 25 no supermercado", OWNER_ID)).thenReturn(registered());

        mockMvc.perform(post("/api/capture/text").contentType(MediaType.APPLICATION_JSON).content("{\"text\": \" gastei 25 no supermercado \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REGISTERED"))
                .andExpect(jsonPath("$.transaction.id").value(55))
                .andExpect(jsonPath("$.transaction.operationType").value("EXPENSE"))
                .andExpect(jsonPath("$.transaction.amount").value(25))
                .andExpect(jsonPath("$.transaction.account").value("Inter"))
                .andExpect(jsonPath("$.transaction.category").value("Mercado"))
                .andExpect(jsonPath("$.transaction.newBalance").value(975))
                .andExpect(jsonPath("$.remainingToday").value(99));
    }

    @Test
    void blankOrTooLongTextIsRejectedWithoutSpendingQuota() throws Exception {
        mockMvc.perform(post("/api/capture/text").contentType(MediaType.APPLICATION_JSON).content("{\"text\": \"  \"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/capture/text").contentType(MediaType.APPLICATION_JSON).content("{\"text\": \"" + "a".repeat(301) + "\"}"))
                .andExpect(status().isBadRequest());

        Mockito.verifyNoInteractions(usageService, captureService);
    }

    @Test
    void textOverTheDailyLimitNeverCallsOpenAI() throws Exception {
        Mockito.when(usageService.consumeText(OWNER_ID)).thenReturn(new VoiceUsageService.Quota(101, 100));

        mockMvc.perform(post("/api/capture/text").contentType(MediaType.APPLICATION_JSON).content("{\"text\": \"gastei 25\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value("LIMIT_REACHED"));

        Mockito.verifyNoInteractions(captureService);
    }

    @Test
    void unknownCategoryComesBackAsASuggestion() throws Exception {
        Mockito.when(captureService.capture("comprei dipirona 12", OWNER_ID))
                .thenReturn(new TransactionCaptureService.CategoryNotFound("Saúde", CategoryTypeEnum.EXPENSE));

        mockMvc.perform(post("/api/capture/text").contentType(MediaType.APPLICATION_JSON).content("{\"text\": \"comprei dipirona 12\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CATEGORY_NOT_FOUND"))
                .andExpect(jsonPath("$.category.name").value("Saúde"))
                .andExpect(jsonPath("$.category.type").value("EXPENSE"));
    }

    @Test
    void iphoneVoiceNoteIsTranscribedAsMp4AndRegistered() throws Exception {
        Mockito.when(transcriptionService.transcribe(AUDIO, ".mp4")).thenReturn("gastei 25 no supermercado");
        Mockito.when(captureService.capture("gastei 25 no supermercado", OWNER_ID)).thenReturn(registered());

        mockMvc.perform(multipart("/api/capture/voice").file(new MockMultipartFile("audio", "note.mp4", "audio/mp4", AUDIO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REGISTERED"))
                .andExpect(jsonPath("$.transcription").value("gastei 25 no supermercado"))
                .andExpect(jsonPath("$.remainingToday").value(17));
    }

    @Test
    void chromeWebmWithCodecIsAccepted() throws Exception {
        Mockito.when(transcriptionService.transcribe(AUDIO, ".webm")).thenReturn("oi");
        Mockito.when(captureService.capture("oi", OWNER_ID)).thenReturn(new TransactionCaptureService.NotUnderstood());

        mockMvc.perform(multipart("/api/capture/voice").file(new MockMultipartFile("audio", "note.webm", "audio/webm;codecs=opus", AUDIO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NOT_UNDERSTOOD"));
    }

    @Test
    void unsupportedFormatIsRejectedBeforeSpendingQuota() throws Exception {
        mockMvc.perform(multipart("/api/capture/voice").file(new MockMultipartFile("audio", "x.png", "image/png", AUDIO)))
                .andExpect(status().isUnsupportedMediaType());

        Mockito.verifyNoInteractions(usageService, transcriptionService);
    }

    @Test
    void voiceOverTheDailyLimitIsNeverTranscribed() throws Exception {
        Mockito.when(usageService.consume(OWNER_ID)).thenReturn(new VoiceUsageService.Quota(21, 20));

        mockMvc.perform(multipart("/api/capture/voice").file(new MockMultipartFile("audio", "note.mp4", "audio/mp4", AUDIO)))
                .andExpect(status().isTooManyRequests());

        Mockito.verifyNoInteractions(transcriptionService, captureService);
    }

    @Test
    void openAIFailureIsABadGateway() throws Exception {
        Mockito.when(transcriptionService.transcribe(ArgumentMatchers.any(), ArgumentMatchers.anyString())).thenThrow(new IOException("timeout"));

        mockMvc.perform(multipart("/api/capture/voice").file(new MockMultipartFile("audio", "note.mp4", "audio/mp4", AUDIO)))
                .andExpect(status().isBadGateway());
    }

    @Test
    void extensionFollowsTheContentType() {
        Assertions.assertEquals(".mp4", CaptureController.extensionOf("audio/mp4").orElseThrow());
        Assertions.assertEquals(".webm", CaptureController.extensionOf("Audio/WebM; codecs=opus").orElseThrow());
        Assertions.assertTrue(CaptureController.extensionOf(null).isEmpty());
        Assertions.assertTrue(CaptureController.extensionOf("video/mp4").isEmpty());
    }

    private static TransactionCaptureService.Registered registered() {
        return new TransactionCaptureService.Registered(OperationTypeEnum.EXPENSE,
                new TransactionRegistrationResultDTO(new BigDecimal("25"), new BigDecimal("975"), CurrencyEnum.BRL, "Inter", "Mercado", 55));
    }
}
