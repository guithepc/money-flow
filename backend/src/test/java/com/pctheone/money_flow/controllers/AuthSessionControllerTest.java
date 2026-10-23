package com.pctheone.money_flow.controllers;

import com.pctheone.money_flow.services.AuthService;
import com.pctheone.money_flow.services.telegram.TelegramApiClient;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Date;
import java.util.HexFormat;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

//---- HTTP contract of the persistent session (#24): login cookie, refresh rotation, logout, expired access token,
//---- and the password reset (#26) ending the refresh sessions too.
@SpringBootTest(properties = {
        "telegram.bot-token=test-token",
        "telegram.webhook-secret=test-secret",
        "jwt.secret=" + AuthSessionControllerTest.JWT_SECRET,
        "jwt.expiration=3600000",
        "app.cors.allowed-origins=http://localhost:5173"
})
@AutoConfigureMockMvc
public class AuthSessionControllerTest {

    static final String JWT_SECRET = "tmpGFRC21ovxZhKhbd11YOC3Kw8YDyg5KcBb1dlMY0c=";
    static final String PASSWORD = "coxinha123";

    @MockitoBean
    TelegramApiClient telegramApiClient;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AuthService authService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    String email;
    Integer ownerId;

    @BeforeEach
    void setUp() {
        email = "auth-" + UUID.randomUUID() + "@test.local";
        ownerId = authService.register("auth", email, PASSWORD).orElseThrow();
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM owner WHERE owner_id = ?", ownerId);
    }

    private MvcResult login() throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();
    }

    private static Cookie refreshCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie(AuthController.REFRESH_COOKIE);
        Assertions.assertNotNull(cookie);
        return new Cookie(cookie.getName(), cookie.getValue());
    }

    @Test
    void loginSetsHttpOnlyStrictRefreshCookie() throws Exception {
        MvcResult result = login();

        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        Assertions.assertNotNull(setCookie);
        Assertions.assertTrue(setCookie.startsWith(AuthController.REFRESH_COOKIE + "="));
        Assertions.assertTrue(setCookie.contains("HttpOnly"));
        Assertions.assertTrue(setCookie.contains("Secure"));
        Assertions.assertTrue(setCookie.contains("SameSite=Strict"));
        Assertions.assertTrue(setCookie.contains("Path=/api/auth"));
        Assertions.assertTrue(setCookie.contains("Max-Age=2592000"));
    }

    @Test
    void refreshWithValidCookieReturnsTokenAndRotatesCookie() throws Exception {
        Cookie first = refreshCookie(login());

        MvcResult refreshed = mockMvc.perform(post("/api/auth/refresh").cookie(first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();
        Cookie second = refreshCookie(refreshed);
        Assertions.assertNotEquals(first.getValue(), second.getValue());

        //---- The new access token opens the protected endpoints.
        String accessToken = com.jayway.jsonpath.JsonPath.read(refreshed.getResponse().getContentAsString(), "$.token");
        mockMvc.perform(get("/api/owner/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    @Test
    void refreshWithoutCookieIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshWithExpiredCookieIsUnauthorizedAndClearsIt() throws Exception {
        Cookie cookie = refreshCookie(login());
        jdbcTemplate.update("UPDATE refresh_token SET expires_at = now() - interval '1 minute' WHERE owner_id = ?", ownerId);

        MvcResult result = mockMvc.perform(post("/api/auth/refresh").cookie(cookie))
                .andExpect(status().isUnauthorized())
                .andReturn();
        Assertions.assertTrue(result.getResponse().getHeader(HttpHeaders.SET_COOKIE).contains("Max-Age=0"));
    }

    @Test
    void reusingRotatedCookieRevokesTheFamily() throws Exception {
        Cookie first = refreshCookie(login());
        Cookie second = refreshCookie(mockMvc.perform(post("/api/auth/refresh").cookie(first))
                .andExpect(status().isOk()).andReturn());

        mockMvc.perform(post("/api/auth/refresh").cookie(first))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/refresh").cookie(second))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesTheSession() throws Exception {
        Cookie cookie = refreshCookie(login());

        MvcResult result = mockMvc.perform(post("/api/auth/logout").cookie(cookie))
                .andExpect(status().isNoContent())
                .andReturn();
        Assertions.assertTrue(result.getResponse().getHeader(HttpHeaders.SET_COOKIE).contains("Max-Age=0"));

        mockMvc.perform(post("/api/auth/refresh").cookie(cookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void passwordResetRevokesEveryRefreshSession() throws Exception {
        Cookie first = refreshCookie(login());
        Cookie second = refreshCookie(login());

        String resetToken = "reset-" + UUID.randomUUID();
        String resetHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(resetToken.getBytes(StandardCharsets.UTF_8)));
        jdbcTemplate.update("INSERT INTO password_reset_token (owner_id, token_hash, expires_at) VALUES (?, ?, now() + interval '30 minutes')",
                ownerId, resetHash);
        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + resetToken + "\",\"newPassword\":\"new-password-123\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/refresh").cookie(first))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/refresh").cookie(second))
                .andExpect(status().isUnauthorized());
        Assertions.assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM refresh_token WHERE owner_id = ? AND revoked_at IS NULL", Integer.class, ownerId));
    }

    @Test
    void refreshFromUntrustedOriginIsForbidden() throws Exception {
        Cookie cookie = refreshCookie(login());

        mockMvc.perform(post("/api/auth/refresh").cookie(cookie).header(HttpHeaders.ORIGIN, "https://evil.pctheone.com"))
                .andExpect(status().isForbidden());
        //---- The cookie was not consumed by the refused call.
        mockMvc.perform(post("/api/auth/refresh").cookie(cookie).header(HttpHeaders.ORIGIN, "http://localhost:5173"))
                .andExpect(status().isOk());
    }

    @Test
    void expiredAccessTokenIsUnauthorized() throws Exception {
        long past = System.currentTimeMillis() - 60_000;
        String expired = Jwts.builder()
                .subject(ownerId.toString())
                .issuedAt(new Date(past - 60_000))
                .expiration(new Date(past))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(JWT_SECRET)))
                .compact();

        mockMvc.perform(get("/api/owner/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }
}
