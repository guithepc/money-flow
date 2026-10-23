package com.pctheone.money_flow.controllers;

import com.pctheone.money_flow.services.AuthService;
import com.pctheone.money_flow.services.PasswordResetTestFixture;
import com.pctheone.money_flow.services.OwnerService;
import com.pctheone.money_flow.services.RefreshTokenService;
import com.pctheone.money_flow.utils.PasswordPolicy;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;
import java.util.Optional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class AuthControllerTest {

    private static final String REQUEST_URL = "/api/auth/password-reset/request";
    private static final String CONFIRM_URL = "/api/auth/password-reset/confirm";
    private static final String REGISTER_URL = "/api/auth/register";
    private static final long CHAT_ID = 42L;

    PasswordResetTestFixture fixture;
    AuthService authService;
    OwnerService ownerService;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        fixture = new PasswordResetTestFixture();
        fixture.addOwner(7, "linked@example.com", CHAT_ID, "pt");
        fixture.addOwner(8, "no-telegram@example.com", null, "pt");

        authService = Mockito.mock(AuthService.class);
        Mockito.when(authService.generateToken(ArgumentMatchers.any())).thenReturn("jwt");
        //---- Real rule check (OwnerServiceImpl applies PasswordPolicy), the rest of registration mocked.
        ownerService = Mockito.mock(OwnerService.class);
        Mockito.when(ownerService.registerOwner(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).thenAnswer(invocation -> {
            PasswordPolicy.check(invocation.getArgument(2));
            return Optional.of(9);
        });

        //---- Register also starts a session (#24): the refresh token itself is covered by AuthSessionControllerTest.
        RefreshTokenService refreshTokenService = Mockito.mock(RefreshTokenService.class);
        Mockito.when(refreshTokenService.issue(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn("refresh");

        AuthController controller = new AuthController();
        controller.authService = authService;
        controller.ownerService = ownerService;
        controller.passwordResetService = fixture.service;
        controller.refreshTokenService = refreshTokenService;
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    //---- Request

    @Test
    void sameResponseForLinkedUnlinkedAndUnknownEmails() throws Exception {
        MockHttpServletResponse linked = request("linked@example.com");
        MockHttpServletResponse noTelegram = request("no-telegram@example.com");
        MockHttpServletResponse unknown = request("nobody@example.com");

        for (MockHttpServletResponse response : new MockHttpServletResponse[]{linked, noTelegram, unknown}) {
            Assertions.assertEquals(202, response.getStatus());
            Assertions.assertEquals("", response.getContentAsString());
            Assertions.assertEquals(linked.getHeaderNames(), response.getHeaderNames());
        }
        //---- Only the linked owner actually got a link.
        Assertions.assertFalse(fixture.lastTokenSentTo(CHAT_ID).isBlank());
        Assertions.assertEquals(1, fixture.tokens.size());
    }

    @Test
    void fourthRequestWithinAnHourReturns429() throws Exception {
        for (String email : new String[]{"linked@example.com", "nobody@example.com"}) {
            for (int i = 0; i < 3; i++)
                Assertions.assertEquals(202, request(email).getStatus());
            Assertions.assertEquals(429, request(email).getStatus());
        }
    }

    @Test
    void missingOrBlankEmailReturns400() throws Exception {
        mockMvc.perform(post(REQUEST_URL).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(REQUEST_URL).contentType(MediaType.APPLICATION_JSON).content("{\"email\": \"  \"}"))
                .andExpect(status().isBadRequest());
    }

    //---- Confirm

    @Test
    void confirmReturns204AndTheTokenCannotBeReused() throws Exception {
        request("linked@example.com");
        String token = fixture.lastTokenSentTo(CHAT_ID);

        confirm(token, "new-password-123").andExpect(status().isNoContent()).andExpect(content().string(""));
        confirm(token, "new-password-123").andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("TOKEN_USED"));
    }

    @Test
    void confirmErrorsReturnTheirCode() throws Exception {
        confirm("unknown", "new-password-123").andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("TOKEN_INVALID"));

        request("linked@example.com");
        String token = fixture.lastTokenSentTo(CHAT_ID);
        confirm(token, "short").andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("WEAK_PASSWORD"));

        fixture.clock.advance(Duration.ofMinutes(31));
        confirm(token, "new-password-123").andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("TOKEN_EXPIRED"));
    }

    //---- Register

    @Test
    void registerRejectsWeakPassword() throws Exception {
        register("short").andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("WEAK_PASSWORD"));

        Mockito.verify(authService, Mockito.never()).generateToken(ArgumentMatchers.any());
    }

    @Test
    void registerAcceptsValidPassword() throws Exception {
        register("long-enough-1").andExpect(status().isOk()).andExpect(jsonPath("$.token").value("jwt"));
    }

    private MockHttpServletResponse request(String email) throws Exception {
        return mockMvc.perform(post(REQUEST_URL).contentType(MediaType.APPLICATION_JSON).content("{\"email\": \"" + email + "\"}"))
                .andReturn().getResponse();
    }

    private ResultActions confirm(String token, String password) throws Exception {
        return mockMvc.perform(post(CONFIRM_URL).contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\": \"" + token + "\", \"newPassword\": \"" + password + "\"}"));
    }

    private ResultActions register(String password) throws Exception {
        return mockMvc.perform(post(REGISTER_URL).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"G\", \"email\": \"new@example.com\", \"password\": \"" + password + "\"}"));
    }
}
