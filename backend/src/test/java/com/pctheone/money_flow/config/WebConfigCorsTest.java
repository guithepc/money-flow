package com.pctheone.money_flow.config;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import java.util.Map;

class WebConfigCorsTest {

    @SuppressWarnings("unchecked")
    private Map<String, CorsConfiguration> corsConfigurations() {
        WebConfig config = new WebConfig();
        ReflectionTestUtils.setField(config, "allowedOrigins", new String[]{"http://localhost:5173"});
        CorsRegistry registry = new CorsRegistry();
        config.addCorsMappings(registry);
        return (Map<String, CorsConfiguration>) ReflectionTestUtils.invokeMethod(registry, "getCorsConfigurations");
    }

    //---- The edit dialog issues PUT /api/transactions/{id}. Without PUT in the CORS mapping the browser
    //---- preflight is rejected (403 "Invalid CORS request") before reaching the controller.
    @Test
    void transactionsMappingAllowsPut() {
        CorsConfiguration transactions = corsConfigurations().get("/api/transactions/**");
        Assertions.assertNotNull(transactions, "No CORS mapping for /api/transactions/**");
        Assertions.assertNotNull(transactions.checkHttpMethod(HttpMethod.PUT), "PUT must be allowed so editing works");
        Assertions.assertNotNull(transactions.checkHttpMethod(HttpMethod.GET), "GET must stay allowed");
    }
}
