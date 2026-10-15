package com.pctheone.money_flow.utils;

import tools.jackson.databind.ObjectMapper;

//---- Single shared ObjectMapper (thread-safe once configured) for bot/GPT JSON and the onboarding draft.
public final class Json {

    private Json() {
    }

    public static final ObjectMapper MAPPER = new ObjectMapper();
}
