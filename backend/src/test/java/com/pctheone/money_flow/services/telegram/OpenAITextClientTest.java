package com.pctheone.money_flow.services.telegram;

import com.openai.core.ObjectMappers;
import com.openai.models.responses.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Optional;

public class OpenAITextClientTest {

    @Test
    void skipsNonMessageItemsAndNonTextContent() throws Exception {
        Response response = response("""
                [
                  {"type": "reasoning", "id": "rs_1", "summary": []},
                  {"type": "message", "id": "msg_1", "role": "assistant", "status": "completed",
                   "content": [
                     {"type": "refusal", "refusal": "no"},
                     {"type": "output_text", "text": "{\\"ok\\": true}", "annotations": []}
                   ]}
                ]
                """);

        Assertions.assertEquals(Optional.of("{\"ok\": true}"), OpenAITextClient.firstOutputText(response));
    }

    @Test
    void emptyWhenThereIsNoText() throws Exception {
        Assertions.assertEquals(Optional.empty(), OpenAITextClient.firstOutputText(response("""
                [{"type": "reasoning", "id": "rs_1", "summary": []}]
                """)));
    }

    private static Response response(String output) throws Exception {
        String json = """
                {"id": "resp_1", "object": "response", "created_at": 0, "model": "gpt-4o-mini",
                 "output": %s, "parallel_tool_calls": false, "tool_choice": "auto", "tools": [],
                 "error": null, "incomplete_details": null, "instructions": null, "metadata": null,
                 "temperature": null, "top_p": null}
                """.formatted(output);
        return ObjectMappers.jsonMapper().readValue(json, Response.class);
    }
}
