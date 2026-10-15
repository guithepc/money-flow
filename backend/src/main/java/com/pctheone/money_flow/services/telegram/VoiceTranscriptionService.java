package com.pctheone.money_flow.services.telegram;

import com.openai.client.OpenAIClient;
import com.openai.models.audio.transcriptions.TranscriptionCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

//---- Downloads a Telegram voice message and transcribes it with Whisper. Shared by the voice parser and onboarding.
@Service
public class VoiceTranscriptionService {

    private static final Logger log = LoggerFactory.getLogger(VoiceTranscriptionService.class);

    @Autowired
    TelegramApiClient telegramApiClient;

    @Lazy
    @Autowired
    OpenAIClient openAIClient;

    public String transcribe(String fileId) throws IOException, InterruptedException {
        byte[] audio = telegramApiClient.downloadFile(fileId);

        Path tempFile = Files.createTempFile("voice_", ".ogg");
        try {
            Files.write(tempFile, audio);

            log.info("Sending audio to Whisper for transcription");
            var result = openAIClient.audio().transcriptions().create(
                    TranscriptionCreateParams.builder()
                            .file(tempFile)
                            .model("gpt-transcribe")
                            .build());

            String transcription = result.asTranscription().text();
            log.info("Whisper transcription result: {}", transcription);
            return transcription;
        } finally {
            deleteTempFile(tempFile);
        }
    }

    private void deleteTempFile(Path tempFile){
        try {
            Files.deleteIfExists(tempFile);
        } catch (IOException e) {
            log.error("Failed to delete temp voice file {}: {}", tempFile, e.getMessage());
        }
    }
}
