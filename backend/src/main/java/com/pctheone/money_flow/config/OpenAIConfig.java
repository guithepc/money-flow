package com.pctheone.money_flow.config;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

@Configuration
public class OpenAIConfig {

    //---- Single shared client. Lazy so the app still boots without OPENAI_API_KEY (only voice messages need it).
    @Bean(destroyMethod = "close")
    @Lazy
    public OpenAIClient openAIClient() {
        return OpenAIOkHttpClient.fromEnv();
    }
}
