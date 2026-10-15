package com.pctheone.money_flow.services.telegram;

import com.pctheone.money_flow.dto.TelegramBotCommandDTO;
import com.pctheone.money_flow.dto.TelegramInlineButtonDTO;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import com.pctheone.money_flow.utils.Json;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

//---- Thin wrapper over the Telegram Bot HTTP API. Kept separate so the bot logic can be tested without network calls.
@Component
public class TelegramApiClient {

    private static final Logger log = LoggerFactory.getLogger(TelegramApiClient.class);

    @Value("${telegram.url}")
    private String telegramUrl;

    @Value("${telegram.bot-token}")
    private String telegramBotToken;

    @Value("${telegram.timeout}")
    private int telegramTimeout;

    private final ObjectMapper objectMapper = Json.MAPPER;

    private HttpClient httpClient;

    @PostConstruct
    private void init() {
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(telegramTimeout))
                .build();
    }

    public void sendMessage(Long chatId, String text) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("chat_id", chatId);
        body.put("text", text);
        post("sendMessage", body);
    }

    //---- Each inner list is one row of buttons.
    public void sendMessageWithInlineKeyboard(Long chatId, String text, List<List<TelegramInlineButtonDTO>> keyboard) {
        List<List<Map<String, String>>> rows = keyboard.stream()
                .map(row -> row.stream()
                        .map(TelegramApiClient::toButtonJson)
                        .toList())
                .toList();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("chat_id", chatId);
        body.put("text", text);
        body.put("reply_markup", Map.of("inline_keyboard", rows));
        post("sendMessage", body);
    }

    //---- Buttons are validated when built (TelegramInlineButtonDTO factories), so exactly one of callbackData/url is set.
    static Map<String, String> toButtonJson(TelegramInlineButtonDTO button) {
        Map<String, String> json = new LinkedHashMap<>();
        json.put("text", button.getText());
        if (button.getCallbackData() != null)
            json.put("callback_data", button.getCallbackData());
        if (button.getUrl() != null)
            json.put("url", button.getUrl());
        return json;
    }

    public void answerCallbackQuery(String callbackQueryId, String text) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("callback_query_id", callbackQueryId);
        if (text != null)
            body.put("text", text);
        post("answerCallbackQuery", body);
    }

    //---- languageCode null registers the default (fallback) command list.
    public boolean setMyCommands(List<TelegramBotCommandDTO> commands, String languageCode) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("commands", commands.stream()
                .map(command -> Map.of("command", command.getCommand(), "description", command.getDescription()))
                .toList());
        if (languageCode != null)
            body.put("language_code", languageCode);
        return post("setMyCommands", body);
    }

    public byte[] downloadFile(String fileId) throws IOException, InterruptedException {
        log.info("Requesting file metadata from Telegram API, fileId: {}", fileId);
        HttpRequest getFileRequest = HttpRequest.newBuilder()
                .uri(URI.create(telegramUrl + telegramBotToken + "/getFile" + "?file_id=" + fileId))
                .header("Content-Type", "application/json")
                .build();

        HttpResponse<String> getFileResponse = httpClient.send(getFileRequest, HttpResponse.BodyHandlers.ofString());
        JsonNode node = objectMapper.readTree(getFileResponse.body());
        if (node.get("result") == null || node.get("result").get("file_path") == null)
            throw new IOException("Telegram getFile failed: " + getFileResponse.statusCode() + " - " + getFileResponse.body());

        String filePath = node.get("result").get("file_path").asString();
        log.info("File metadata retrieved, filePath: {}", filePath);

        HttpRequest downloadFileRequest = HttpRequest.newBuilder()
                .uri(URI.create("https://api.telegram.org/file/bot" + telegramBotToken + "/" + filePath))
                .build();

        HttpResponse<byte[]> downloadFileResponse = httpClient.send(downloadFileRequest, HttpResponse.BodyHandlers.ofByteArray());
        log.info("Voice file downloaded, size: {} bytes", downloadFileResponse.body().length);
        return downloadFileResponse.body();
    }

    private boolean post(String method, Map<String, Object> body) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(telegramUrl + telegramBotToken + "/" + method))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                log.info("Telegram {} sent successfully", method);
                return true;
            }
            log.info("Telegram {} FAILED {} - {}", method, response.statusCode(), response.body());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while calling Telegram API {}", method, e);
            return false;
        } catch (Exception e) {
            log.error("Failed to call Telegram API {}: {}", method, e.getMessage(), e);
            return false;
        }
    }
}
