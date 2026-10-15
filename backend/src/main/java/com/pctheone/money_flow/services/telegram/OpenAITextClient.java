package com.pctheone.money_flow.services.telegram;

import com.openai.client.OpenAIClient;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseOutputItem;
import com.openai.models.responses.ResponseOutputMessage;
import com.openai.models.responses.ResponseOutputText;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.Optional;

//---- One GPT text call (instructions + user input -> text). Shared by the voice parser and the account parser.
@Service
public class OpenAITextClient {

    static final String MODEL = "gpt-4o-mini";

    @Lazy
    @Autowired
    OpenAIClient openAIClient;

    //---- Empty when the response has no text output.
    public Optional<String> complete(String instructions, String input) {
        ResponseCreateParams params = ResponseCreateParams.builder()
                .model(MODEL)
                .instructions(instructions)
                .input(input)
                .build();
        return firstOutputText(openAIClient.responses().create(params));
    }

    //---- Skips non-message items (reasoning, tool calls) and non-text content (refusals) instead of throwing.
    static Optional<String> firstOutputText(Response response) {
        return response.output().stream()
                .map(ResponseOutputItem::message)
                .flatMap(Optional::stream)
                .flatMap(message -> message.content().stream())
                .map(ResponseOutputMessage.Content::outputText)
                .flatMap(Optional::stream)
                .map(ResponseOutputText::text)
                .findFirst();
    }
}
