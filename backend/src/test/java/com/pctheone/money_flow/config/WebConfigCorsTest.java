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

    //---- The edit dialog issues PUT (edit) and DELETE (remove) on /api/transactions/{id}. Without them in
    //---- the CORS mapping the browser preflight is rejected (403 "Invalid CORS request") before the controller.
    @Test
    void transactionsMappingAllowsEditAndDelete() {
        CorsConfiguration transactions = corsConfigurations().get("/api/transactions/**");
        Assertions.assertNotNull(transactions, "No CORS mapping for /api/transactions/**");
        Assertions.assertNotNull(transactions.checkHttpMethod(HttpMethod.GET), "GET must stay allowed");
        Assertions.assertNotNull(transactions.checkHttpMethod(HttpMethod.PUT), "PUT must be allowed so editing works");
        Assertions.assertNotNull(transactions.checkHttpMethod(HttpMethod.DELETE), "DELETE must be allowed so deleting works");
    }

    //---- The accounts tab renames (PUT) and deletes (DELETE) accounts from the browser.
    @Test
    void accountsMappingAllowsRenameAndDelete() {
        CorsConfiguration accounts = corsConfigurations().get("/api/accounts/**");
        Assertions.assertNotNull(accounts, "No CORS mapping for /api/accounts/**");
        Assertions.assertNotNull(accounts.checkHttpMethod(HttpMethod.GET), "GET must stay allowed");
        Assertions.assertNotNull(accounts.checkHttpMethod(HttpMethod.PUT), "PUT must be allowed so renaming works");
        Assertions.assertNotNull(accounts.checkHttpMethod(HttpMethod.DELETE), "DELETE must be allowed so deleting works");
    }

    //---- The admin volumetry tab fetches /api/admin/metrics from the browser.
    @Test
    void adminMappingAllowsGet() {
        CorsConfiguration admin = corsConfigurations().get("/api/admin/**");
        Assertions.assertNotNull(admin, "No CORS mapping for /api/admin/**");
        Assertions.assertNotNull(admin.checkHttpMethod(HttpMethod.GET), "GET must be allowed for the metrics endpoint");
    }
}
