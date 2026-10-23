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
        //---- PUT edits a transaction, DELETE removes it; without them the browser's CORS preflight
        //---- on the edit dialog is rejected with 403 "Invalid CORS request".
        registry.addMapping("/api/transactions/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "PUT", "DELETE");
        //---- PUT renames an account, DELETE removes it (cascade). GET lists them.
        registry.addMapping("/api/accounts/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "PUT", "DELETE");
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
