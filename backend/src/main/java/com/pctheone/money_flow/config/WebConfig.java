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
        registry.addMapping("/api/transactions/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET");
        registry.addMapping("/api/accounts/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET");
        //---- Credentials so the HttpOnly refresh cookie travels on cross-origin dev setups (localhost:5173 -> :8080).
        registry.addMapping("/api/auth/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("POST")
                .allowCredentials(true);
        //---- GET /me and /export, POST invite and terms acceptance, DELETE for account deletion.
        registry.addMapping("/api/owner/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "DELETE");
    }
}
