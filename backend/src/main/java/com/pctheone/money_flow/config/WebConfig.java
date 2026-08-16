package com.pctheone.money_flow.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Libera CORS para o dashboard React (dev server do Vite) consumir os endpoints
 * de agregação. A origem permitida é configurável via app.cors.allowed-origins.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.cors.allowed-origins:http://localhost:5173}")
    private String[] allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/transactions/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET");
    }
}
