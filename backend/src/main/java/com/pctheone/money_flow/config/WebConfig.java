package com.pctheone.money_flow.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;


@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.cors.allowed-origins:http://localhost:5173}")
    private String[] allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/transactions/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET");
        registry.addMapping("/accounts/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET");
        registry.addMapping("/auth/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("POST");
    }
}
